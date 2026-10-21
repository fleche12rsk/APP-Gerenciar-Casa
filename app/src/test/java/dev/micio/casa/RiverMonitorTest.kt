package dev.micio.casa

import dev.micio.casa.integrations.river.LevelPoint
import dev.micio.casa.integrations.river.RiverMonitor
import dev.micio.casa.integrations.river.RiverReport
import dev.micio.casa.integrations.river.Trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.hours

/** Usa respostas reais da API da Defesa Civil de Rio do Sul (21/10/2026). */
class RiverMonitorTest {

    private fun read(name: String) = javaClass.getResource(name)!!.readText()

    private val stations = RiverMonitor.merge(
        RiverMonitor.parseStationList(read("/rio-stations.json")),
        RiverMonitor.parsePanel(read("/rio-panel.json")),
    )

    @Test fun `estacoes do painel na ordem do site`() {
        assertEquals(listOf("Ponte Dom Tito Buss", "Ponte Ricardo Kanitz", "Ponte BR 470"), stations.map { it.name })
    }

    @Test fun `estacao de referencia e o Itajai-Acu`() {
        val reference = RiverReport(stations, emptyList()).reference!!
        assertEquals("Rio Itajaí-Açu", reference.river)
        assertEquals(2.819, reference.levelM!!, 0.001)
        assertEquals("Normal", reference.bandLabel)
        assertEquals(listOf("Atenção", "Alerta", "Emergência"), reference.thresholds.map { it.label })
        assertEquals(4.5, reference.thresholds.first().levelM, 0.0)
        assertNotNull(reference.readingAt)
    }

    @Test fun `historico de 24 horas`() {
        val history = RiverMonitor.parseHistory(read("/rio-history.json"))
        assertEquals(24, history.size)
        assertTrue(history.zipWithNext().all { (a, b) -> a.time < b.time })
    }

    @Test fun `datas nos dois formatos da api`() {
        assertEquals(Instant.parse("2026-10-21T00:06:18.719125Z"), RiverMonitor.parseInstant("2026-10-21 00:06:18.719125+00"))
        assertEquals(Instant.parse("2026-10-21T00:07:05.664448Z"), RiverMonitor.parseInstant("2026-10-21T00:07:05.664448Z"))
        assertEquals(Instant.parse("2026-10-20T00:00:00Z"), RiverMonitor.parseInstant("2026-10-20T00:00:00Z"))
    }

    @Test fun `tendencia de subida`() {
        val reference = stations.first().copy(levelM = 3.40, readingAt = Instant.parse("2026-10-21T12:00:00Z"))
        val history = (0..6).map { h -> LevelPoint(Instant.parse("2026-10-21T06:00:00Z") + h.hours, 3.0 + h * 0.03) }
        val report = RiverReport(listOf(reference) + stations.drop(1), history)
        assertEquals(Trend.RISING, report.trend)
        assertTrue(report.centimetersPerHour!! > 2)
    }
}

class RiverCitiesTest {
    @Test fun `lista de cidades com painel`() {
        val cities = RiverMonitor.parseCities(javaClass.getResource("/rio-cities.json")!!.readText())
        assertEquals(14, cities.size)
        val itajuba = cities.first { it.name == "Itajubá" }
        assertEquals(3132404, itajuba.id)
        assertEquals("MG", itajuba.state)
    }

    @Test fun `site oficial so para Rio do Sul`() {
        assertEquals("https://defesacivil.riodosul.sc.gov.br", RiverMonitor.siteFor(RiverMonitor.RIO_DO_SUL))
        assertEquals(null, RiverMonitor.siteFor(3132404))
    }
}
