package com.redbunny.nativ.data

import android.content.Context
import android.content.SharedPreferences

object ProxyRepository {
    private const val PREF_NAME = "redbunny_prefs"
    private const val KEY_SOURCES = "proxy_sources"

    // Sumber bawaan (Default)
    private val DEFAULT_SOURCES = setOf(
        "https://raw.githubusercontent.com/mrzero0nol/My-v2ray/main/proxyList.txt",
        "https://raw.githubusercontent.com/proxifly/free-proxy-list/main/proxies/protocols/socks5/data.txt",
        "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/http.txt"
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
