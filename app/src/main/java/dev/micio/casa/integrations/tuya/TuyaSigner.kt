package dev.micio.casa.integrations.tuya

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Assinatura HMAC-SHA256 exigida pela Tuya Cloud OpenAPI.
 * https://developer.tuya.com/en/docs/iot/new-singnature
 */
object TuyaSigner {

    fun sign(
        clientId: String,
        secret: String,
        accessToken: String?,
        timestamp: String,
        nonce: String,
        method: String,
        pathWithQuery: String,
        body: String,
    ): String {
        val stringToSign = listOf(method.uppercase(), sha256Hex(body), "", canonicalUrl(pathWithQuery)).joinToString("\n")
        val payload = clientId + (accessToken ?: "") + timestamp + nonce + stringToSign
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return mac.doFinal(payload.toByteArray()).toHex().uppercase()
    }

    /** A Tuya exige os parâmetros de query em ordem alfabética dentro da assinatura. */
    fun canonicalUrl(pathWithQuery: String): String {
        val path = pathWithQuery.substringBefore('?')
        val query = pathWithQuery.substringAfter('?', "")
        if (query.isEmpty()) return path
        val sorted = query.split('&').filter { it.isNotEmpty() }.sortedBy { it.substringBefore('=') }
        return path + "?" + sorted.joinToString("&")
    }

    fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
