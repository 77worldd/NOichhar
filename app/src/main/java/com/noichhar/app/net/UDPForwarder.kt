package com.noichhar.app.net

import com.noichhar.app.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class UDPForwarder {

    fun forward(data: ByteArray, destHost: String, destPort: Int): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.soTimeout = 5000

            val address = InetAddress.getByName(destHost)
            val packet = DatagramPacket(data, data.size, address, destPort)
            socket.send(packet)

            val buffer = ByteArray(1500)
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            return buffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            Logger.e("UDP forward error: ${e.message}")
            return null
        } finally {
            socket?.close()
        }
    }
}
