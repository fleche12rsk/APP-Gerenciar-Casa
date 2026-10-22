package dev.micio.casa.core

import kotlinx.serialization.encodeToString
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * "Código de configuração": leva toda a configuração (contas, TVs, câmeras, cômodos...)
 * para outro aparelho, sem servidor. O código vai cifrado com uma senha que a pessoa
 * digita nos dois aparelhos e que nunca viaja junto com ele.
 *
 * Formato: "CASA1." + base64url( iterações(4) | sal(16) | nonce(16) | texto cifrado | etiqueta(32) ).
 * Cifra: PBKDF2-HMAC-SHA256 gera 64 bytes (32 para cifrar, 32 para autenticar); o texto é
 * combinado com HMAC-SHA256(chave, nonce | contador) em modo contador e autenticado com
 * HMAC-SHA256 sobre todo o cabeçalho e o texto cifrado (encrypt-then-MAC).
 */
object ConfigTransfer {

    const val PREFIX = "CASA1."
    const val MIN_PASSWORD = 6
    private const val ITERATIONS = 60_000
    private const val SALT = 16
    private const val NONCE = 16
    private const val TAG = 32

    private val CODE = Regex("""CASA1\.([A-Za-z0-9_-]+)""")

    class InvalidCode : Exception("Código inválido ou incompleto. Copie o código inteiro de novo.")
    class WrongPassword : Exception("Senha incorreta.")

    @OptIn(ExperimentalEncodingApi::class)
    fun export(config: AppConfig, password: String, random: Random = Random.Default): String {
        require(password.length >= MIN_PASSWORD) { "A senha precisa ter pelo menos $MIN_PASSWORD caracteres." }
        val plain = Http.json.encodeToString(config.forTransfer()).encodeToByteArray()
        val salt = random.nextBytes(SALT)
        val nonce = random.nextBytes(NONCE)
        val header = intBytes(ITERATIONS) + salt + nonce
        val (encKey, macKey) = keys(password, salt, ITERATIONS)
        val cipher = xorKeystream(plain, encKey, nonce)
        val tag = hmacSha256(macKey, PREFIX.encodeToByteArray() + header + cipher)
        return PREFIX + Base64.UrlSafe.encode(header + cipher + tag).trimEnd('=')
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun import(code: String, password: String): AppConfig {
        // Aceita a mensagem inteira colada (com texto em volta): só o trecho do código importa.
        val body = CODE.find(code)?.groupValues?.get(1) ?: throw InvalidCode()
        val bytes = runCatching {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(body)
        }.getOrElse { throw InvalidCode() }
        if (bytes.size < 4 + SALT + NONCE + TAG) throw InvalidCode()
        val iterations = readInt(bytes, 0)
        if (iterations !in 1_000..1_000_000) throw InvalidCode()
        val salt = bytes.copyOfRange(4, 4 + SALT)
        val nonce = bytes.copyOfRange(4 + SALT, 4 + SALT + NONCE)
        val headerEnd = 4 + SALT + NONCE
        val cipher = bytes.copyOfRange(headerEnd, bytes.size - TAG)
        val tag = bytes.copyOfRange(bytes.size - TAG, bytes.size)
        val (encKey, macKey) = keys(password, salt, iterations)
        val expected = hmacSha256(macKey, PREFIX.encodeToByteArray() + bytes.copyOfRange(0, headerEnd) + cipher)
        // Senha errada e código adulterado dão o mesmo resultado aqui; senha errada é o caso comum.
        if (!constantTimeEquals(tag, expected)) throw WrongPassword()
        val plain = xorKeystream(cipher, encKey, nonce).decodeToString()
        return runCatching { Http.json.decodeFromString<AppConfig>(plain).migrated() }.getOrElse { throw InvalidCode() }
    }

    /** Ajustes da tela (sempre ligada, tela cheia) são de cada aparelho e não viajam. */
    private fun AppConfig.forTransfer() = copy(keepScreenOn = AppConfig().keepScreenOn, immersive = AppConfig().immersive)

    private fun keys(password: String, salt: ByteArray, iterations: Int): Pair<ByteArray, ByteArray> {
        val material = pbkdf2Sha256(password.encodeToByteArray(), salt, iterations, 64)
        return material.copyOfRange(0, 32) to material.copyOfRange(32, 64)
    }

    private fun xorKeystream(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        var counter = 0
        var offset = 0
        while (offset < data.size) {
            val block = hmacSha256(key, nonce + intBytes(counter++))
            for (i in block.indices) {
                if (offset + i >= data.size) break
                out[offset + i] = (data[offset + i].toInt() xor block[i].toInt()).toByte()
            }
            offset += block.size
        }
        return out
    }

    private fun intBytes(value: Int) = byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte())

    private fun readInt(bytes: ByteArray, at: Int) =
        (bytes[at].toInt() and 0xff shl 24) or (bytes[at + 1].toInt() and 0xff shl 16) or
            (bytes[at + 2].toInt() and 0xff shl 8) or (bytes[at + 3].toInt() and 0xff)
}

/** Resumo do que um código traz, para a pessoa conferir antes de aplicar. */
fun AppConfig.summary(): List<String> = buildList {
    if (tuya != null) add("Conta Smart Life / Tuya")
    if (homeAssistant != null) add("Home Assistant")
    if (tvs.isNotEmpty()) add(if (tvs.size == 1) "1 TV" else "${tvs.size} TVs")
    if (cameras.isNotEmpty()) add(if (cameras.size == 1) "1 câmera" else "${cameras.size} câmeras")
    weather?.let { add("Previsão de ${it.name}") }
    river?.let { add("Nível do rio de ${it.name}") }
    if (rooms.isNotEmpty()) add("${rooms.size} cômodos")
    if (overrides.isNotEmpty()) add("Nomes e favoritos de ${overrides.size} aparelhos")
}
