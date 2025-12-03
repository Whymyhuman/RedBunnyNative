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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun scrapeAll(urls: Set<String>): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map {
            async { fetchUrl(it) }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String): List<ProxyItem> {
        try {
            // Anti-Cache: Add timestamp param + Cache-Control header
            val finalUrl = if (url.contains("?")) "$url&t=${System.currentTimeMillis()}" else "$url?t=${System.currentTimeMillis()}"
            
            Log.d("ProxyScraper", "Fetching: $finalUrl")
            val request = Request.Builder()
                .url(finalUrl)
                .cacheControl(CacheControl.FORCE_NETWORK) // Force fetch from network
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            response.close()

            // Try decode if Base64 (common in v2ray subs)
            val content = if (isBase64(body)) decodeBase64(body) else body
            return parseContent(content)
        } catch (e: Exception) {
            Log.e("ProxyScraper", "Error fetching $url: ${e.message}")
            return emptyList()
        }
    }

    private fun parseContent(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        val lines = content.split("\n", "\r", " ") // Split by space too for some formats
        
        // Regex IP:Port yang fleksibel
        val regexIp = Regex("\\b(\\d{1,3}\\.{\\d{1,3}}\\.{\\d{1,3}}\\.{\\d{1,3}})\\b")
        val regexPort = Regex("[:\\s]+(\\d{2,5})\\b")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue

            // 1. Check standard vless:// trojan:// uris
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://") || trimmed.startsWith("vmess://")) {
                parseUri(trimmed)?.let { items.add(it) }
                continue
            }

            // 2. Check IP:Port format
            val matchIp = regexIp.find(trimmed)
            if (matchIp != null) {
                val ip = matchIp.groupValues[1]
                // Look for port after IP
                val afterIp = trimmed.substring(matchIp.range.last + 1)
                val matchPort = regexPort.find(afterIp)
                
                if (matchPort != null) {
                    val port = matchPort.groupValues[1].toIntOrNull()
                    if (port != null && port in 1..65535) {
                        items.add(ProxyItem(ip, port, "Unknown", "Public", ProxyType.UNKNOWN))
                    }
                } else if (trimmed.contains(":")) {
                     // Simple split fallback
                     val parts = trimmed.split(":")
                     if (parts.size >= 2) {
                         val p = parts[1].filter { it.isDigit() }.toIntOrNull()
                         if (p != null) items.add(ProxyItem(ip, p, "Unknown", "Public", ProxyType.UNKNOWN))
                     }
                }
            }
        }
        return items
    }

    private fun parseUri(uri: String): ProxyItem? {
        try {
            // Basic parsing for vless/trojan to extract IP and Port
            // vless://uuid@ip:port?...
            val regexUri = Regex("@([^:]+):(\\d+)")
            val match = regexUri.find(uri) ?: return null
            val ip = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: return null
            
            val type = when {
                uri.startsWith("vless://") -> ProxyType.VLESS
                uri.startsWith("trojan://") -> ProxyType.TROJAN
                uri.startsWith("vmess://") -> ProxyType.VMESS
                else -> ProxyType.UNKNOWN
            }
            
            // Extract name if possible (after #)
            val tag = if (uri.contains("#")) java.net.URLDecoder.decode(uri.substringAfterLast("#"), "UTF-8") else "Imported"
            
            return ProxyItem(ip, port, "Unknown", tag, type)
        } catch (e: Exception) {
            return null
        }
    }

    private fun isBase64(str: String): Boolean {
        val trimmed = str.trim()
        // Basic check: no spaces, length multiple of 4 (with padding), only base64 chars
        if (trimmed.contains(" ") || trimmed.length < 20) return false
        return try {
            Base64.decode(trimmed, Base64.DEFAULT)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeBase64(str: String): String {
        return try {
            String(Base64.decode(str, Base64.DEFAULT))
        } catch (e: Exception) {
            str
        }
    }
}
