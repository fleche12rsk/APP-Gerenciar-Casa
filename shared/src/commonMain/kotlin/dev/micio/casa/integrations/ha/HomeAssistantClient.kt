package dev.micio.casa.integrations.ha

import dev.micio.casa.core.HaConfig
import dev.micio.casa.core.Http
import dev.micio.casa.integrations.IntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class HaState(
    val entity_id: String,
    val state: String,
    val attributes: JsonObject = JsonObject(emptyMap()),
) {
    val domain get() = entity_id.substringBefore('.')
    val friendlyName get() = attributes["friendly_name"]?.jsonPrimitive?.contentOrNull ?: entity_id
    fun attr(key: String) = attributes[key]?.jsonPrimitive?.contentOrNull
}

/** REST API do Home Assistant: https://developers.home-assistant.io/docs/api/rest */
class HomeAssistantClient(
    private val config: HaConfig,
    private val http: HttpClient = Http.client,
) {
    private val base = config.url.trim().trimEnd('/')

    suspend fun states(): List<HaState> = Http.json.decodeFromString(get("/api/states"))

    suspend fun callService(domain: String, service: String, data: JsonObject) {
        http.post(base + "/api/services/$domain/$service") {
            authorize()
            setBody(TextContent(data.toString(), ContentType.Application.Json))
        }.ensureOk()
    }

    suspend fun cameraSnapshot(entityId: String): ByteArray =
        http.get(base + "/api/camera_proxy/$entityId") { authorize() }.ensureOk().body()

    private suspend fun get(path: String): String =
        http.get(base + path) { authorize() }.ensureOk().bodyAsText()

    private fun HttpRequestBuilder.authorize() {
        header("Authorization", "Bearer ${config.token.trim()}")
    }

    private fun HttpResponse.ensureOk(): HttpResponse {
        if (!status.isSuccess()) throw error(status.value)
        return this
    }

    private fun error(code: Int) = IntegrationException(
        when (code) {
            401 -> "Home Assistant recusou o token. Gere um novo token de longa duração."
            404 -> "Home Assistant: recurso não encontrado (a integração está instalada?)."
            else -> "Home Assistant respondeu com erro $code."
        }
    )
}
