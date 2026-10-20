package dev.micio.casa.integrations.ha

import dev.micio.casa.core.HaConfig
import dev.micio.casa.core.Http
import dev.micio.casa.core.await
import dev.micio.casa.integrations.IntegrationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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
    private val http: OkHttpClient = Http.client,
) {
    private val base = config.url.trim().trimEnd('/')

    suspend fun states(): List<HaState> = Http.json.decodeFromString(get("/api/states"))

    suspend fun callService(domain: String, service: String, data: JsonObject) {
        val request = authorized("/api/services/$domain/$service")
            .post(data.toString().toRequestBody(JSON_TYPE))
            .build()
        http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw error(response.code)
        }
    }

    suspend fun cameraSnapshot(entityId: String): ByteArray {
        val request = authorized("/api/camera_proxy/$entityId").get().build()
        return http.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw error(response.code)
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun get(path: String): String =
        http.newCall(authorized(path).get().build()).await().use { response ->
            if (!response.isSuccessful) throw error(response.code)
            response.body?.string().orEmpty()
        }

    private fun authorized(path: String) = Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer ${config.token.trim()}")

    private fun error(code: Int) = IntegrationException(
        when (code) {
            401 -> "Home Assistant recusou o token. Gere um novo token de longa duração."
            404 -> "Home Assistant: recurso não encontrado (a integração está instalada?)."
            else -> "Home Assistant respondeu com erro $code."
        }
    )

    private companion object {
        val JSON_TYPE = "application/json".toMediaType()
    }
}
