package com.noichhar.app.dns

import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import java.io.DataInputStream
import java.io.DataOutputStream
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class DotResolver {

    fun resolve(query: ByteArray, hostname: String): ByteArray? {
        var socket: SSLSocket? = null
        return try {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            socket = factory.createSocket(hostname, Constants.DOT_PORT) as SSLSocket
            socket.soTimeout = Constants.DNS_TIMEOUT_MS

            if (!hostname.contains(':')) {
                val sslParams = socket.sslParameters
                sslParams.serverNames = listOf(SNIHostName(hostname))
                socket.sslParameters = sslParams
            }
            socket.startHandshake()

            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            if (!verifier.verify(hostname, socket.session)) {
                Logger.e("DoT hostname verification failed for $hostname")
                return null
            }

            val output = DataOutputStream(socket.getOutputStream())
            output.writeShort(query.size)
            output.write(query)
            output.flush()

            val input = DataInputStream(socket.getInputStream())
            val responseLength = input.readUnsignedShort()
            if (responseLength <= 0 || responseLength > Constants.DNS_UDP_BUFFER_SIZE * 2) {
                Logger.e("Invalid DoT response length: $responseLength")
                return null
            }
            val response = ByteArray(responseLength)
            input.readFully(response)
            response.takeIf { it.size >= 12 }
        } catch (e: Exception) {
            Logger.e("DoT resolve failed: ${e.message}")
            null
        } finally {
            runCatching { socket?.close() }
        }
    }
}
