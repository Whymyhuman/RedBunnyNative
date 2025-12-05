package com.redbunny.nativ.data

import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import java.net.URLEncoder
import java.util.Base64 // Import the correct Base64
import org.json.JSONObject // Import JSONObject

object ConfigGenerator {

    data class ConfigOptions(
        val bugHost: String = "media-sin6-3.cdn.whatsapp.net", // Default Bug Host
        val sniHeader: String = "" // Untuk Custom SNI, jika tidak kosong akan pakai ini
    )

    fun generateVless(proxy: ProxyItem, options: ConfigOptions): String {
        val serverHost = if (proxy.originalHost.isNotEmpty()) proxy.originalHost else proxy.ip 
        val finalSni = if (options.sniHeader.isNotEmpty()) options.sniHeader else "onering:$serverHost:${options.bugHost}"
        val tag = "${proxy.country} ${proxy.provider}"
        val path = if (proxy.path.isNotEmpty()) proxy.path else "/" // Gunakan path asli jika ada, default ke /

        return "vless://${proxy.uuid}@${proxy.ip}:${proxy.port}" +
               "?type=ws&encryption=none&security=tls" +
               "&sni=${encode(finalSni)}" +
               "&host=${encode(serverHost)}" +
               "&path=${encode(path)}" + 
               "#${encode(tag)}"
    }

    fun generateTrojan(proxy: ProxyItem, options: ConfigOptions): String {
        val serverHost = if (proxy.originalHost.isNotEmpty()) proxy.originalHost else proxy.ip
        val finalSni = if (options.sniHeader.isNotEmpty()) options.sniHeader else "onering:$serverHost:${options.bugHost}"
        val tag = "${proxy.country} ${proxy.provider}"
        val path = if (proxy.path.isNotEmpty()) proxy.path else "/" // Gunakan path asli jika ada, default ke /

        return "trojan://${proxy.uuid}@${proxy.ip}:${proxy.port}" +
               "?type=ws&security=tls" +
               "&sni=${encode(finalSni)}" +
               "&host=${encode(serverHost)}" +
               "&path=${encode(path)}" +
               "#${encode(tag)}"
    }
    
    fun generateVmess(proxy: ProxyItem, options: ConfigOptions): String {
        val serverHost = if (proxy.originalHost.isNotEmpty()) proxy.originalHost else proxy.ip
        val finalSni = if (options.sniHeader.isNotEmpty()) options.sniHeader else "onering:$serverHost:${options.bugHost}"
        val tag = "${proxy.country} ${proxy.provider}"
        val path = if (proxy.path.isNotEmpty()) proxy.path else "/"

        val vmessJson = JSONObject().apply {
            put("v", "2")
            put("ps", tag)
            put("add", proxy.ip)
            put("port", proxy.port)
            put("id", proxy.uuid)
            put("aid", proxy.aid) // Alter ID
            put("net", "ws") // Asumsi WebSocket, bisa diubah jika perlu
            put("type", "none")
            put("host", serverHost)
            put("path", path)
            put("tls", "tls")
            put("sni", finalSni)
        }.toString()
        
        return "vmess://" + Base64.getEncoder().encodeToString(vmessJson.toByteArray())
    }

    private fun encode(s: String): String {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}