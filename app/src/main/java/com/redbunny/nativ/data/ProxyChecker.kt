package com.redbunny.nativ.data

import android.util.Log
import com.redbunny.nativ.model.ProxyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

object ProxyChecker {

    private val semaphore = Semaphore(50) // Bisa lebih banyak karena TCP ping ringan
    private const val GEO_API_URL = "http://ip-api.com/json" 

    suspend fun checkProxies(proxies: List<ProxyItem>): List<ProxyItem> = withContext(Dispatchers.IO) {
        val tasks = proxies.map { item ->
            async {
                semaphore.withPermit {
                    checkSingleProxy(item)
                }
            }
        }
        return@withContext tasks.awaitAll()
    }

    private fun checkSingleProxy(item: ProxyItem): ProxyItem {
        // Copy item agar thread-safe dan trigger update UI nantinya
        val newItem = item.copy(isChecking = true, latency = -1, isWorking = false)

        try {
            // 1. TCP PING (Cek Latency Dasar)
            // Ini mengukur seberapa cepat kita bisa connect ke proxy server
            val time = measureTimeMillis {
                Socket().use { socket ->
                    // Timeout 3 detik untuk connect
                    socket.connect(InetSocketAddress(newItem.ip, newItem.port), 3000)
                }
            }
            
            newItem.latency = time
            newItem.isWorking = true // Bisa connect = Working secara teknis
            
            // 2. Cek GeoIP jika Ping Sukses
            // Kita lakukan request HTTP biasa ke ip-api.com untuk mendapatkan info IP proxy
            if (newItem.isWorking) {
                fetchGeoInfo(newItem)
            }

        } catch (e: Exception) {
            newItem.latency = -1
            newItem.isWorking = false
        } finally {
            newItem.isChecking = false
        }
        
        return newItem
    }
    
    // GeoIP check (opsional, dinonaktifkan default agar cepat muncul MS)
    private fun fetchGeoInfo(item: ProxyItem) {
        try {
            // Gunakan client baru tanpa proxy untuk cek info IP target secara direct
            // URL: http://ip-api.com/json/{IP_TARGET}
            val client = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
                
            val req = Request.Builder().url("http://ip-api.com/json/${item.ip}").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            resp.close()
            
            if (body != null) {
                val json = JSONObject(body)
                if (json.optString("status") == "success") {
                    val cc = json.optString("countryCode")
                    val isp = json.optString("isp")
                    val org = json.optString("org") // Kadang org lebih deskriptif drpd isp
                    
                    if (cc.isNotEmpty()) item.country = cc
                    // Prioritaskan ISP, kalau kosong pakai Org
                    if (isp.isNotEmpty()) item.provider = isp else if (org.isNotEmpty()) item.provider = org
                }
            }
        } catch (e: Exception) {
            Log.w("ProxyChecker", "Geo check failed for ${item.ip}")
        }
    }
}