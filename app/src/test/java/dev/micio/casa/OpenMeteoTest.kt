package dev.micio.casa

import dev.micio.casa.integrations.weather.OpenMeteo
import dev.micio.casa.integrations.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {

    private val sample = javaClass.getResource("/open-meteo-forecast.json")!!.readText()

    @Test fun `le condicao atual`() {
        val now = OpenMeteo.parseForecast(sample).current
        assertEquals(24.6, now.temperature, 0.0)
        assertEquals(58, now.humidity)
        assertEquals(135, now.windDirection)
        assertEquals(2, now.code)
        assertTrue(now.isDay)
    }

    @Test fun `horas comecam na hora atual e vao ate 24`() {
        val hours = OpenMeteo.parseForecast(sample).hourly
        assertEquals(24, hours.size)
        assertEquals(14, hours.first().time.hour)
    }

    @Test fun `sete dias com nascer e por do sol`() {
        val days = OpenMeteo.parseForecast(sample).daily
        assertEquals(7, days.size)
        assertEquals(85, days[1].precipitationProbability)
        assertEquals(5, days[0].sunrise!!.hour)
    }

    @Test fun `busca de cidades`() {
        val places = OpenMeteo.parsePlaces(
            """{"results":[{"name":"Florianópolis","latitude":-27.59667,"longitude":-48.54917,"country":"Brasil","admin1":"Santa Catarina"}]}"""
        )
        assertEquals("Florianópolis", places.single().name)
        assertEquals("Santa Catarina, Brasil", places.single().region)
        assertTrue(OpenMeteo.parsePlaces("""{"generationtime_ms":0.1}""").isEmpty())
    }

    @Test fun `direcao do vento e indice uv`() {
        assertEquals("N", WeatherCodes.windDirection(350))
        assertEquals("SE", WeatherCodes.windDirection(135))
        assertEquals("O", WeatherCodes.windDirection(270))
        assertEquals("Alto", WeatherCodes.uvLevel(6.15))
    }
}
