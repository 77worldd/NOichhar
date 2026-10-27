package com.noichhar.app.net

import android.content.Context
import com.noichhar.app.blocker.DomainBlocker
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.util.Constants
import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import com.noichhar.app.util.NetworkUtils
import com.noichhar.app.util.PackageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LocalFilterProxy(
    private val context: Context,
    private val domainBlocker: DomainBlocker,
    private val firewallManager: FirewallManager,
    private val uidResolver: UidResolver,
    private val protectSocket: (Socket) -> Boolean,
    private val onBlocked: (DnsQueryEvent) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var proxyScope = newScope()
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile
    private var running = false

    @Volatile
    private var localPort: Int = 0

    fun start(): Int? {
        if (running && localPort > 0) return localPort
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(Constants.LOCAL_PROXY_ADDRESS), 0), 128)
            serverSocket = socket
            localPort = socket.localPort
            running = true
            proxyScope = newScope()
            proxyScope.launch { acceptLoop(socket) }
            Logger.i("Local app-traffic filter started on 127.0.0.1:$localPort")
            localPort
        } catch (e: Exception) {
            Logger.e("Could not start local app-traffic filter", e)
            serverSocket = null
            null
        }
    }

    fun port(): Int = localPort

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        openSockets.forEach { socket -> runCatching { socket.close() } }
        openSockets.clear()
        proxyScope.cancel()
        Logger.i("Local app-traffic filter stopped")
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (running && proxyScope.isActive) {
            try {
                val client = socket.accept()
                openSockets += client
                proxyScope.launch { handleClient(client) }
            } catch (e: SocketException) {
                if (running) Logger.e("Proxy accept failed: ${e.message}")
            } catch (e: Exception) {
                if (running) Logger.e("Proxy accept error: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = 0
            val input = BufferedInputStream(client.getInputStream(), Constants.PROXY_HEADER_LIMIT * 2)
            val output = client.getOutputStream()
            val head = readHead(input)
            if (head == null) {
                writeForbidden(output, "Malformed request")
                return
            }

            when (head.method.uppercase(Locale.ROOT)) {
                "CONNECT" -> upstream = handleConnect(head, client, input, output)
                "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE" ->
                    upstream = handlePlainHttp(head, client, input, output)
                else -> writeForbidden(output, "Unsupported method")
            }
        } catch (e: Exception) {
            Logger.e("Local proxy connection failed: ${e.message}")
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
            upstream?.let { openSockets.remove(it) }
            openSockets.remove(client)
        }
    }

    private suspend fun handleConnect(
        head: ProxyRequestHead,
        client: Socket,
        input: InputStream,
        output: OutputStream
    ): Socket? {
        val destination = parseAuthority(head.target, Constants.HTTPS_PORT) ?: run {
            writeForbidden(output, "Invalid destination")
            return null
        }

        if (shouldBlock(client, destination.host, "HTTPS")) {
            writeForbidden(output, "Blocked by NOichhar")
            return null
        }

        val socket = connectProtected(destination) ?: run {
            output.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
            return null
        }
        openSockets += socket
        output.write(
            "HTTP/1.1 200 Connection Established\r\nProxy-Agent: NOichhar/2.0\r\n\r\n"
                .toByteArray(Charsets.US_ASCII)
        )
        output.flush()
        relay(client, socket, input, output)
        return socket
    }

    private suspend fun handlePlainHttp(
        head: ProxyRequestHead,
        client: Socket,
        input: InputStream,
        output: OutputStream
    ): Socket? {
        val uri = runCatching { URI(head.target) }.getOrNull()
        val rawHost = when {
            uri != null && uri.host != null -> uri.host
            else -> head.headers["host"]
        } ?: run {
            writeForbidden(output, "Missing host")
            return null
        }

        val defaultPort = if (uri?.scheme?.equals("https", ignoreCase = true) == true) {
            Constants.HTTPS_PORT
        } else {
            Constants.HTTP_PORT
        }
        val destination = parseAuthority(rawHost, defaultPort) ?: run {
            writeForbidden(output, "Invalid host")
            return null
        }
        val port = if (uri?.port != null && uri.port > 0) uri.port else destination.port

        if (shouldBlock(client, destination.host, "HTTP")) {
            writeForbidden(output, "Blocked by NOichhar")
            return null
        }

        val socket = connectProtected(HostPort(destination.host, port)) ?: run {
            output.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
            return null
        }
        openSockets += socket

        val path = when {
            uri != null && uri.rawPath.isNullOrEmpty().not() ->
                uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
            uri != null -> "/"
            else -> head.target
        }
        val request = buildOriginRequest(head, path, destination.host, port)
        socket.getOutputStream().write(request)
        socket.getOutputStream().flush()
        relay(client, socket, input, output)
        return socket
    }

    private suspend fun shouldBlock(client: Socket, host: String, queryType: String): Boolean {
        val normalized = DomainValidator.normalize(host)
        val domainBlocked = normalized != null && domainBlocker.isBlocked(normalized.domain)
        val uid = uidResolver.resolveTcpUid(
            sourcePort = client.port,
            localAddress = client.inetAddress,
            remoteAddress = client.localAddress,
            remotePort = client.localPort
        )
        val decision = firewallManager.evaluateDns(uid, NetworkUtils.isScreenOn(context))
        val blocked = domainBlocked || !decision.allowed
        if (!blocked) return false

        val reason = when {
            domainBlocked -> domainBlocker.getBlockReason(host) ?: "Blocklist rule"
            !decision.allowed -> decision.reason ?: "App rule"
            else -> "Blocked by NOichhar"
        }
        val appName = if (uid >= 0) PackageUtils.getAppName(context, uid) else "Unknown"
        onBlocked(
            DnsQueryEvent(
                domain = host,
                queryType = queryType,
                isBlocked = true,
                blockReason = reason,
                responseTimeMs = 0,
                appUid = uid,
                appName = appName,
                responseIp = null
            )
        )
        return true
    }

    private fun connectProtected(destination: HostPort): Socket? = try {
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.connect(InetSocketAddress(destination.host, destination.port), Constants.PROXY_CONNECT_TIMEOUT_MS)
        socket.soTimeout = 0
        socket
    } catch (e: Exception) {
        Logger.e("Proxy upstream connection failed for ${destination.host}:${destination.port}: ${e.message}")
        null
    }

    private suspend fun relay(
        client: Socket,
        upstream: Socket,
        clientInput: InputStream,
        clientOutput: OutputStream
    ) = coroutineScope {
        val upstreamInput = upstream.getInputStream()
        val upstreamOutput = upstream.getOutputStream()
        val clientToServer = async(Dispatchers.IO) { copy(clientInput, upstreamOutput) }
        val serverToClient = async(Dispatchers.IO) { copy(upstreamInput, clientOutput) }

        select<Unit> {
            clientToServer.onAwait { }
            serverToClient.onAwait { }
        }
        runCatching { client.close() }
        runCatching { upstream.close() }
        clientToServer.cancelAndJoin()
        serverToClient.cancelAndJoin()
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(Constants.PROXY_BUFFER_SIZE)
        try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        } catch (_: Exception) {
            // Socket closed by one side; the relay coroutine will stop.
        }
    }

    private fun readHead(input: InputStream): ProxyRequestHead? {
        val bytes = ByteArrayOutputStream()
        var b1 = 0
        var b2 = 0
        var b3 = 0
        var b4 = 0
        while (bytes.size() < Constants.PROXY_HEADER_LIMIT) {
            val value = input.read()
            if (value < 0) return null
            bytes.write(value)
            b1 = b2
            b2 = b3
            b3 = b4
            b4 = value
            if ((b1 == 13 && b2 == 10 && b3 == 13 && b4 == 10) || (b3 == 10 && b4 == 10)) break
        }
        if (bytes.size() >= Constants.PROXY_HEADER_LIMIT) return null

        val raw = String(bytes.toByteArray(), Charsets.ISO_8859_1)
        val lines = raw.split(Regex("\r?\n")).filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val requestParts = lines[0].split(" ", limit = 3)
        if (requestParts.size < 2) return null

        val headers = LinkedHashMap<String, String>()
        for (index in 1 until lines.size) {
            val separator = lines[index].indexOf(':')
            if (separator > 0) {
                val name = lines[index].substring(0, separator).trim().lowercase(Locale.ROOT)
                val value = lines[index].substring(separator + 1).trim()
                headers[name] = value
            }
        }
        return ProxyRequestHead(
            method = requestParts[0],
            target = requestParts[1],
            headers = headers
        )
    }

    private fun buildOriginRequest(
        head: ProxyRequestHead,
        path: String,
        host: String,
        port: Int
    ): ByteArray {
        val builder = StringBuilder()
        builder.append(head.method.uppercase(Locale.ROOT))
            .append(' ')
            .append(path)
            .append(" HTTP/1.1\r\n")
        val removed = setOf("proxy-authorization", "proxy-connection", "connection", "keep-alive", "upgrade", "host")
        head.headers.forEach { (name, value) ->
            if (name !in removed) builder.append(name).append(": ").append(value).append("\r\n")
        }
        val hostHeader = if (port == Constants.HTTP_PORT) host else "$host:$port"
        builder.append("Host: ").append(hostHeader).append("\r\n")
        builder.append("Connection: close\r\n\r\n")
        return builder.toString().toByteArray(Charsets.ISO_8859_1)
    }

    private fun writeForbidden(output: OutputStream, reason: String) {
        val body = "NOichhar blocked this request: $reason"
        val response = "HTTP/1.1 403 Forbidden\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${body.toByteArray(Charsets.UTF_8).size}\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n\r\n" +
            body
        runCatching {
            output.write(response.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    private fun parseAuthority(value: String, defaultPort: Int): HostPort? {
        var authority = value.trim()
        if (authority.contains("://")) {
            val uri = runCatching { URI(authority) }.getOrNull() ?: return null
            authority = uri.authority ?: return null
            val port = if (uri.port > 0) uri.port else defaultPort
            return HostPort(cleanHost(uri.host ?: return null), port)
        }
        authority = authority.substringBefore('/').substringBefore('?').substringBefore('#').trim()
        if (authority.isEmpty()) return null

        val host: String
        val port: Int
        if (authority.startsWith("[")) {
            val closing = authority.indexOf(']')
            if (closing <= 1) return null
            host = authority.substring(1, closing)
            val remainder = authority.substring(closing + 1)
            port = if (remainder.startsWith(":")) remainder.substring(1).toIntOrNull() ?: return null else defaultPort
        } else {
            val separator = authority.lastIndexOf(':')
            if (separator > 0 && separator < authority.length - 1) {
                host = authority.substring(0, separator)
                port = authority.substring(separator + 1).toIntOrNull() ?: return null
            } else {
                host = authority
                port = defaultPort
            }
        }
        if (port !in 1..65535) return null
        return HostPort(cleanHost(host), port)
    }

    private fun cleanHost(value: String): String = value
        .trim()
        .trim('[', ']')
        .removeSuffix(".")
        .lowercase(Locale.ROOT)

    private data class ProxyRequestHead(
        val method: String,
        val target: String,
        val headers: Map<String, String>
    )

    private data class HostPort(val host: String, val port: Int)
}
