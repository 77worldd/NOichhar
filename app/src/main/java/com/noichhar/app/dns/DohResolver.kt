package com.noichhar.app.dns

import com.noichhar.app.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class DohResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun resolve(query: ByteArray, dohUrl: String): ByteArray? = try {
        val mediaType = "application/dns-message".toMediaType()
        val request = Request.Builder()
            .url(dohUrl)
            .post(query.toRequestBody(mediaType))
            .header("Accept", "application/dns-message")
            .header("Content-Type", "application/dns-message")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.e("DoH resolve failed: HTTP ${response.code}")
                return@use null
            }
            response.body?.bytes()?.takeIf { it.size >= 12 }
        }
    } catch (e: Exception) {
        Logger.e("DoH resolve failed: ${e.message}")
        null
    }
}
