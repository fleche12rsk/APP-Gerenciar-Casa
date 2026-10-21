package dev.micio.casa.integrations.river

import dev.micio.casa.core.Http
import dev.micio.casa.core.RiverCity
import dev.micio.casa.core.twoDigits
import dev.micio.casa.integrations.IntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.time.Duration.Companion.hours

data class RiverBand(val label: String, val levelM: Double, val color: Long)

data class RiverStation(
    val id: String,
    val name: String,
    val river: String?,
    val levelM: Double?,
    val bandLabel: String,
    val bandOrdinal: Int,
    val bandColor: Long,
    val thresholds: List<RiverBand>,
    val rain1h: Double?,
    val rain24h: Double?,
    val isRaining: Boolean,
    val readingAt: Instant?,
    val isReference: Boolean,
)

data class LevelPoint(val time: Instant, val levelM: Double)

enum class Trend { RISING, FALLING, STEADY }

data class RiverReport(
    val stations: List<RiverStation>,
    /** Últimas 24 h da estação de referência, uma leitura por hora. */
    val history: List<LevelPoint>,
) {
    val reference get() = stations.firstOrNull { it.isReference } ?: stations.firstOrNull()

    /** Variação em cm por hora, comparando a leitura atual com a de ~3 h atrás. */
    val centimetersPerHour: Double?
        get() {
            val now = reference?.levelM ?: return null
            val nowAt = reference?.readingAt ?: history.lastOrNull()?.time ?: return null
            val past = history.lastOrNull { (nowAt - it.time).inWholeMinutes >= 150 } ?: return null
            val hours = (nowAt - past.time).inWholeMinutes / 60.0
            return if (hours <= 0) null else (now - past.levelM) * 100 / hours
        }

    val trend: Trend
        get() {
            val rate = centimetersPerHour ?: return Trend.STEADY
            return when {
                abs(rate) < 2 -> Trend.STEADY
                rate > 0 -> Trend.RISING
                else -> Trend.FALLING
            }
        }
}

/**
 * Nível dos rios publicado pelas Defesas Civis municipais que usam o painel Asthon
 * (é a mesma API pública do site defesacivil.riodosul.sc.gov.br). Nada é recalculado.
 */
class RiverMonitor(private val http: HttpClient = Http.client) {

    /** Cidades com painel público, mais as que só aparecem no site da própria prefeitura. */
    suspend fun cities(): List<RiverCity> {
        val listed = parseCities(get("/public/cities"))
        return (listed + EXTRA_CITIES.filter { extra -> listed.none { it.id == extra.id } })
            .sortedWith(compareBy({ it.state }, { it.name }))
    }

    suspend fun load(cityId: Int, now: Instant = Clock.System.now()): RiverReport {
        val readings = parsePanel(get("/public/panel?city_id=$cityId&include_geometry=false"))
        // O site da cidade escolhe quais estações mostrar; sem site, usa as do painel que medem rio.
        val sitePanel = runCatching { parseStationList(get("/public/city-site/stations?city_id=$cityId")) }.getOrDefault(emptyList())
        val stations = if (sitePanel.isNotEmpty()) {
            merge(sitePanel, readings)
        } else {
            val referenceId = runCatching { parseReferenceId(get("/public/cities/$cityId")) }.getOrNull()
            val rivers = readings.values.filter { it.levelM != null && it.river != null }
            val reference = rivers.firstOrNull { it.id == referenceId } ?: rivers.firstOrNull()
            (listOfNotNull(reference) + rivers.filter { it.id != reference?.id }).take(4)
                .map { it.copy(isReference = it.id == reference?.id) }
        }
        val reference = stations.firstOrNull { it.isReference } ?: stations.firstOrNull()
        val history = reference?.let { station ->
            runCatching {
                val start = isoMinute(now - 24.hours)
                val end = isoMinute(now)
                parseHistory(get("/public/station-history?station_id=${station.id}&start=$start&end=$end&fields=level&resolution=hourly"))
            }.getOrDefault(emptyList())
        }.orEmpty()
        return RiverReport(stations, history)
    }

    private suspend fun get(path: String): String {
        val response = http.get(BASE + path) { header("accept", "application/json") }
        if (!response.status.isSuccess()) throw IntegrationException("Defesa Civil fora do ar (erro ${response.status.value}).")
        return response.bodyAsText()
    }

    data class PanelEntry(val id: String, val name: String, val order: Int, val isReference: Boolean)

