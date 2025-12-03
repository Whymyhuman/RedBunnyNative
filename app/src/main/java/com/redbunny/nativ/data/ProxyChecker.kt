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
import java.util.concurrent.TimeUnit

object ProxyChecker {

    // Batasi 40 pengecekan bersamaan agar tidak OOM atau timeout massal
    private val semaphore = Semaphore(40) 

    // Target validasi koneksi
    private const val TARGET_URL = "https://www.speedtest.net"
    
    // API GeoIP
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
        // Create a copy to avoid race conditions on UI state if accessed directly
        val newItem = item.copy(isChecking = true)

        val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(newItem.ip, newItem.port))
        
        val client = OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url(TARGET_URL)
                .header("User-Agent", "Mozilla/5.0 (RedBunnyNative)")
                .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            
            response.close()

            if (response.isSuccessful || response.code in 200..399) {
                newItem.isWorking = true
                newItem.speedtestAccess = true
                newItem.latency = latency
                
                if (newItem.country == "Unknown") {
                    fetchGeoInfo(client, newItem)
                }
            } else {
                newItem.isWorking = false
            }

        } catch (e: Exception) {
            newItem.isWorking = false
            newItem.latency = -1
        } finally {
            newItem.isChecking = false
        }
        
        return newItem
    }

    private fun fetchGeoInfo(client: OkHttpClient, item: ProxyItem) {
        try {
            // Note: Reuse client with proxy to check what the outside world sees the IP as
            val req = Request.Builder().url(GEO_API_URL).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            resp.close()
            
            if (body != null) {
                val json = JSONObject(body)
                if (json.optString("status") == "success") {
                    val cc = json.optString("countryCode")
                    val isp = json.optString("isp")
                    
                    if (cc.isNotEmpty()) item.country = cc
                    if (isp.isNotEmpty()) item.provider = isp
                }
            }
        } catch (e: Exception) {
            Log.w("ProxyChecker", "Geo check failed for ${item.ip}")
        }
    }
}
