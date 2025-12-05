package com.redbunny.nativ.data

import android.content.Context
import android.content.SharedPreferences

object ProxyRepository {
    private const val PREF_NAME = "redbunny_prefs"
    private const val KEY_SOURCES = "proxy_sources"

    // SUMBER TERPUSAT (ACTIVE PROXIES)
    // Menggunakan file yang sudah difilter (hidup) oleh server checker
    private val DEFAULT_SOURCES = setOf(
        "https://api.github.com/repos/Whymyhuman/RedBunnyNative/contents/active_proxies.txt"
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