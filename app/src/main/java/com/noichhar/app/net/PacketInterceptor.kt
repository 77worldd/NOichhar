package com.noichhar.app.net

import android.content.Context
import android.os.ParcelFileDescriptor
import com.noichhar.app.blocker.EnhancedBlocklistEngine
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

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
    private val domainBlocker: EnhancedBlocklistEngine,
    private val dnsCache: DnsCache,
    private val preferenceManager: PreferenceManager,
    private val firewallManager: FirewallManager,
    private val blockedIps: Set<String>,
    private val networkShield: NetworkShield,
    private val onDnsQuery: (DnsQueryEvent) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dnsSemaphore = Semaphore(48)
    private val dnsResolver = DnsResolver()
    private val dohResolver = DohResolver()
    private val dotResolver = DotResolver()
    private val uidResolver = UidResolver(context)
    private val outputLock = Any()
    private val tcpSessions = ConcurrentHashMap<String, TcpDnsSession>()
    private val lastBlockedIpLog = ConcurrentHashMap<String, Long>()

    @Volatile
    private var running = false

    private var packetCounter = 0L

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
                packetCounter++
                if (packetCounter % 512L == 0L) pruneTcpSessions()

                val packetData = buffer.copyOf(length)
                val packet = IPPacket.parse(packetData) ?: continue
                val destination = packet.destAddress.hostAddress

                if (packet.isDnsTarget() && packet.isDnsQuery()) {
                    val segment = packet.tcpSegment()
                    if (packet.isTcpDnsQuery() && segment != null) {
                        if (segment.hasFlag(TcpSegment.FLAG_SYN)) {
                            handleTcpSyn(packet, segment, outputStream)
                            continue
                        }
                        if (segment.hasFlag(TcpSegment.FLAG_RST) || segment.hasFlag(TcpSegment.FLAG_FIN)) {
                            tcpSessions.remove(sessionKey(packet))
                            continue
                        }
                    }
                    val dnsPayload = packet.getDnsPayload() ?: continue
                    val tcpSegment = if (packet.isTcpDnsQuery()) segment else null
                    scope.launch {
                        dnsSemaphore.withPermit {
                            handleDnsPacket(packet, tcpSegment, dnsPayload, outputStream)
                        }
                    }
                } else if (destination != null && destination in blockedIps) {
                    handleBlockedIp(packet, destination, outputStream)
                }
                // Any other packet is intentionally ignored. Normal traffic is not
                // routed into the TUN interface, so it never reaches this loop.
            } catch (e: Exception) {
                if (running) {
                    Logger.e("Packet processing error: ${e.message}")
                    delay(10)
                }
            }
        }
    }

    private fun handleTcpSyn(packet: IPPacket, segment: TcpSegment, outputStream: FileOutputStream) {
        val initialSequence = Random.nextLong(1L, 0xFFFFFFFFL)
        val acknowledgement = segment.nextSequenceNumber()
        tcpSessions[sessionKey(packet)] = TcpDnsSession(
            nextSequence = (initialSequence + 1L) and 0xFFFFFFFFL,
            createdAt = System.currentTimeMillis()
        )
        val response = PacketBuilder.tcpSynAck(packet, initialSequence, acknowledgement)
        synchronized(outputLock) {
            outputStream.write(response)
            outputStream.flush()
        }
    }

    private suspend fun handleDnsPacket(
        packet: IPPacket,
        tcpSegment: TcpSegment?,
        dnsPayload: ByteArray,
        outputStream: FileOutputStream
    ) {
        val question = DnsPacketParser.parseQuestion(dnsPayload) ?: return
        val domain = question.name
        val queryType = DnsPacketParser.getQueryTypeName(question.type)
        val startTime = System.currentTimeMillis()
        val uid = if (packet.isTcpDnsQuery()) {
            uidResolver.resolveTcpUid(packet)
        } else {
            uidResolver.resolveUdpUid(packet)
        }
        val appName = if (uid >= 0) PackageUtils.getAppName(context, uid) else "Unknown"
        val firewallDecision = firewallManager.evaluateDns(uid, NetworkUtils.isScreenOn(context))

        var blocked = !firewallDecision.allowed
        var blockReason = firewallDecision.reason
        var response: ByteArray = if (blocked) {
            DnsPacketParser.createNxDomainResponse(dnsPayload)
        } else if (preferenceManager.protectionMode.domainFiltering && domainBlocker.isBlocked(domain)) {
            blocked = true
            blockReason = domainBlocker.getBlockReason(domain) ?: "Blocklist rule"
            DnsPacketParser.createNxDomainResponse(dnsPayload)
        } else {
            val cached = dnsCache.get(domain, question.type)
            if (cached != null) {
                DnsPacketParser.rewriteTransactionId(cached, dnsPayload)
            } else {
                val upstream = resolveDns(dnsPayload)
                if (upstream == null) {
                    DnsPacketParser.createServFailResponse(dnsPayload)
                } else {
                    val blockedAlias = DnsPacketParser.parseAnswerNames(upstream)
                        .firstOrNull { alias -> preferenceManager.protectionMode.domainFiltering &&
                            alias != domain.lowercase() && domainBlocker.isBlocked(alias) }
                    if (blockedAlias != null) {
                        blocked = true
                        blockReason = "Blocked upstream alias: $blockedAlias"
                        DnsPacketParser.createNxDomainResponse(dnsPayload)
                    } else {
                        dnsCache.put(domain, question.type, upstream)
                        DnsPacketParser.rewriteTransactionId(upstream, dnsPayload)
                    }
                }
            }
        }

        if (response.isEmpty()) return
        val overhead = if (packet.isTcpDnsQuery()) 20 + 20 + 2 else 20 + 8
        if (overhead + response.size > Constants.VPN_MTU) {
            response = DnsPacketParser.createServFailResponse(dnsPayload)
        }

        writeDnsResponse(packet, tcpSegment, response, outputStream)

        onDnsQuery(
            DnsQueryEvent(
                domain = domain,
                queryType = queryType,
                isBlocked = blocked,
                blockReason = blockReason,
                responseTimeMs = System.currentTimeMillis() - startTime,
                appUid = uid,
                appName = appName,
                responseIp = if (blocked) null else DnsPacketParser.parseFirstAnswerIp(response)
            )
        )
    }

    private fun writeDnsResponse(
        packet: IPPacket,
        tcpSegment: TcpSegment?,
        dnsResponse: ByteArray,
        outputStream: FileOutputStream
    ) {
        val responsePacket = if (packet.isTcpDnsQuery()) {
            val segment = tcpSegment ?: return
            val session = tcpSessions.remove(sessionKey(packet))
            val sequence = session?.nextSequence
                ?: (segment.acknowledgementNumber and 0xFFFFFFFFL)
            val acknowledgement = segment.nextSequenceNumber()
            PacketBuilder.tcpDnsResponse(
                original = packet,
                dnsResponse = dnsResponse,
                sequenceNumber = sequence,
                acknowledgementNumber = acknowledgement
            )
        } else {
            PacketBuilder.udpDnsResponse(packet, dnsResponse)
        }

        synchronized(outputLock) {
            outputStream.write(responsePacket)
            outputStream.flush()
        }
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

    private fun handleBlockedIp(packet: IPPacket, destination: String, outputStream: FileOutputStream) {
        if (packet.protocol == IPPacket.PROTOCOL_TCP) {
            val reset = PacketBuilder.tcpReset(packet)
            synchronized(outputLock) {
                outputStream.write(reset)
                outputStream.flush()
            }
        }

        val now = System.currentTimeMillis()
        val last = lastBlockedIpLog[destination] ?: 0L
        if (now - last < 5_000L) return
        lastBlockedIpLog[destination] = now

        scope.launch {
            val uid = if (packet.protocol == IPPacket.PROTOCOL_TCP) {
                uidResolver.resolveTcpUid(packet)
            } else {
                uidResolver.resolveUdpUid(packet)
            }
            val appName = if (uid >= 0) PackageUtils.getAppName(context, uid) else "Unknown"
            onDnsQuery(
                DnsQueryEvent(
                    domain = networkShield.domainFor(destination) ?: "Known ad server",
                    queryType = "IP",
                    isBlocked = true,
                    blockReason = "Network shield: $destination",
                    responseTimeMs = 0,
                    appUid = uid,
                    appName = appName,
                    responseIp = destination
                )
            )
        }
    }

    private fun sessionKey(packet: IPPacket): String =
        "${packet.sourceAddress.hostAddress}:${packet.sourcePort}->" +
            "${packet.destAddress.hostAddress}:${packet.destPort}"

    private fun pruneTcpSessions() {
        val cutoff = System.currentTimeMillis() - 30_000L
        tcpSessions.entries.removeIf { it.value.createdAt < cutoff }
        if (lastBlockedIpLog.size > 2_000) lastBlockedIpLog.clear()
    }

    private data class TcpDnsSession(
        val nextSequence: Long,
        val createdAt: Long
    )
}
