package dev.micio.casa

import dev.micio.casa.core.AppConfig
import dev.micio.casa.core.CameraBrand
import dev.micio.casa.core.CameraConfig
import dev.micio.casa.core.ConfigTransfer
import dev.micio.casa.core.Http
import dev.micio.casa.core.TuyaConfig
import dev.micio.casa.core.TvBrand
import dev.micio.casa.core.TvConfig
import dev.micio.casa.core.migrated
import dev.micio.casa.core.pbkdf2Sha256
import dev.micio.casa.integrations.tv.RokuClient
import dev.micio.casa.integrations.tv.SamsungTvClient
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

class ConfigTransferTest {

    private val config = AppConfig(
        tuya = TuyaConfig("id-123", "segredo-456"),
        tvs = listOf(TvConfig("a1", TvBrand.SAMSUNG, "192.168.0.20", token = "987"), TvConfig("b2", TvBrand.ROKU, "192.168.0.21", name = "Quarto")),
        cameras = listOf(CameraConfig("c1", "Portão", CameraBrand.ICSEE, "rtsp://192.168.0.30/x")),
        rooms = listOf("Sala", "Varanda"),
        keepScreenOn = false,
        immersive = true,
    )

    @Test fun `ida e volta com a senha certa`() {
        val code = ConfigTransfer.export(config, "minha-senha", Random(1))
        assertTrue(code.startsWith("CASA1."))
        val back = ConfigTransfer.import(code, "minha-senha")
        assertEquals(config.tuya, back.tuya)
        assertEquals(config.tvs, back.tvs)
        assertEquals(config.cameras, back.cameras)
        assertEquals(config.rooms, back.rooms)
    }

    @Test fun `ajustes de tela ficam em cada aparelho`() {
        val back = ConfigTransfer.import(ConfigTransfer.export(config, "minha-senha"), "minha-senha")
        assertEquals(AppConfig().keepScreenOn, back.keepScreenOn)
        assertEquals(AppConfig().immersive, back.immersive)
    }

    @Test fun `segredos nao aparecem em texto no codigo`() {
        val code = ConfigTransfer.export(config, "minha-senha")
        assertFalse(code.contains("segredo"))
        val decoded = java.util.Base64.getUrlDecoder().decode(code.removePrefix("CASA1."))
        assertFalse(String(decoded, Charsets.ISO_8859_1).contains("segredo-456"))
    }

    @Test(expected = ConfigTransfer.WrongPassword::class)
    fun `senha errada`() {
        ConfigTransfer.import(ConfigTransfer.export(config, "minha-senha"), "outra-senha")
    }

    @Test(expected = ConfigTransfer.WrongPassword::class)
    fun `codigo adulterado e recusado`() {
        val code = ConfigTransfer.export(config, "minha-senha")
        val i = code.length - 50
        val tampered = code.substring(0, i) + (if (code[i] == 'A') 'B' else 'A') + code.substring(i + 1)
        ConfigTransfer.import(tampered, "minha-senha")
    }

    @Test(expected = ConfigTransfer.InvalidCode::class)
    fun `texto sem codigo`() {
        ConfigTransfer.import("oi, tudo bem?", "minha-senha")
    }

    @Test fun `aceita a mensagem inteira colada`() {
        val code = ConfigTransfer.export(config, "minha-senha")
        val message = "Código de configuração do app Casa (a senha vai separada):\n\n$code\n"
        assertEquals(config.tvs, ConfigTransfer.import(message, "minha-senha").tvs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `senha curta nao gera codigo`() {
        ConfigTransfer.export(config, "123")
    }

    @Test fun `pbkdf2 igual ao java`() {
        val salt = Random(3).nextBytes(16)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        for ((password, iterations, length) in listOf(Triple("senha", 1, 32), Triple("minha-senha", 1000, 64), Triple("çãõ#ü", 25, 48))) {
            val expected = factory.generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, length * 8)).encoded
            assertEquals(expected.toList(), pbkdf2Sha256(password.encodeToByteArray(), salt, iterations, length).toList())
        }
    }
}

class TvMigrationTest {

    @Test fun `tv lg do formato antigo vira a primeira tv da lista`() {
        val old = """{"tv":{"host":"192.168.0.50","mac":"AA:BB:CC:DD:EE:FF","clientKey":"chave","name":"TV da sala"}}"""
        val config = Http.json.decodeFromString<AppConfig>(old).migrated()
        assertEquals(null, config.tv)
        assertEquals(listOf(TvConfig("tv1", TvBrand.LG, "192.168.0.50", "AA:BB:CC:DD:EE:FF", "chave", "TV da sala")), config.tvs)
    }

    @Test fun `configuracao nova nao muda`() {
        val config = AppConfig(tvs = listOf(TvConfig("x", TvBrand.ROKU, "10.0.0.2")))
        assertEquals(config, config.migrated())
    }

    @Test fun `apps e entradas do roku`() {
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
            <apps>
              <app id="tvinput.hdmi1" type="tvin" version="1.0.0">HDMI 1</app>
              <app id="12" type="appl" version="5.2.98">Netflix</app>
              <app id="837" subtype="ndka" type="appl" version="2.21.95">YouTube</app>
              <app id="2213" type="appl" version="1.0">Globoplay &amp; Mais</app>
            </apps>"""
        val apps = RokuClient.parseApps(xml)
        assertEquals(listOf("tvinput.hdmi1", "12", "837", "2213"), apps.map { it.first.id })
        assertEquals("tvin", apps.first().second)
        assertEquals("Globoplay & Mais", apps.last().first.title)
    }

    @Test fun `tecla da samsung no formato do protocolo`() {
        val params = SamsungTvClient.remoteKey("KEY_VOLUP")["params"]!!.jsonObject
        assertEquals("Click", params["Cmd"]!!.jsonPrimitive.content)
        assertEquals("KEY_VOLUP", params["DataOfCmd"]!!.jsonPrimitive.content)
        assertEquals("SendRemoteKey", params["TypeOfRemote"]!!.jsonPrimitive.content)
    }
}
