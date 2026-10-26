package com.noichhar.app.net

import android.content.Context
import android.os.ParcelFileDescriptor
import com.noichhar.app.blocker.DomainBlocker
import com.noichhar.app.dns.DnsCache
import com.noichhar.app.dns.DnsPacketParser
import com.noichhar.app.dns.DnsResolver
import com.noichhar.app.dns.DohResolver
import com.noichhar.app.dns.DotResolver
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.util.Constants
import com.noichhar.app.util.Logger
import com.noichhar.app.util.NetworkUtils
import com.noichhar.app.util.PackageUtils
import com.noichhar.app.util.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

data class DnsQueryEvent(
    val domain: String,
    val queryType: String,
    val isBlocked: Boolean,
    val blockReason: String?,
    val responseTimeMs: Long,
    val appUid: Int,
    val appName: String,
    val responseIp: String?
)

class PacketInterceptor(
    private val context: Context,
    private val vpnFileDescriptor: ParcelFileDescriptor,
    private val domainBlocker: DomainBlocker,
    private val dnsCache: DnsCache,
    private val preferenceManager: PreferenceManager,
    private val firewallManager: FirewallManager,
    private val onDnsQuery: (DnsQueryEvent) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dnsResolver = DnsResolver()
    private val dohResolver = DohResolver()
    private val dotResolver = DotResolver()
    private val uidResolver = UidResolver(context)
    private val outputLock = Any()

    @Volatile
    private var running = false

    fun start() {
        running = true
        scope.launch { processPackets() }
    }

    fun stop() {
        running = false
        scope.cancel()
    }

    private suspend fun processPackets() {
        val inputStream = FileInputStream(vpnFileDescriptor.fileDescriptor)
        val outputStream = FileOutputStream(vpnFileDescriptor.fileDescriptor)
        val buffer = ByteArray(Constants.VPN_MTU)

        while (running && scope.isActive) {
            try {
                val length = inputStream.read(buffer)
                if (length <= 0) {
                    delay(1)
                    continue
                }
                val packetData = buffer.copyOf(length)
                val packet = IPPacket.parse(packetData) ?: continue
                if (!packet.isDnsQuery()) continue

                scope.launch { handleDnsPacket(packet, outputStream) }
            } catch (e: Exception) {
                if (running) {
                    Logger.e("Packet processing error: ${e.message}")
                    delay(10)
                }
            }
        }
    }

    private suspend fun handleDnsPacket(packet: IPPacket, outputStream: FileOutputStream) {
        val dnsPayload = packet.getDnsPayload() ?: return
        val question = DnsPacketParser.parseQuestion(dnsPayload) ?: return
        val domain = question.name
        val queryType = DnsPacketParser.getQueryTypeName(question.type)
        val startTime = System.currentTimeMillis()
        val uid = uidResolver.resolveUdpUid(packet)
        val appName = if (uid >= 0) PackageUtils.getAppName(context, uid) else "Unknown"
        val firewallDecision = firewallManager.evaluateDns(uid, NetworkUtils.isScreenOn(context))

        var blocked = !firewallDecision.allowed
        var blockReason = firewallDecision.reason
        var response: ByteArray? = null

        if (blocked) {
            response = DnsPacketParser.createNxDomainResponse(dnsPayload)
        } else if (domainBlocker.isBlocked(domain)) {
            blocked = true
            blockReason = domainBlocker.getBlockReason(domain) ?: "Blocklist rule"
            response = DnsPacketParser.createNxDomainResponse(dnsPayload)
        } else {
            val cached = dnsCache.get(domain, question.type)
            if (cached != null) {
                response = DnsPacketParser.rewriteTransactionId(cached, dnsPayload)
            } else {
                response = resolveDns(dnsPayload)?.let { upstream ->
                    dnsCache.put(domain, question.type, upstream)
                    DnsPacketParser.rewriteTransactionId(upstream, dnsPayload)
                } ?: DnsPacketParser.createServFailResponse(dnsPayload)
            }
        }

        if (response == null || response.isEmpty()) return
        var finalResponse = response
        if (20 + 8 + finalResponse.size > Constants.VPN_MTU) {
            finalResponse = DnsPacketParser.createServFailResponse(dnsPayload)
        }
        writeDnsResponse(packet, finalResponse, outputStream)

        onDnsQuery(
            DnsQueryEvent(
                domain = domain,
                queryType = queryType,
                isBlocked = blocked,
                blockReason = blockReason,
                responseTimeMs = System.currentTimeMillis() - startTime,
                appUid = uid,
                appName = appName,
                responseIp = if (blocked) null else DnsPacketParser.parseFirstAnswerIp(finalResponse)
            )
        )
    }

    private fun resolveDns(query: ByteArray): ByteArray? = when (preferenceManager.dnsProtocol) {
        Constants.DnsProtocol.DOH -> {
            val url = preferenceManager.getDohUrl()
            if (url.isNotBlank()) dohResolver.resolve(query, url) else resolveSystemDns(query)
        }
        Constants.DnsProtocol.DOT -> {
            val host = preferenceManager.getDotHostname()
            if (host.isNotBlank()) dotResolver.resolve(query, host) else resolveSystemDns(query)
        }
        Constants.DnsProtocol.PLAIN -> {
            val ip = preferenceManager.getPlainDnsIp()
            if (ip.isNotBlank()) dnsResolver.resolveUdp(query, ip) else resolveSystemDns(query)
        }
    }

    private fun resolveSystemDns(query: ByteArray): ByteArray? {
        val servers = NetworkUtils.getSystemDnsServers(context)
            .ifEmpty { listOf(Constants.FALLBACK_DNS_IP) }
        return dnsResolver.resolveUdpAny(query, servers)
    }

    private fun writeDnsResponse(
        originalPacket: IPPacket,
        dnsResponse: ByteArray,
        outputStream: FileOutputStream
    ) {
        val responsePacket = buildUdpResponsePacket(originalPacket, dnsResponse)
        synchronized(outputLock) {
            outputStream.write(responsePacket)
            outputStream.flush()
        }
    }

    private fun buildUdpResponsePacket(originalPacket: IPPacket, dnsResponse: ByteArray): ByteArray {
        val udpLength = 8 + dnsResponse.size
        val ipLength = 20 + udpLength
        val buffer = ByteBuffer.allocate(ipLength)

        buffer.put(0x45.toByte())
        buffer.put(0)
        buffer.putShort(ipLength.toShort())
        buffer.putShort(0)
        buffer.putShort(0x4000.toShort())
        buffer.put(64)
        buffer.put(17)
        buffer.putShort(0)

        buffer.put(originalPacket.destAddress.address)
        buffer.put(originalPacket.sourceAddress.address)

        buffer.putShort(originalPacket.destPort.toShort())
        buffer.putShort(originalPacket.sourcePort.toShort())
        buffer.putShort(udpLength.toShort())
        buffer.putShort(0)
        buffer.put(dnsResponse)

        val result = buffer.array()
        calculateIpChecksum(result)
        return result
    }

    private fun calculateIpChecksum(packet: ByteArray) {
        packet[10] = 0
        packet[11] = 0
        var sum = 0L
        for (i in 0 until 20 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        val checksum = sum.toInt().inv() and 0xFFFF
        packet[10] = (checksum shr 8).toByte()
        packet[11] = (checksum and 0xFF).toByte()
    }
}
