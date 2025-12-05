package com.redbunny.nativ.data

import android.util.Base64
import android.util.Log
import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object ProxyScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun scrapeAll(urls: Set<String>, onProgress: (String) -> Unit): List<ProxyItem> = withContext(Dispatchers.IO) {
        val deferreds = urls.map {
            async {
                var items = fetchUrl(it, onProgress)
                if (items.isEmpty() && it.contains("api.github.com")) {
                    val rawUrl = "https://raw.githubusercontent.com/Whymyhuman/RedBunnyNative/main/active_proxies.txt"
                    onProgress("API Failed. Trying Raw: $rawUrl")
                    items = fetchUrl(rawUrl, onProgress)
                }
                items
            }
        }
        val results = deferreds.awaitAll()
        return@withContext results.flatten().distinctBy { "${it.ip}:${it.port}" }
    }

    private fun fetchUrl(url: String, onProgress: (String) -> Unit): List<ProxyItem> {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "RedBunnyNative/1.0")
                .header("Accept", "application/vnd.github.v3+json, text/plain") 
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            response.close()

            if (!response.isSuccessful || responseBody == null) {
                return emptyList()
            }

            var content = responseBody!!

            if (responseBody.trim().startsWith("{") && url.contains("api.github.com")) {
                try {
                    val json = JSONObject(responseBody)
                    val encodedContent = json.optString("content").replace("\n", "")
                    if (encodedContent.isNotEmpty()) {
                        content = String(Base64.decode(encodedContent, Base64.DEFAULT))
                    }
                } catch (e: Exception) { } 
            } else if (isBase64(content.trim())) {
                content = decodeBase64(content.trim())
            }

            val items = parseContentGlobal(content)
            if (items.isNotEmpty()) onProgress("Found ${items.size} proxies")
            return items

        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun parseContentGlobal(content: String): List<ProxyItem> {
        val items = mutableListOf<ProxyItem>()
        val lines = content.split("\n", "\r")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            if (trimmed.startsWith("vless://") || trimmed.startsWith("trojan://")) {
                parseVlessTrojan(trimmed)?.let { items.add(it) }
            } else if (trimmed.startsWith("vmess://")) {
                parseVmess(trimmed)?.let { items.add(it) }
            }
        }
        return items
    }

    private fun parseVlessTrojan(uri: String): ProxyItem? {
        try {
            val type = if (uri.startsWith("vless")) ProxyType.VLESS else ProxyType.TROJAN
            
            val main = uri.substringAfter("://")
            if ("@" !in main) return null
            
            val uuid = main.substringBefore("@")
            val rest = main.substringAfter("@")
            
            var addressPart = rest
            var paramsPart = ""
            var name = ""
            
            if ("#" in rest) {
                name = rest.substringAfter("#")
                addressPart = rest.substringBefore("#")
            }
            
            if ("?" in addressPart) {
                paramsPart = addressPart.substringAfter("?")
                addressPart = addressPart.substringBefore("?")
            }
            
            val ip = addressPart.substringBeforeLast(":")
            val port = addressPart.substringAfterLast(":").toIntOrNull() ?: 443
            
            // Parse Query Params for Host/SNI/Path
            var originalHost = ip // Default ke IP
            var path = "/"
            
            val params = URLDecoder.decode(paramsPart, "UTF-8").split("&").associate {
                val (key, value) = it.split("=", limit = 2)
                key to value
            }

            if (params.containsKey("host") && params["host"]!!.isNotEmpty()) {
                originalHost = params["host"]!!
            } else if (params.containsKey("sni") && params["sni"]!!.isNotEmpty()) {
                originalHost = params["sni"]!!
            }
            if (params.containsKey("path") && params["path"]!!.isNotEmpty()) {
                path = params["path"]!!
            }
            
            // Parse Name for Country/ISP
            var country = "UN"
            var provider = "Unknown"
            
            if (name.isNotEmpty()) {
                val decodedName = URLDecoder.decode(name, "UTF-8")
                val parts = decodedName.split(" ", limit = 2)
                if (parts.isNotEmpty()) country = parts[0]
                if (parts.size > 1) provider = parts[1]
            }

            return ProxyItem(
                ip = ip,
                port = port,
                country = country,
                provider = provider,
                type = type,
                uuid = uuid,
                originalHost = originalHost,
                path = path // Set path
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseVmess(uri: String): ProxyItem? {
        try {
            val b64 = uri.replace("vmess://", "")
            val decodedJson = String(Base64.decode(b64, Base64.DEFAULT))
            val jsonObject = JSONObject(decodedJson)

            val ip = jsonObject.optString("add")
            val port = jsonObject.optInt("port")
            val id = jsonObject.optString("id")
            val net = jsonObject.optString("net")
            val host = jsonObject.optString("host")
            val path = jsonObject.optString("path")
            val tls = jsonObject.optString("tls")
            val sni = jsonObject.optString("sni")
            val aid = jsonObject.optInt("aid", 0)

            var originalHost = host
            if (originalHost.isEmpty()) originalHost = sni
            if (originalHost.isEmpty()) originalHost = ip

            var country = "UN"
            var provider = "Unknown"
            val ps = jsonObject.optString("ps")
            if (ps.isNotEmpty()) {
                val parts = ps.split(" ", limit = 2)
                if (parts.isNotEmpty()) country = parts[0]
                if (parts.size > 1) provider = parts[1]
            }

            return ProxyItem(
                ip = ip,
                port = port,
                country = country,
                provider = provider,
                type = ProxyType.VMESS,
                uuid = id,
                originalHost = originalHost,
                aid = aid, // Set aid
                path = path // Set path
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun isBase64(str: String): Boolean {
        if (str.contains(" ")) return false
        return try { Base64.decode(str, Base64.DEFAULT); true } catch (e: Exception) { false }
    }

    private fun decodeBase64(str: String): String {
        return try { String(Base64.decode(str, Base64.DEFAULT)) } catch (e: Exception) { str }
    }
}