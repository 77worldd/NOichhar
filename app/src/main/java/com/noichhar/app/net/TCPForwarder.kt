package com.noichhar.app.net

import com.noichhar.app.util.Logger
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class TCPForwarder {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun forward(
        sourceInput: InputStream,
        sourceOutput: OutputStream,
        destHost: String,
        destPort: Int
    ) {
        scope.launch {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(destHost, destPort), 5000)
                socket.soTimeout = 30000

                val destInput = socket.getInputStream()
                val destOutput = socket.getOutputStream()

                // Forward in both directions
                val job1 = launch { pipe(sourceInput, destOutput) }
                val job2 = launch { pipe(destInput, sourceOutput) }

                job1.join()
                job2.join()
            } catch (e: Exception) {
                Logger.e("TCP forward error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private suspend fun pipe(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        scope.cancel()
    }
}
