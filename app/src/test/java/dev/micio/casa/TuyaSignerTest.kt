package dev.micio.casa

import dev.micio.casa.integrations.tuya.TuyaSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TuyaSignerTest {

    @Test fun `corpo vazio usa o hash sha256 da string vazia`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", TuyaSigner.sha256Hex(""))
    }

    @Test fun `query entra ordenada na assinatura`() {
        assertEquals(
            "/v1.0/iot-01/associated-users/devices?last_row_key=abc&size=100",
            TuyaSigner.canonicalUrl("/v1.0/iot-01/associated-users/devices?size=100&last_row_key=abc"),
        )
        assertEquals("/v1.0/token", TuyaSigner.canonicalUrl("/v1.0/token"))
    }

    @Test fun `assinatura em hexadecimal maiusculo e muda com o token`() {
        val semToken = TuyaSigner.sign("id", "secret", null, "1700000000000", "n", "GET", "/v1.0/token?grant_type=1", "")
        val comToken = TuyaSigner.sign("id", "secret", "tok", "1700000000000", "n", "GET", "/v1.0/token?grant_type=1", "")
        assertEquals(64, semToken.length)
        assertEquals(semToken.uppercase(), semToken)
        assertNotEquals(semToken, comToken)
    }
}
