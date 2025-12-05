package com.redbunny.nativ.data

import android.util.Base64
import android.util.Log
import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ProxyScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS) // Lebih lama lagi
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Regex Global Sederhana: IP:Port
    private val GLOBAL_REGEX = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{2,5})""")

    private val FALLBACK_PROXIES = listOf(
        ProxyItem("1.1.1.1", 80, "US", "Cloudflare (Fallback)", ProxyType.UNKNOWN),
        ProxyItem("8.8.8.8", 80, "US", "Google (Fallback)", ProxyType.UNKNOWN)
    )

    suspend fun scrapeAll(urls: Set<String>, onProgress: (String) -> Unit): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map {
            async {
                var items = fetchUrl(it, onProgress)
                // Jika gagal dan URL adalah GitHub API, coba raw sebagai fallback (kebalikan dari sebelumnya)
                if (items.isEmpty() && it.contains("api.github.com")) {
                    val rawUrl = "https://raw.githubusercontent.com/Whymyhuman/RedBunnyNative/main/all_proxies.txt"
                    onProgress("API Failed. Trying Raw: $rawUrl")
                    items = fetchUrl(rawUrl, onProgress)
                }
                items
            }
        }
        val results = deferreds.awaitAll()
        val allProxies = results.flatten().distinctBy { "${it.ip}:${it.port}" }

        if (allProxies.isEmpty()) {
            onProgress("CRITICAL: All sources failed. Check internet connection.")
            return@withContext FALLBACK_PROXIES
        }
        
        return@withContext allProxies
    }

    private fun fetchUrl(url: String, onProgress: (String) -> Unit): List<ProxyItem> {
        try {
            // Add cache buster for raw urls, API urls ignore it mostly but fine
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RedBunnyNative/1.0") // Simple UA
                .header("Accept", "application/vnd.github.v3+json, text/plain") 
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (!response.isSuccessful || responseBody == null) {
                onProgress("Err ${response.code}: $url")
                return emptyList()
            }

            var content = responseBody!!

            // 1. Cek apakah ini respon JSON dari GitHub API?
            if (responseBody.trim().startsWith("{") && url.contains("api.github.com")) {
                try {
                    val json = JSONObject(responseBody)
                    // GitHub API returns content in Base64 with newlines
                    val encodedContent = json.optString("content").replace("\n", "")
                    if (encodedContent.isNotEmpty()) {
                        content = String(Base64.decode(encodedContent, Base64.DEFAULT))
                        onProgress("Decoded GitHub API response")
                    }
                } catch (e: Exception) {
                    onProgress("JSON Parse Error: ${e.message}")
                }
            } 
            // 2. Cek Base64 biasa (Raw file)
            else if (isBase64(content.trim())) {
                content = decodeBase64(content.trim())
            }

            val items = parseContentGlobal(content)
            
            if (items.isNotEmpty()) {
                onProgress("Success: Retrieved ${items.size} proxies")
            } else {
                onProgress("Zero proxies found in content")
            }
            return items

        } catch (e: Exception) {
            onProgress("Exception: ${e.message}")
            Log.e("ProxyScraper", "Fetch error", e)
            return emptyList()
        }
    }

    private fun parseContentGlobal(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        val matches = GLOBAL_REGEX.findAll(content)
        for (match in matches) {
            val (ip, portStr) = match.destructured
            val port = portStr.toIntOrNull()
            if (port != null && port <= 65535 && isValidIp(ip)) {
                items.add(ProxyItem(ip, port, "Unknown", "Public", ProxyType.UNKNOWN))
            }
        }
        return items
    }

    private fun isValidIp(ip: String): Boolean {
        return ip.split(".").all { it.toIntOrNull() in 0..255 }
    }

    private fun isBase64(str: String): Boolean {
        if (str.contains(" ") || str.length < 20) return false
        return try { Base64.decode(str, Base64.DEFAULT); true } catch (e: Exception) { false }
    }

    private fun decodeBase64(str: String): String {
        return try { String(Base64.decode(str, Base64.DEFAULT)) } catch (e: Exception) { str }
    }
}
