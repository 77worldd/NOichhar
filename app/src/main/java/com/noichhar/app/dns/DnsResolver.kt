package com.noichhar.app.dns

import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsResolver {
    fun resolveUdp(query: ByteArray, serverIp: String, port: Int = Constants.DNS_PORT): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            socket.soTimeout = Constants.DNS_TIMEOUT_MS
            val serverAddress = InetAddress.getByName(serverIp)
            socket.send(DatagramPacket(query, query.size, serverAddress, port))

            val buffer = ByteArray(Constants.DNS_UDP_BUFFER_SIZE)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)
            val response = buffer.copyOf(responsePacket.length)
            if (DnsPacketParser.getTransactionId(response) == DnsPacketParser.getTransactionId(query)) response else null
        } catch (e: Exception) {
            Logger.e("DNS UDP resolve failed: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }

    fun resolveUdpAny(query: ByteArray, servers: List<String>): ByteArray? {
        for (server in servers) {
            resolveUdp(query, server)?.let { return it }
        }
        return null
    }
}
