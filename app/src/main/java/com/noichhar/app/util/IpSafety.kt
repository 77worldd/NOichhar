package com.noichhar.app.util

object IpSafety {
    fun parseIpv4(value: String): IntArray? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        val bytes = IntArray(4)
        for (i in 0 until 4) {
            val part = parts[i]
            if (part.isEmpty() || part.length > 3 || !part.all { it.isDigit() }) return null
            val number = part.toIntOrNull() ?: return null
            if (number !in 0..255) return null
            bytes[i] = number
        }
        return bytes
    }

    fun isSafeToBlock(value: String): Boolean {
        val ip = parseIpv4(value) ?: return false
        if (value in Constants.PROTECTED_DNS_IPS) return false
        if (value == Constants.VPN_INTERFACE_ADDRESS || value == Constants.VPN_DNS_ADDRESS) return false
        val first = ip[0]
        val second = ip[1]
        if (first == 0 || first == 10 || first == 127) return false
        if (first == 100 && second in 64..127) return false // CGNAT.
        if (first == 169 && second == 254) return false
        if (first == 172 && second in 16..31) return false
        if (first == 192 && second == 168) return false
        if (first == 192 && second == 0 && ip[2] == 0) return false
        if (first == 192 && second == 0 && ip[2] == 2) return false // TEST-NET-1.
        if (first == 198 && (second == 18 || second == 19)) return false // benchmarking.
        if (first == 198 && second == 51 && ip[2] == 100) return false // TEST-NET-2.
        if (first == 203 && second == 0 && ip[2] == 113) return false // TEST-NET-3.
        if (first >= 224) return false // multicast, reserved and broadcast.
        if (first == 255 && second == 255 && ip[2] == 255 && ip[3] == 255) return false
        return true
    }
}
