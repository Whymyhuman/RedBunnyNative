package com.redbunny.nativ.data

import com.redbunny.nativ.model.ProxyItem
import java.util.UUID

object ConfigGenerator {

    data class ConfigOptions(
        val frontDomain: String = "df.game.naver.com",
        val sni: String = "df.game.naver.com.redbunny.dpdns.org",
        val tlsPort: Int = 443,
        val hostHeader: String = ""
    )

    fun generateVless(proxy: ProxyItem, options: ConfigOptions): String {
        val uuid = UUID.randomUUID().toString()
        val host = if (options.hostHeader.isNotEmpty()) options.hostHeader else options.sni
        val tag = "${proxy.country} ${proxy.provider} [${proxy.ip}]"
        
        // Standard VLESS WS TLS format
        return "vless://$uuid@${options.frontDomain}:${options.tlsPort}/?type=ws&encryption=none&flow=&host=$host&path=/${proxy.ip}-${proxy.port}&security=tls&sni=${options.sni}#${encode(tag)}"
    }

    fun generateTrojan(proxy: ProxyItem, options: ConfigOptions): String {
        val password = UUID.randomUUID().toString()
        val host = if (options.hostHeader.isNotEmpty()) options.hostHeader else options.sni
        val tag = "${proxy.country} ${proxy.provider} [${proxy.ip}]"

        // Standard Trojan WS TLS format
        return "trojan://$password@${options.frontDomain}:${options.tlsPort}/?type=ws&host=$host&path=/${proxy.ip}-${proxy.port}&security=tls&sni=${options.sni}#${encode(tag)}"
    }

    private fun encode(s: String): String {
        return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    }
}
