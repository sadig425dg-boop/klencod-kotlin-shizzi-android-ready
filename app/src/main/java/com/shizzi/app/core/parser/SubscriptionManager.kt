package com.shizzi.app.core.parser

import com.shizzi.app.core.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Downloads a subscription URL body and parses it into [ServerConfig] rows. */
class SubscriptionManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun fetch(url: String): List<ServerConfig> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Shizzi/1.0 (compatible; v2rayNG)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("فشل تحميل الاشتراك: HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            ConfigParser.parseImportText(body).map { it.copy(source = "subscription", subscriptionUrl = url) }
        }
    }

    fun isUrl(value: String): Boolean = value.trim().let { it.startsWith("http://") || it.startsWith("https://") }
}
