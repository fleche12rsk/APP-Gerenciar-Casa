package dev.micio.casa.integrations.tv

import dev.micio.casa.integrations.IntegrationException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.IPPROTO_UDP
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_BROADCAST
import platform.posix.close
import platform.posix.memset
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket

/**
 * Pacote UDP por socket BSD. Observação: o iOS só libera broadcast a apps com a
 * permissão de rede multicast da Apple; sem ela o envio é recusado e avisamos a pessoa.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun sendUdpBroadcast(packet: ByteArray, targets: List<String>, ports: IntArray) {
    withContext(Dispatchers.IO) {
        val fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
        if (fd < 0) throw IntegrationException("Não foi possível abrir a conexão de rede.")
        try {
            var sent = false
            memScoped {
                val on = alloc<IntVar>()
                on.value = 1
                setsockopt(fd, SOL_SOCKET, SO_BROADCAST, on.ptr, sizeOf<IntVar>().convert())
                for (target in targets) {
                    val address = ipv4(target) ?: continue
                    for (port in ports) {
                        val addr = alloc<sockaddr_in>()
                        memset(addr.ptr, 0, sizeOf<sockaddr_in>().convert())
                        addr.sin_len = sizeOf<sockaddr_in>().convert()
                        addr.sin_family = AF_INET.convert()
                        // Porta em ordem de rede (big-endian).
                        addr.sin_port = (((port and 0xff) shl 8) or ((port shr 8) and 0xff)).convert()
                        addr.sin_addr.s_addr = address
                        val result = packet.usePinned { pinned ->
                            sendto(fd, pinned.addressOf(0), packet.size.convert(), 0, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
                        }
                        if (result >= 0) sent = true
                    }
                }
            }
            if (!sent) {
                throw IntegrationException("O iOS bloqueou o sinal para ligar a TV pela rede. Ligue pelo controle ou pelo hub infravermelho.")
            }
        } finally {
            close(fd)
        }
    }
}

/** "192.168.0.10" -> endereço em ordem de rede, como o s_addr espera (iPhone/iPad são little-endian). */
private fun ipv4(text: String): UInt? {
    val parts = text.trim().split('.').map { it.toIntOrNull() ?: return null }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return null
    return (parts[0] or (parts[1] shl 8) or (parts[2] shl 16) or (parts[3] shl 24)).toUInt()
}
