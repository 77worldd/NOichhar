package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class ParsedRules(
    val blocked: LinkedHashSet<String> = linkedSetOf(),
    val allowed: LinkedHashSet<String> = linkedSetOf()
) {
    val total: Int get() = blocked.size + allowed.size
}

class HostsFileParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val ignoredHosts = setOf(
        "localhost", "localhost.localdomain", "broadcasthost", "local",
        "ip6-localhost", "ip6-loopback", "ip6-localnet", "ip6-mcastprefix",
        "ip6-allnodes", "ip6-allrouters", "0.0.0.0", "127.0.0.1", "::1", "::"
    )

    private val hostPrefixes = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "fe80::1%lo0")

    fun downloadAndParse(url: String): List<String> = downloadAndParseRules(url).blocked.toList()

    fun downloadAndParseRules(url: String, limit: Int = com.noichhar.app.util.Constants.MAX_BLOCKLIST_ENTRIES): ParsedRules {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NOichhar/2.0 blocklist-updater")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.e("Failed to download: $url (${response.code})")
                    return ParsedRules()
                }
                val stream = response.body?.byteStream() ?: return ParsedRules()
                stream.use { parse(it, limit) }
            }
        } catch (e: Exception) {
            Logger.e("Blocklist download failed: $url - ${e.message}")
            ParsedRules()
        }
    }

    fun parse(content: String): List<String> = parseRules(content).blocked.toList()

    fun parseRules(content: String): ParsedRules {
        val result = ParsedRules()
        BufferedReader(StringReader(content)).use { reader ->
            reader.forEachLine { line -> parseLine(line, result) }
        }
        return result
    }

    fun parse(stream: InputStream, limit: Int = com.noichhar.app.util.Constants.MAX_BLOCKLIST_ENTRIES): ParsedRules {
        val result = ParsedRules()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            while (result.total < limit) {
                val line = reader.readLine() ?: break
                parseLine(line, result)
            }
        }
        return result
    }

    private fun parseLine(rawLine: String, result: ParsedRules) {
        var line = rawLine.trim()
        if (line.isEmpty()) return

        // Adblock Plus metadata and cosmetic rules are not DNS rules.
        if (line.startsWith("!") || line.startsWith("[") ||
            line.contains("##") || line.contains("#@#") || line.contains("#?#")
        ) return

        // Hosts files use '#' comments; strip inline comments safely.
        if (line.startsWith("#")) return
        line = line.substringBefore('#').trim()
        if (line.isEmpty()) return

        val allow = line.startsWith("@@")
        if (allow) line = line.removePrefix("@@").trim()
        if (line.isEmpty()) return

        val candidates = mutableListOf<String>()

        when {
            line.startsWith("||") -> {
                val rule = line.removePrefix("||")
                    .substringBefore('^')
                    .substringBefore('$')
                    .substringBefore('/')
                    .trim()
                if (rule.isNotEmpty()) candidates += rule
            }

            line.startsWith("address=/") || line.startsWith("local=/") -> {
                val rule = line.substringAfter('/').substringBefore('/').trim()
                if (rule.isNotEmpty()) candidates += rule
            }

            else -> {
                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.isEmpty()) return
                val first = parts[0]
                if (hostPrefixes.contains(first.lowercase()) || looksLikeIp(first)) {
                    candidates += parts.drop(1)
                } else if (parts.size == 1) {
                    candidates += first
                } else {
                    // Compressed hosts files may contain several domains after an IP.
                    candidates += parts
                }
            }
        }

        for (rawCandidate in candidates) {
            val candidate = rawCandidate
                .removePrefix("||")
                .removeSuffix("^")
                .substringBefore('$')
                .substringBefore('/')
                .trim()
                .removeSuffix(".")
            if (candidate.isEmpty() || candidate.lowercase() in ignoredHosts || looksLikeIp(candidate)) continue
            val rule = DomainValidator.normalize(candidate) ?: continue
            val display = DomainValidator.display(rule)
            if (allow) result.allowed += display else result.blocked += display
        }
    }

    private fun looksLikeIp(value: String): Boolean {
        val candidate = value.trim().trim('[', ']')
        if (candidate.isEmpty()) return false
        if (candidate.all { it.isDigit() || it == '.' }) return true
        if (candidate.contains(':') &&
            candidate.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
        ) return true
        return false
    }
}
