package com.redbunny.nativ.data

import android.content.Context
import android.content.SharedPreferences

object ProxyRepository {
    private const val PREF_NAME = "redbunny_prefs"
    private const val KEY_SOURCES = "proxy_sources"

    // SUMBER SUPER LENGKAP (Mix VLESS, Trojan, VMess, Socks5, HTTP)
    // Diambil dari repo-repo populer yang update otomatis setiap jam
    private val DEFAULT_SOURCES = setOf(
        // Sumber Asli RedBunny
        "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
        
        // Sumber VLESS/Trojan/VMess Campuran (Base64 & Raw)
        "https://raw.githubusercontent.com/freefq/free/master/vless.txt",
        "https://raw.githubusercontent.com/rostergamer/v2ray/master/vless",
        "https://raw.githubusercontent.com/Pawdroid/Free-servers/main/sub",
        "https://raw.githubusercontent.com/peasoft/NoMoreWalls/master/list.txt",
        "https://raw.githubusercontent.com/mahdibland/V2RayAggregator/master/Eternity.txt",
        "https://raw.githubusercontent.com/mfuu/v2ray/master/vless",
        "https://raw.githubusercontent.com/ermaozi/get_subscribe/main/subscribe/v2ray.txt",
        "https://raw.githubusercontent.com/Barimehdi/sub_v2ray/refs/heads/main/vless.txt",
        
        // Sumber IP:PORT Raw (Socks5/HTTP) - Bagus untuk scanner
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt",
        "https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/socks5.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/socks5.txt",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
        "https://raw.githubusercontent.com/prxchk/proxy-list/main/http.txt",
        "https://raw.githubusercontent.com/prxchk/proxy-list/main/socks5.txt"
    )

    fun getSources(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_SOURCES, DEFAULT_SOURCES) ?: DEFAULT_SOURCES
    }

    fun addSource(context: Context, url: String) {
        val current = getSources(context).toMutableSet()
        if (url.isNotBlank()) {
            current.add(url.trim())
            saveSources(context, current)
        }
    }

    fun removeSource(context: Context, url: String) {
        val current = getSources(context).toMutableSet()
        current.remove(url)
        saveSources(context, current)
    }

    fun resetSources(context: Context) {
        saveSources(context, DEFAULT_SOURCES)
    }

    private fun saveSources(context: Context, sources: Set<String>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SOURCES, sources).apply()
    }
}