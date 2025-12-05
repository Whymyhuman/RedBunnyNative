package com.redbunny.nativ.data

import com.redbunny.nativ.model.ProxyItem
import com.redbunny.nativ.model.ProxyType
import java.net.URLEncoder

object ConfigGenerator {

    data class ConfigOptions(
        val frontDomain: String = "media-sin6-3.cdn.whatsapp.net", // Default Bug
        val sni: String = "" // Tidak dipakai langsung, kita generate format Onering
    )

    fun generateVless(proxy: ProxyItem, options: ConfigOptions): String {
        // Format Onering SNI: onering:SERVER_HOST:BUG_HOST
        // Host asli proxy (IP atau Domain)
        val serverHost = proxy.ip 
        val oneringSni = "onering:$serverHost:${options.frontDomain}"
        
        val tag = "${proxy.country} ${proxy.provider}"
        
        // Standard VLESS WS TLS format dengan SNI Onering
        return "vless://${proxy.uuid}@${proxy.ip}:${proxy.port}?type=ws&encryption=none&security=tls&sni=$oneringSni&host=$serverHost&path=%2F#${encode(tag)}"
    }

    fun generateTrojan(proxy: ProxyItem, options: ConfigOptions): String {
        val serverHost = proxy.ip
        val oneringSni = "onering:$serverHost:${options.frontDomain}"
        val tag = "${proxy.country} ${proxy.provider}"

        return "trojan://${proxy.uuid}@${proxy.ip}:${proxy.port}?type=ws&security=tls&sni=$oneringSni&host=$serverHost&path=%2F#${encode(tag)}"
    }
    
    fun generateVmess(proxy: ProxyItem, options: ConfigOptions): String {
        // VMess butuh JSON construction
        // Maaf, untuk VMess agak kompleks di Kotlin tanpa library JSON full, 
        // tapi kita bisa construct string JSON manual atau skip dulu.
        // Fokus VLESS/Trojan dulu sesuai request utama.
        return ""
    }

    private fun encode(s: String): String {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}