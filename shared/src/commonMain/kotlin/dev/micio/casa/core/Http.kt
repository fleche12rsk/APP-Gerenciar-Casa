package dev.micio.casa.core

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

object Http {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }

    /** Cliente para serviços na internet (Tuya, Home Assistant, previsão, Defesa Civil). */
    val client: HttpClient by lazy { createHttpClient() }

    /**
     * Cliente só para a TV LG: o webOS expõe wss://IP:3001 com certificado
     * autoassinado, que nenhuma autoridade pública consegue validar. Fica restrito
     * a endereços da rede local digitados pelo usuário. Já vem com WebSockets.
     */
    val lanClient: HttpClient by lazy { createLanHttpClient() }
}

internal expect fun createHttpClient(): HttpClient

internal expect fun createLanHttpClient(): HttpClient
