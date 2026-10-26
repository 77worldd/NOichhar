package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

class HostsFileParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun downloadAndParse(url: String): List<String> {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e("Failed to download: $url (${response.code})")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            return parse(body)
        }
    }

    fun parse(content: String): List<String> {
        val domains = LinkedHashSet<String>()
        BufferedReader(StringReader(content)).use { reader ->
            reader.forEachLine { rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEachLine

                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                val candidate = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::" || parts[0] == "::1") -> parts[1]
                    parts.size == 1 -> parts[0].removePrefix("||").removeSuffix("^")
                    else -> null
                } ?: return@forEachLine

                if (candidate.equals("localhost", ignoreCase = true) ||
                    candidate.equals("localhost.localdomain", ignoreCase = true) ||
                    candidate.equals("broadcasthost", ignoreCase = true)
                ) return@forEachLine

                val rule = DomainValidator.normalize(candidate) ?: return@forEachLine
                domains.add(com.noichhar.app.util.DomainValidator.display(rule))
            }
        }
        return domains.toList()
    }
}
