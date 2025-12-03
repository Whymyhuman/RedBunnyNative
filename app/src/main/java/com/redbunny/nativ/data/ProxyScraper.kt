package com.redbunny.nativ.data

import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import android.util.Log

object ProxyScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // List of raw text proxy sources
    private val SOURCES = listOf(
        "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt", // Original source
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt", // Generic SOCKS5 (often adaptable)
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        // Add more reliable raw lists here. Many vless lists are base64 encoded configs, 
        // but for now we stick to IP:Port scraping as requested by the "Original" logic.
    )

    suspend fun scrapeAll(): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = SOURCES.map { url ->
            async { fetchUrl(url) }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String): List<ProxyItem> {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()

            return parseContent(body)
        } catch (e: Exception) {
            Log.e("ProxyScraper", "Error fetching $url: ${e.message}")
            return emptyList()
        }
    }

    private fun parseContent(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        val lines = content.split("\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            // Attempt to parse standard IP:Port format
            // Regex for IP:Port
            val match = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d+)""").find(trimmed)
            
            if (match != null) {
                val (ip, portStr) = match.destructured
                val port = portStr.toIntOrNull()
                
                if (port != null && port in 1..65535) {
                    // Try to extract country if available (comma separated)
                    val parts = trimmed.split(",")
                    var country = "Unknown"
                    var provider = "Public"
                    
                    if (parts.size >= 3) {
                        country = parts[2].trim().uppercase()
                        if (parts.size >= 4) provider = parts.subList(3, parts.size).joinToString(" ")
                    }

                    items.add(ProxyItem(ip, port, country, provider, ProxyType.UNKNOWN))
                }
            }
        }
        return items
    }
}
