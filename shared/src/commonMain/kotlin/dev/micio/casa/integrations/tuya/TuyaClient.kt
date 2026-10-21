package dev.micio.casa.integrations.tuya

import dev.micio.casa.core.Http
import dev.micio.casa.core.TuyaConfig
import dev.micio.casa.core.nowMillis
import dev.micio.casa.core.toHex
import dev.micio.casa.integrations.IntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.random.Random

/** Cliente mínimo da Tuya Cloud OpenAPI (projeto "Cloud Development" em iot.tuya.com). */
class TuyaClient(
    private val config: TuyaConfig,
    private val http: HttpClient = Http.client,
) {
    private val mutex = Mutex()
    private var token: String? = null
    private var tokenExpiresAt = 0L

    suspend fun get(path: String): JsonElement = request("GET", path, null)

    suspend fun post(path: String, body: JsonElement): JsonElement = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JsonElement?): JsonElement {
        val response = runCatching { call(method, path, body, accessToken()) }.getOrElse { error ->
            // Token expirado ou revogado: pega um novo e tenta mais uma vez.
            if (error is TuyaApiException && error.code in TOKEN_ERRORS) {
                mutex.withLock { token = null }
                call(method, path, body, accessToken())
            } else throw error
        }
        return response["result"] ?: JsonNull
    }

    private suspend fun accessToken(): String = mutex.withLock {
        token?.takeIf { nowMillis() < tokenExpiresAt }?.let { return it }
        val result = call("GET", "/v1.0/token?grant_type=1", null, null)["result"]?.jsonObject
            ?: throw IntegrationException("A Tuya não devolveu um token de acesso.")
        val newToken = result["access_token"]?.jsonPrimitive?.contentOrNull
            ?: throw IntegrationException("Resposta de token da Tuya sem access_token.")
        val expiresIn = result["expire_time"]?.jsonPrimitive?.longOrNull ?: 3600
        token = newToken
        tokenExpiresAt = nowMillis() + (expiresIn - 60) * 1000
        newToken
    }

    private suspend fun call(method: String, path: String, body: JsonElement?, accessToken: String?): JsonObject {
        val bodyText = body?.toString() ?: ""
        val timestamp = nowMillis().toString()
        val nonce = Random.nextBytes(16).toHex()
        val sign = TuyaSigner.sign(
            config.clientId.trim(), config.clientSecret.trim(), accessToken, timestamp, nonce, method, path, bodyText,
        )
        val text = http.request(config.region.host + path) {
            this.method = HttpMethod.parse(method)
            header("client_id", config.clientId.trim())
            header("sign", sign)
            header("sign_method", "HMAC-SHA256")
            header("t", timestamp)
            header("nonce", nonce)
            accessToken?.let { header("access_token", it) }
            if (method != "GET") setBody(TextContent(bodyText, ContentType.Application.Json))
        }.bodyAsText()
        val json = runCatching { Http.json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw IntegrationException("Resposta inesperada da Tuya.") }
        if (json["success"]?.jsonPrimitive?.booleanOrNull != true) {
            val code = json["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1
            val msg = json["msg"]?.jsonPrimitive?.contentOrNull ?: "erro desconhecido"
            throw TuyaApiException(code, msg)
        }
        return json
    }

    private companion object {
        val TOKEN_ERRORS = setOf(1010, 1011, 1012)
    }
}

class TuyaApiException(val code: Int, message: String) : Exception(
    when (code) {
        1004 -> "Tuya: assinatura recusada. Confira o Access Secret."
        1106 -> "Tuya: sem permissão. Vincule a conta do app Smart Life ao projeto e ative as APIs."
        28841105 -> "Tuya: projeto sem a API necessária. Assine \"IoT Core\" e \"Smart Home Scene Linkage\"."
        else -> "Tuya ($code): $message"
    }
)
