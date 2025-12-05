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
import java.util.concurrent.TimeUnit

object ProxyScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Regex Global Sederhana: IP:Port
    // Cocok untuk file all_proxies.txt yang formatnya baris per baris IP:Port
    private val GLOBAL_REGEX = Regex("""(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}):(\d{2,5})""")

    // Fallback data jika internet mati total
    private val FALLBACK_PROXIES = listOf(
        ProxyItem("1.1.1.1", 80, "US", "Cloudflare (Fallback)", ProxyType.UNKNOWN),
        ProxyItem("8.8.8.8", 80, "US", "Google (Fallback)", ProxyType.UNKNOWN)
    )

    suspend fun scrapeAll(urls: Set<String>, onProgress: (String) -> Unit): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map { url ->
            async { 
                var items = fetchUrl(url, onProgress)
                if (items.isEmpty() && url.contains("githubusercontent")) {
                    val mirror = url.replace("raw.githubusercontent.com", "mirror.ghproxy.com/https://raw.githubusercontent.com")
                    onProgress("Retry Mirror: $mirror")
                    items = fetchUrl(mirror, onProgress)
                }
                items
            }
        }
        val results = deferreds.awaitAll()
        val allProxies = results.flatten().distinctBy { "${it.ip}:${it.port}" }

        if (allProxies.isEmpty()) {
            onProgress("WARNING: No proxies found via network. Using fallback.")
            return@withContext FALLBACK_PROXIES
        }
        
        return@withContext allProxies
    }

    private fun fetchUrl(url: String, onProgress: (String) -> Unit): List<ProxyItem> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            response.close()

            val decoded = if (isBase64(body)) decodeBase64(body) else body
            val items = parseContentGlobal(decoded)
            
            if (items.isNotEmpty()) {
                onProgress("OK: $url (+${items.size})")
            } else {
                onProgress("Empty: $url")
            }
            return items
        } catch (e: Exception) {
            onProgress("Err: $url (${e.message})")
            return emptyList()
        }
    }

    private fun parseContentGlobal(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        
        // 1. Coba parsing V2Ray Links dulu (vless:// etc)
        val lines = content.split("\n", " ")
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                parseUri(trimmed)?.let { items.add(it) }
            }
        }

        // 2. Parsing IP:Port menggunakan Global Regex pada SELURUH teks sekaligus
        // Ini menangani format JSON, CSV, HTML, atau teks acak sekalipun
        val matches = GLOBAL_REGEX.findAll(content)
        for (match in matches) {
            val (ip, portStr) = match.destructured
            val port = portStr.toIntOrNull()
            if (port != null && port <= 65535) {
                // Cek apakah IP valid (0-255)
                if (isValidIp(ip)) {
                    items.add(ProxyItem(ip, port, "Unknown", "Public", ProxyType.UNKNOWN))
                }
            }
        }
        
        return items
    }

    private fun isValidIp(ip: String): Boolean {
        return ip.split(".").all { it.toIntOrNull() in 0..255 }
    }

    private fun parseUri(uri: String): ProxyItem? {
        return try {
            val regexUri = Regex("""@([^:]+):(\d+)""")
            val match = regexUri.find(uri) ?: return null
            val ip = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: return null
            val type = if (uri.startsWith("vless")) ProxyType.VLESS else ProxyType.TROJAN
            ProxyItem(ip, port, "Unknown", "Imported", type)
        } catch (e: Exception) { null }
    }

    private fun isBase64(str: String): Boolean {
        val t = str.trim()
        if (t.contains(" ") || t.length < 20) return false
        return try { Base64.decode(t, Base64.DEFAULT); true } catch (e: Exception) { false }
    }

    private fun decodeBase64(str: String): String {
        return try { String(Base64.decode(str, Base64.DEFAULT)) } catch (e: Exception) { str }
    }
}