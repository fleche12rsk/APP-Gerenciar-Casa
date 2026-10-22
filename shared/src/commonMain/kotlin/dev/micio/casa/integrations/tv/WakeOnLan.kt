package dev.micio.casa.integrations.tv

/** Liga a TV pela rede ("Ligar via Wi-Fi" precisa estar ativado na TV). */
object WakeOnLan {

    suspend fun send(mac: String, host: String? = null) {
        val macBytes = mac.split(':', '-').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }
        require(macBytes.size == 6) { "MAC inválido: use o formato AA:BB:CC:DD:EE:FF" }
        val packet = ByteArray(6) { 0xFF.toByte() } + List(16) { macBytes }.flatten()
        val targets = buildList {
            add("255.255.255.255")
            // Broadcast da sub-rede /24 da TV, que alguns roteadores exigem.
            host?.split('.')?.takeIf { it.size == 4 }?.let { add(it.take(3).joinToString(".") + ".255") }
        }
        repeat(3) { sendUdpBroadcast(packet, targets, intArrayOf(9, 7)) }
    }
}

internal expect suspend fun sendUdpBroadcast(packet: ByteArray, targets: List<String>, ports: IntArray)
