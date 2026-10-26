package com.noichhar.app.service

import com.noichhar.app.blocker.DomainBlocker
import com.noichhar.app.dns.*
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import com.noichhar.app.util.PreferenceManager
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DnsProxyService(
    private val domainBlocker: DomainBlocker,
    private val dnsCache: DnsCache,
    private val preferenceManager: PreferenceManager,
    private val onQuery: suspend (String, String, Boolean, String?, Long) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dnsResolver = DnsResolver()
    private val dohResolver = DohResolver()
    private val dotResolver = DotResolver()
    private var socket: DatagramSocket? = null

    @Volatile
    private var running = false

    fun start(listenPort: Int = Constants.DNS_PORT) {
        running = true
        scope.launch {
            try {
                socket = DatagramSocket(listenPort, InetAddress.getByName("127.0.0.1"))
                Logger.i("DNS Proxy listening on port $listenPort")

                while (running) {
                    val buffer = ByteArray(1024)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val data = buffer.copyOf(packet.length)
                    val clientAddress = packet.address
                    val clientPort = packet.port

                    launch {
                        handleQuery(data, clientAddress, clientPort)
                    }
                }
            } catch (e: Exception) {
                if (running) {
                    Logger.e("DNS Proxy error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleQuery(
        queryData: ByteArray,
        clientAddress: InetAddress,
        clientPort: Int
    ) {
        val question = DnsPacketParser.parseQuestion(queryData) ?: return
        val domain = question.name
        val queryType = DnsPacketParser.getQueryTypeName(question.type)
        val startTime = System.currentTimeMillis()

        // Check blocklist
        if (domainBlocker.isBlocked(domain)) {
            val response = DnsPacketParser.createBlockedResponse(queryData)
            sendResponse(response, clientAddress, clientPort)
            val elapsed = System.currentTimeMillis() - startTime
            onQuery(domain, queryType, true, domainBlocker.getBlockReason(domain), elapsed)
            return
        }

        // Check cache
        val cached = dnsCache.get(domain, question.type)
        if (cached != null) {
            val copy = cached.copyOf()
            copy[0] = queryData[0]
            copy[1] = queryData[1]
            sendResponse(copy, clientAddress, clientPort)
            val elapsed = System.currentTimeMillis() - startTime
            onQuery(domain, queryType, false, null, elapsed)
            return
        }

        // Resolve
        val response = resolve(queryData)
        if (response != null) {
            dnsCache.put(domain, question.type, response)
            sendResponse(response, clientAddress, clientPort)
        }

        val elapsed = System.currentTimeMillis() - startTime
        onQuery(domain, queryType, false, null, elapsed)
    }

    private fun resolve(query: ByteArray): ByteArray? {
        return when (preferenceManager.dnsProtocol) {
            Constants.DnsProtocol.DOH -> {
                val url = preferenceManager.getDnsUrl()
                if (url.isNotEmpty()) dohResolver.resolve(query, url)
                else dnsResolver.resolveUdp(query, preferenceManager.getDnsIp())
            }
            Constants.DnsProtocol.DOT -> {
                val host = getDotHost()
                if (host.isNotEmpty()) dotResolver.resolve(query, host)
                else dnsResolver.resolveUdp(query, preferenceManager.getDnsIp())
            }
            Constants.DnsProtocol.PLAIN -> {
                dnsResolver.resolveUdp(query, preferenceManager.getDnsIp())
            }
        }
    }

    private fun getDotHost(): String {
        return when (preferenceManager.dnsProvider) {
            Constants.DnsProvider.CLOUDFLARE -> Constants.DOT_CLOUDFLARE
            Constants.DnsProvider.GOOGLE -> Constants.DOT_GOOGLE
            Constants.DnsProvider.QUAD9 -> Constants.DOT_QUAD9
            Constants.DnsProvider.ADGUARD -> Constants.DOT_ADGUARD
            else -> ""
        }
    }

    private fun sendResponse(response: ByteArray, address: InetAddress, port: Int) {
        try {
            val packet = DatagramPacket(response, response.size, address, port)
            socket?.send(packet)
        } catch (e: Exception) {
            Logger.e("Failed to send DNS response: ${e.message}")
        }
    }

    fun stop() {
        running = false
        socket?.close()
        scope.cancel()
    }
}
