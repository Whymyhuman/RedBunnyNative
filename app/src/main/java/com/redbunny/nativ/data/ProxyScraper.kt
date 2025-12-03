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
        val deferreds = urls.map { url ->
            async { fetchUrl(url) }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String): List<ProxyItem> {
        try {
            // Anti-Cache: Add timestamp param
            val finalUrl = if (url.contains("?")) "$url&t=${System.currentTimeMillis()}" else "$url?t=${System.currentTimeMillis()}"
            
            Log.d("ProxyScraper", "Fetching: $finalUrl")
            val request = Request.Builder()
                .url(finalUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            response.close()

            // Try decode if Base64 (common in v2ray subs)
            // Only try if it looks like a single block of base64 (no spaces, long string)
            val trimmedBody = body.trim()
            val content = if (!trimmedBody.contains(" ") && !trimmedBody.contains("\n") && trimmedBody.length > 20) {
                 decodeBase64(trimmedBody) 
            } else {
                 // Some lists are base64 encoded line by line or whole file
                 if (isBase64(trimmedBody)) decodeBase64(trimmedBody) else body
            }
            
            return parseContent(content)
        } catch (e: Exception) {
            Log.e("ProxyScraper", "Error fetching $url: ${e.message}")
            return emptyList()
        }
    }

    private fun parseContent(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        // Split ONLY by newlines to preserve context
        val lines = content.split("\n", "\r") 
        
        // Regex IP yang kuat
        val regexIp = Regex("""\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b""")
        
        // Regex Port yang menerima : (titik dua), , (koma), atau spasi/tab
        // Menangkap angka port 2-5 digit
        val regexPort = Regex("""[:,\\s]+(\\d{2,5})\\b""")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//") || trimmed.startsWith("<")) continue

            // 1. Check standard vless:// trojan:// uris
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://") || trimmed.startsWith("vmess://")) {
                parseUri(trimmed)?.let { items.add(it) }
                continue
            }

            // 2. Check IP format
            val matchIp = regexIp.find(trimmed)
            if (matchIp != null) {
                val ip = matchIp.groupValues[1]
                
                // Cari port SETELAH posisi IP
                // Ini menghindari salah deteksi jika ada angka lain sebelum IP
                val afterIp = trimmed.substring(matchIp.range.last + 1)
                val matchPort = regexPort.find(afterIp)
                
                if (matchPort != null) {
                    val port = matchPort.groupValues[1].toIntOrNull()
                    
                    if (port != null && port in 1..65535) {
                        // Coba ekstrak info tambahan (Country/Provider) jika format CSV
                        // Contoh: IP,Port,Country,Provider
                        var country = "Unknown"
                        var provider = "Public"
                        
                        val parts = trimmed.split(",")
                        if (parts.size >= 3) {
                            // Asumsi sederhana: bagian 2 huruf besar adalah kode negara
                            val potentialCountry = parts.find { it.trim().length == 2 && it.trim().all { c -> c.isUpperCase() } && it.trim() != "IP" }
                            if (potentialCountry != null) country = potentialCountry
                        }

                        items.add(ProxyItem(ip, port, country, provider, ProxyType.UNKNOWN))
                    }
                }
            }
        }
        return items
    }

    private fun parseUri(uri: String): ProxyItem? {
        try {
            val regexUri = Regex("""@([^:]+):(\\d+)"""")
            val match = regexUri.find(uri) ?: return null
            val ip = match.groupValues[1]
            val port = match.groupValues[2].toIntOrNull() ?: return null
            
            val type = when {
                uri.startsWith("vless://") -> ProxyType.VLESS
                uri.startsWith("trojan://") -> ProxyType.TROJAN
                uri.startsWith("vmess://") -> ProxyType.VMESS
                else -> ProxyType.UNKNOWN
            }
            
            val tag = if (uri.contains("#")) java.net.URLDecoder.decode(uri.substringAfterLast("#"), "UTF-8") else "Imported"
            
            return ProxyItem(ip, port, "Unknown", tag, type)
        } catch (e: Exception) {
            return null
        }
    }

    private fun isBase64(str: String): Boolean {
        // Basic check to prevent crashing on normal text
        if (str.contains(" ")) return false
        return try {
            Base64.decode(str, Base64.DEFAULT)
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