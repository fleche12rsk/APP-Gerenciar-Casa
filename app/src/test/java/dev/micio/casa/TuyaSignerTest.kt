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

/** O SHA-256/HMAC em Kotlin puro (usado também no iOS) tem que bater com o javax.crypto. */
class CryptoTest {

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test fun `sha256 igual ao java para varios tamanhos`() {
        val random = java.util.Random(42)
        for (size in listOf(0, 1, 55, 56, 63, 64, 65, 119, 120, 1000, 4096)) {
            val data = ByteArray(size).also(random::nextBytes)
            val expected = java.security.MessageDigest.getInstance("SHA-256").digest(data)
            assertEquals("tamanho $size", hex(expected), hex(dev.micio.casa.core.Sha256.digest(data)))
        }
    }

    @Test fun `hmac igual ao java com chaves curtas e longas`() {
        val random = java.util.Random(7)
        for (keySize in listOf(0, 16, 64, 65, 200)) {
            val key = ByteArray(keySize).also(random::nextBytes)
            val message = ByteArray(300).also(random::nextBytes)
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            // SecretKeySpec não aceita chave vazia; o HMAC com chave vazia equivale a 64 zeros.
            mac.init(javax.crypto.spec.SecretKeySpec(if (keySize == 0) ByteArray(64) else key, "HmacSHA256"))
            assertEquals("chave $keySize", hex(mac.doFinal(message)), hex(dev.micio.casa.core.hmacSha256(key, message)))
        }
    }
}
