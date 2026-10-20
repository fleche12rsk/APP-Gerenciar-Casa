package dev.micio.casa.integrations.lg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Liga a TV pela rede ("Ligar via Wi-Fi" precisa estar ativado na TV). */
object WakeOnLan {

    suspend fun send(mac: String, host: String? = null) = withContext(Dispatchers.IO) {
        val macBytes = mac.split(':', '-').filter { it.isNotBlank() }.map { it.toInt(16).toByte() }
        require(macBytes.size == 6) { "MAC inválido: use o formato AA:BB:CC:DD:EE:FF" }
        val packet = ByteArray(6) { 0xFF.toByte() } + List(16) { macBytes }.flatten()
        val targets = buildList {
            add("255.255.255.255")
            // Broadcast da sub-rede /24 da TV, que alguns roteadores exigem.
            host?.split('.')?.takeIf { it.size == 4 }?.let { add(it.take(3).joinToString(".") + ".255") }
        }
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(3) {
                targets.forEach { target ->
                    for (port in intArrayOf(9, 7)) {
                        socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                    }
                }
            }
        }
    }
}
