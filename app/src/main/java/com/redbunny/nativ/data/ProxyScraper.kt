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
        .connectTimeout(20, TimeUnit.SECONDS) // Perpanjang timeout
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Regex IP yang sangat sederhana (Brute force)
    private val SIMPLE_IP_REGEX = Regex("""\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b""")

    suspend fun scrapeAll(urls: Set<String>, onProgress: (String) -> Unit): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map {
            async {
                // Coba URL asli
                var items = fetchUrl(it, onProgress)

                // Jika gagal/kosong dan ini adalah link GitHub, coba pakai Mirror (ghproxy)
                if (items.isEmpty() && it.contains("raw.githubusercontent.com")) {
                    val mirrorUrl = "https://mirror.ghproxy.com/$it"
                    onProgress("Retrying with mirror: $mirrorUrl")
                    items = fetchUrl(mirrorUrl, onProgress)
                }
                items
            }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String, onProgress: (String) -> Unit): List<ProxyItem> {
        try {
            val finalUrl = if (url.contains("?")) "$url&t=${System.currentTimeMillis()}" else "$url?t=${System.currentTimeMillis()}"

            val request = Request.Builder()
                .url(finalUrl)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36") // PENTING!
                .header("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                onProgress("Failed ${response.code}: $url")
                response.close()
                return emptyList()
            }

            val body = response.body?.string() ?: return emptyList()
            response.close()

            val trimmedBody = body.trim()

            // Deteksi Base64 Murni (tanpa spasi/newline)
            val content = if (!trimmedBody.contains(" ") && !trimmedBody.contains("\n") && trimmedBody.length > 20) {
                 decodeBase64(trimmedBody)
            } else {
                 if (isBase64(trimmedBody)) decodeBase64(trimmedBody) else body
            }

            val parsed = parseContent(content)
            if (parsed.isNotEmpty()) {
                onProgress("Got ${parsed.size} from $url")
            } else {
                onProgress("No proxies found in $url")
            }
            return parsed

        } catch (e: Exception) {
            onProgress("Error $url: ${e.message}")
            return emptyList()
        }
    }

    private fun parseContent(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        val lines = content.split("\n", "\r")

        // 1. Regex Presisi (IP:Port atau IP,Port)
        // Menangkap: IP di grup 1, Port di grup 2
        val preciseRegex = Regex("""\\b(\\d{1,3}\\.{\\d{1,3}}\\.{\\d{1,3}}\\.{\\d{1,3}})\\b[:,\\s]+(\\d{2,5})\\b""")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("<")) continue

            // VLESS/Trojan Links
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://") || trimmed.startsWith("vmess://")) {
                parseUri(trimmed)?.let { items.add(it) }
                continue
            }

            // Coba Regex Presisi dulu
            val preciseMatch = preciseRegex.find(trimmed)
            if (preciseMatch != null) {
                val ip = preciseMatch.groupValues[1]
                val port = preciseMatch.groupValues[2].toIntOrNull()

                if (port != null && port in 1..65535) {
                    items.add(ProxyItem(ip, port, "Unknown", "Public", ProxyType.UNKNOWN))
                    continue // Lanjut ke baris berikutnya jika sudah ketemu
                }
            }

            // Fallback: Brute Force IP (Jika format aneh)
            // Cari IP saja, lalu asumsikan angka berikutnya adalah port jika ada
            val ipMatches = SIMPLE_IP_REGEX.findAll(trimmed)
            for (match in ipMatches) {
                val ip = match.value
                // Cari angka setelah IP ini
                val afterIp = trimmed.substring(match.range.last + 1)
                val portMatch = Regex("\\d{2,5}").find(afterIp)
                if (portMatch != null) {
                     val port = portMatch.value.toIntOrNull()
                     if (port != null) {
                         items.add(ProxyItem(ip, port, "Unknown", "Public", ProxyType.UNKNOWN))
                     }
                }
            }
        }
        return items
    }

    private fun parseUri(uri: String): ProxyItem? {
        try {
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
            val tag = if (uri.contains("#")) java.net.URLDecoder.decode(uri.substringAfterLast("#"), "UTF-8") else "Imported"
            return ProxyItem(ip, port, "Unknown", tag, type)
        } catch (e: Exception) { return null }
    }

    private fun isBase64(str: String): Boolean {
        if (str.contains(" ")) return false
        return try { Base64.decode(str, Base64.DEFAULT); true } catch (e: Exception) { false }
    }

    private fun decodeBase64(str: String): String {
        return try { String(Base64.decode(str, Base64.DEFAULT)) } catch (e: Exception) { str }
    }
}
