package dev.micio.casa.core

import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/** Número com vírgula decimal, como no Brasil: formatDecimal(2.819, 2) == "2,82". */
fun formatDecimal(value: Double, decimals: Int): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val factor = 10.0.pow(decimals)
    val scaled = round(abs(value) * factor).toLong()
    val unit = factor.toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / unit
    if (decimals == 0) return "$sign$whole"
    val fraction = (scaled % unit).toString().padStart(decimals, '0')
    return "$sign$whole,$fraction"
}

fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

fun localNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

fun LocalDateTime.hourMinute(): String = "${twoDigits(hour)}:${twoDigits(minute)}"

fun LocalTime.hourMinute(): String = "${twoDigits(hour)}:${twoDigits(minute)}"

private val weekdays = mapOf(
    DayOfWeek.MONDAY to "Segunda-feira",
    DayOfWeek.TUESDAY to "Terça-feira",
    DayOfWeek.WEDNESDAY to "Quarta-feira",
    DayOfWeek.THURSDAY to "Quinta-feira",
    DayOfWeek.FRIDAY to "Sexta-feira",
    DayOfWeek.SATURDAY to "Sábado",
    DayOfWeek.SUNDAY to "Domingo",
)

private val months = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

fun DayOfWeek.longName(): String = weekdays.getValue(this)

/** "Seg", "Ter", ... */
fun DayOfWeek.shortName(): String = longName().take(3)

fun Month.namePt(): String = months[ordinal]

/** "Quarta-feira, 21 de outubro" */
fun LocalDateTime.longDate(): String = "${dayOfWeek.longName()}, $dayOfMonth de ${month.namePt()}"

/** Remove acentos de texto em português, para comparar falas e nomes. */
fun String.withoutAccents(): String = buildString(length) {
    for (c in this@withoutAccents) append(ACCENTS[c] ?: c)
}

private val ACCENTS: Map<Char, Char> = buildMap {
    fun map(chars: String, to: Char) = chars.forEach { put(it, to) }
    map("áàâãäå", 'a'); map("ÁÀÂÃÄÅ", 'A')
    map("éèêë", 'e'); map("ÉÈÊË", 'E')
    map("íìîï", 'i'); map("ÍÌÎÏ", 'I')
    map("óòôõö", 'o'); map("ÓÒÔÕÖ", 'O')
    map("úùûü", 'u'); map("ÚÙÛÜ", 'U')
    map("ç", 'c'); map("Ç", 'C')
    map("ñ", 'n'); map("Ñ", 'N')
}
