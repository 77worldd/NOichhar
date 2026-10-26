package com.noichhar.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress

class UidResolver(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun resolveUdpUid(packet: IPPacket): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val local = InetSocketAddress(packet.sourceAddress, packet.sourcePort)
                val remote = InetSocketAddress(packet.destAddress, packet.destPort)
                val uid = connectivityManager.getConnectionOwnerUid(OsConstants.IPPROTO_UDP, local, remote)
                if (uid >= 0) return uid
            }
        }
        return resolveUidFromProc(packet.sourcePort)
    }

    private fun resolveUidFromProc(sourcePort: Int): Int {
        if (sourcePort <= 0) return -1
        val portHex = sourcePort.toString(16).uppercase().padStart(4, '0')
        for (path in listOf("/proc/net/udp", "/proc/net/udp6")) {
            try {
                BufferedReader(FileReader(path)).use { reader ->
                    reader.lineSequence().drop(1).forEach { line ->
                        val columns = line.trim().split(Regex("\\s+"))
                        if (columns.size > 8 && columns[1].endsWith(":$portHex", ignoreCase = true)) {
                            return columns[7].toIntOrNull() ?: -1
                        }
                    }
                }
            } catch (_: Exception) {
                // /proc tables can disappear while a socket closes.
            }
        }
        return -1
    }
}
