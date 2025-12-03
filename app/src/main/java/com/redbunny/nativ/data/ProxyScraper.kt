package com.redbunny.nativ.data

import android.util.Log
import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ProxyScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun scrapeAll(urls: Set<String>): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map { url ->
            async { fetchUrl(url) }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String): List<ProxyItem> {
        try {
            Log.d("ProxyScraper", "Fetching: $url")
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
        
        // Regex IP:Port yang fleksibel
        val regex = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})[:\s]+(\d+)""")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            // Coba parsing
            val match = regex.find(trimmed)
            
            if (match != null) {
                val (ip, portStr) = match.destructured
                val port = portStr.toIntOrNull()
                
                if (port != null && port in 1..65535) {
                    // Deteksi Country jika ada di baris tersebut (format CSV umum)
                    // Contoh: 1.1.1.1:80,US,Cloudflare
                    val parts = trimmed.split(",", " ", "\t", ";")
                    var country = "Unknown"
                    var provider = "Public"
                    
                    // Logika sederhana menebak posisi country (biasanya 2 huruf besar)
                    for (part in parts) {
                        val p = part.trim()
                        if (p.length == 2 && p.all { it.isUpperCase() } && p != "IP" && p != "OK") {
                            country = p
                            break
                        }
                    }

                    items.add(ProxyItem(ip, port, country, provider, ProxyType.UNKNOWN))
                }
            }
        }
        return items
    }
}