    companion object {
        const val RIO_DO_SUL = 4214805
        private const val RIO_DO_SUL_SITE = "https://defesacivil.riodosul.sc.gov.br"

        /** Rio do Sul usa o painel, mas não aparece na lista pública de cidades. */
        private val EXTRA_CITIES = listOf(RiverCity(RIO_DO_SUL, "Rio do Sul", "SC", -27.18535, -49.63636))

        fun siteFor(cityId: Int): String? = if (cityId == RIO_DO_SUL) RIO_DO_SUL_SITE else null
        private const val BASE = "https://public.asthon.com.br"

        /** Mesmo formato que o site envia: minuto cheio em UTC, "2026-10-21T00:07:00.000Z". */
        fun isoMinute(instant: Instant): String {
            val t = instant.toLocalDateTime(TimeZone.UTC)
            return "${t.year}-${twoDigits(t.monthNumber)}-${twoDigits(t.dayOfMonth)}T${twoDigits(t.hour)}:${twoDigits(t.minute)}:00.000Z"
        }

        // A API mistura "2026-10-21T00:07:05.66Z" e "2026-10-21 00:06:18.71+00".
        fun parseInstant(text: String?): Instant? {
            if (text.isNullOrBlank()) return null
            var iso = text.trim().replaceFirst(' ', 'T')
            if (Regex("[+-]\\d{2}$").containsMatchIn(iso)) iso += ":00"
            return runCatching { Instant.parse(iso) }.getOrNull()
        }

        fun parseCities(text: String): List<RiverCity> =
            (Http.json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                RiverCity(
                    id = obj.int("city_id") ?: return@mapNotNull null,
                    name = obj.string("name") ?: return@mapNotNull null,
                    state = obj.string("state"),
                    latitude = obj.double("latitude"),
                    longitude = obj.double("longitude"),
                )
            }

        fun parseReferenceId(text: String): String? =
            Http.json.parseToJsonElement(text).jsonObject.string("reference_station_id")

        /** Estações que o site mostra no painel, na ordem dele. */
        fun parseStationList(text: String): List<PanelEntry> =
            (Http.json.parseToJsonElement(text) as? JsonArray).orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                if (obj.bool("show_in_panel") == false) return@mapNotNull null
                PanelEntry(
                    id = obj.string("station_id") ?: return@mapNotNull null,
                    name = obj.string("name").orEmpty(),
                    order = obj.int("display_order") ?: Int.MAX_VALUE,
                    isReference = obj.bool("is_reference") == true,
                )
            }.sortedBy { it.order }

        fun parsePanel(text: String): Map<String, RiverStation> {
            val stations = Http.json.parseToJsonElement(text).jsonObject["stations"]?.jsonArray.orEmpty()
            return stations.mapNotNull { element ->
                val obj = element.jsonObject
                val id = obj.string("station_id") ?: return@mapNotNull null
                id to RiverStation(
                    id = id,
                    name = obj.string("name").orEmpty(),
                    river = obj.string("river_name"),
                    levelM = obj.double("level_m"),
                    bandLabel = obj.string("band_label") ?: "Sem classificação",
                    bandOrdinal = obj.int("band_ordinal") ?: 0,
                    bandColor = parseColor(obj.string("band_color")) ?: 0xFF22C55E,
                    thresholds = obj["band_thresholds"]?.jsonArray.orEmpty().mapNotNull { t ->
                        val band = t.jsonObject
                        RiverBand(
                            label = band.string("label") ?: return@mapNotNull null,
                            levelM = band.double("cota_m") ?: return@mapNotNull null,
                            color = parseColor(band.string("color_hex")) ?: 0xFFEAB308,
                        )
                    }.sortedBy { it.levelM },
                    rain1h = obj.double("rainfall_1h"),
                    rain24h = obj.double("rainfall_24h"),
                    isRaining = obj.bool("is_raining") == true,
                    readingAt = parseInstant(obj.string("last_reading_at")),
                    isReference = false,
                )
            }.toMap()
        }

        fun parseHistory(text: String): List<LevelPoint> =
            Http.json.parseToJsonElement(text).jsonObject["level"]?.jsonArray.orEmpty().mapNotNull { element ->
                val obj = element.jsonObject
                LevelPoint(
                    time = parseInstant(obj.string("timestamp")) ?: return@mapNotNull null,
                    levelM = obj.double("value") ?: return@mapNotNull null,
                )
            }.sortedBy { it.time }

        fun merge(panel: List<PanelEntry>, readings: Map<String, RiverStation>): List<RiverStation> =
            panel.mapNotNull { entry -> readings[entry.id]?.copy(isReference = entry.isReference) }

        private fun parseColor(hex: String?): Long? =
            hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

        private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
        private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull
        private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull
    }
}
