package dev.micio.casa.integrations.weather

import dev.micio.casa.core.Http
import dev.micio.casa.core.WeatherPlace
import dev.micio.casa.core.await
import dev.micio.casa.integrations.IntegrationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime

data class Weather(
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
) {
    val today get() = daily.firstOrNull()
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val precipitation: Double?,
    val code: Int,
    val isDay: Boolean,
    val cloudCover: Int?,
    val pressure: Double?,
    val windSpeed: Double?,
    val windDirection: Int?,
    val windGusts: Double?,
    val uvIndex: Double?,
)

data class HourForecast(
    val time: LocalDateTime,
    val temperature: Double,
    val precipitationProbability: Int?,
    val code: Int,
    val isDay: Boolean,
)

data class DayForecast(
    val date: LocalDate,
    val code: Int,
    val min: Double,
    val max: Double,
    val precipitationProbability: Int?,
    val precipitationSum: Double?,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uvIndexMax: Double?,
    val windSpeedMax: Double?,
)

/**
 * Previsão do Open-Meteo (open-meteo.com): gratuita, sem chave de API, combina os
 * modelos de serviços meteorológicos nacionais. Licença CC BY 4.0 — o app exibe a atribuição.
 */
class OpenMeteo(private val http: OkHttpClient = Http.client) {

    suspend fun forecast(place: WeatherPlace): Weather {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("current", CURRENT)
            .addQueryParameter("hourly", "temperature_2m,precipitation_probability,weather_code,is_day")
            .addQueryParameter("daily", DAILY)
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "7")
            .addQueryParameter("wind_speed_unit", "kmh")
            .build()
        return parseForecast(get(url.toString()))
    }

    /** Busca cidades pelo nome, em português. */
    suspend fun searchPlaces(query: String): List<WeatherPlace> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "8")
            .addQueryParameter("language", "pt")
            .addQueryParameter("format", "json")
            .build()
        return parsePlaces(get(url.toString()))
    }

    private suspend fun get(url: String): String =
        http.newCall(Request.Builder().url(url).build()).await().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val reason = runCatching { Http.json.parseToJsonElement(body).jsonObject["reason"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                throw IntegrationException("Previsão do tempo indisponível${reason?.let { ": $it" } ?: ""}")
            }
            body
        }

    companion object {
        private const val CURRENT = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
            "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,uv_index"
        private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset," +
            "precipitation_probability_max,precipitation_sum,uv_index_max,wind_speed_10m_max"

        fun parseForecast(text: String): Weather {
            val root = Http.json.parseToJsonElement(text).jsonObject
            val c = root["current"]?.jsonObject ?: throw IntegrationException("Resposta de previsão sem dados atuais.")
            val current = CurrentWeather(
                time = LocalDateTime.parse(c.string("time")),
                temperature = c.double("temperature_2m") ?: throw IntegrationException("Previsão sem temperatura."),
                apparentTemperature = c.double("apparent_temperature"),
                humidity = c.int("relative_humidity_2m"),
                precipitation = c.double("precipitation"),
                code = c.int("weather_code") ?: 0,
                isDay = c.int("is_day") != 0,
                cloudCover = c.int("cloud_cover"),
                pressure = c.double("pressure_msl"),
                windSpeed = c.double("wind_speed_10m"),
                windDirection = c.int("wind_direction_10m"),
                windGusts = c.double("wind_gusts_10m"),
                uvIndex = c.double("uv_index"),
            )

            val h = root["hourly"]?.jsonObject
            val hourStart = current.time.withMinute(0)
            val hourly = h?.array("time")?.indices?.mapNotNull { i ->
                val time = LocalDateTime.parse(h.array("time")!![i].jsonPrimitive.content)
                if (time.isBefore(hourStart)) return@mapNotNull null
                HourForecast(
                    time = time,
                    temperature = h.doubleAt("temperature_2m", i) ?: return@mapNotNull null,
                    precipitationProbability = h.intAt("precipitation_probability", i),
                    code = h.intAt("weather_code", i) ?: 0,
                    isDay = h.intAt("is_day", i) != 0,
                )
            }.orEmpty().take(24)

            val d = root["daily"]?.jsonObject
            val daily = d?.array("time")?.indices?.mapNotNull { i ->
                DayForecast(
                    date = LocalDate.parse(d.array("time")!![i].jsonPrimitive.content),
                    code = d.intAt("weather_code", i) ?: 0,
                    min = d.doubleAt("temperature_2m_min", i) ?: return@mapNotNull null,
                    max = d.doubleAt("temperature_2m_max", i) ?: return@mapNotNull null,
                    precipitationProbability = d.intAt("precipitation_probability_max", i),
                    precipitationSum = d.doubleAt("precipitation_sum", i),
                    sunrise = d.stringAt("sunrise", i)?.let(LocalDateTime::parse),
                    sunset = d.stringAt("sunset", i)?.let(LocalDateTime::parse),
                    uvIndexMax = d.doubleAt("uv_index_max", i),
                    windSpeedMax = d.doubleAt("wind_speed_10m_max", i),
                )
            }.orEmpty()

            return Weather(current, hourly, daily)
        }

        fun parsePlaces(text: String): List<WeatherPlace> {
            val results = Http.json.parseToJsonElement(text).jsonObject["results"] as? JsonArray ?: return emptyList()
            return results.mapNotNull { element ->
                val obj = element.jsonObject
                WeatherPlace(
                    name = obj.string("name") ?: return@mapNotNull null,
                    region = listOfNotNull(obj.string("admin1"), obj.string("country")).joinToString(", ").ifEmpty { null },
                    latitude = obj.double("latitude") ?: return@mapNotNull null,
                    longitude = obj.double("longitude") ?: return@mapNotNull null,
                )
            }
        }

        private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        private fun JsonObject.double(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
        private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
        private fun JsonObject.array(key: String) = this[key] as? JsonArray
        private fun JsonObject.doubleAt(key: String, i: Int) = array(key)?.getOrNull(i)?.jsonPrimitive?.doubleOrNull
        private fun JsonObject.intAt(key: String, i: Int) = array(key)?.getOrNull(i)?.jsonPrimitive?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
        private fun JsonObject.stringAt(key: String, i: Int) = array(key)?.getOrNull(i)?.jsonPrimitive?.contentOrNull
    }
}

/** Códigos WMO usados pelo Open-Meteo, em português. */
object WeatherCodes {
    fun describe(code: Int): String = when (code) {
        0 -> "Céu limpo"
        1 -> "Predomínio de sol"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55 -> "Garoa"
        56, 57 -> "Garoa congelante"
        61 -> "Chuva fraca"
        63 -> "Chuva"
        65 -> "Chuva forte"
        66, 67 -> "Chuva congelante"
        71, 73, 75, 77 -> "Neve"
        80 -> "Pancadas de chuva fracas"
        81 -> "Pancadas de chuva"
        82 -> "Pancadas de chuva fortes"
        85, 86 -> "Pancadas de neve"
        95 -> "Trovoadas"
        96, 99 -> "Trovoadas com granizo"
        else -> "Tempo indefinido"
    }

    fun windDirection(degrees: Int): String =
        listOf("N", "NE", "L", "SE", "S", "SO", "O", "NO")[((degrees % 360 + 360) % 360 + 22) / 45 % 8]

    fun uvLevel(index: Double): String = when {
        index < 3 -> "Baixo"
        index < 6 -> "Moderado"
        index < 8 -> "Alto"
        index < 11 -> "Muito alto"
        else -> "Extremo"
    }
}
