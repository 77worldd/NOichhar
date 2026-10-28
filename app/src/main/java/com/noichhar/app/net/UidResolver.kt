package com.noichhar.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.system.OsConstants
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress

class UidResolver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Battery: on API < 29 the UID fallback parses /proc/net/tcp{,6,udp,udp6} for every
     * packet. Those tables are large, so a burst of DNS queries meant re-reading and
     * re-splitting thousands of lines many times a second. Cache the parsed port→uid
     * map for one second and force a single refresh on a miss.
     */
    private val procCache = HashMap<String, ProcSnapshot>()
    private val procLock = Any()

    private class ProcSnapshot(val at: Long, val byPort: Map<String, Int>)

    fun resolveUdpUid(packet: IPPacket): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val local = InetSocketAddress(packet.sourceAddress, packet.sourcePort)
                val remote = InetSocketAddress(packet.destAddress, packet.destPort)
                val uid = connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                if (uid >= 0) return uid
            }
        }
        return resolveUidFromProc(packet.sourcePort, listOf("/proc/net/udp", "/proc/net/udp6"))
    }

    fun resolveTcpUid(packet: IPPacket): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val local = InetSocketAddress(packet.sourceAddress, packet.sourcePort)
                val remote = InetSocketAddress(packet.destAddress, packet.destPort)
                val uid = connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, local, remote)
                if (uid >= 0) return uid
            }
        }
        return resolveUidFromProc(packet.sourcePort, listOf("/proc/net/tcp", "/proc/net/tcp6"))
    }

    fun resolveTcpUid(sourcePort: Int, localAddress: java.net.InetAddress?, remoteAddress: java.net.InetAddress?, remotePort: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && localAddress != null && remoteAddress != null) {
            runCatching {
                val local = InetSocketAddress(localAddress, sourcePort)
                val remote = InetSocketAddress(remoteAddress, remotePort)
                val uid = connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_TCP, local, remote)
                if (uid >= 0) return uid
            }
        }
        return resolveUidFromProc(sourcePort, listOf("/proc/net/tcp", "/proc/net/tcp6"))
    }

    private fun resolveUidFromProc(sourcePort: Int, paths: List<String>): Int {
        if (sourcePort <= 0) return -1
        val portHex = sourcePort.toString(16).uppercase().padStart(4, '0')
        for (path in paths) {
            val cached = procTable(path, maxAgeMs = PROC_CACHE_MS)[portHex]
            if (cached != null) return cached
        }
        // The socket may have opened after the cached snapshot was taken: refresh once.
        for (path in paths) {
            val fresh = procTable(path, maxAgeMs = 0L)[portHex]
            if (fresh != null) return fresh
        }
        return -1
    }

    private fun procTable(path: String, maxAgeMs: Long): Map<String, Int> {
        val now = SystemClock.elapsedRealtime()
        synchronized(procLock) {
            val cached = procCache[path]
            if (cached != null && now - cached.at <= maxAgeMs) return cached.byPort
        }
        val parsed = parseProc(path)
        synchronized(procLock) { procCache[path] = ProcSnapshot(now, parsed) }
        return parsed
    }

    private fun parseProc(path: String): Map<String, Int> {
        val table = HashMap<String, Int>()
        try {
            BufferedReader(FileReader(path)).use { reader ->
                reader.lineSequence().drop(1).forEach { line ->
                    // Format: sl, local_address, rem_address, st, ..., uid (column 7)
                    val columns = line.trim().split(WHITESPACE)
                    if (columns.size <= 8) return@forEach
                    val local = columns[1]
                    val portStart = local.lastIndexOf(':')
                    if (portStart <= 0) return@forEach
                    val portHex = local.substring(portStart + 1).uppercase()
                    val uid = columns[7].toIntOrNull() ?: return@forEach
                    table.putIfAbsent(portHex, uid)
                }
            }
        } catch (_: Exception) {
            // /proc tables can disappear while a socket closes.
        }
        return table
    }

    private companion object {
        const val PROC_CACHE_MS = 1_000L
        val WHITESPACE = Regex("\\s+")
    }
}
