package dev.micio.casa.integrations.lg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

internal actual suspend fun sendUdpBroadcast(packet: ByteArray, targets: List<String>, ports: IntArray) =
    withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            targets.forEach { target ->
                for (port in ports) {
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(target), port))
                }
            }
        }
    }
