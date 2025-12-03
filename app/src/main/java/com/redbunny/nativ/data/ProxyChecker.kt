package com.redbunny.nativ.data

import com.redbunny.nativ.model.ProxyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

object ProxyChecker {
    suspend fun check(proxy: ProxyItem): ProxyItem = withContext(Dispatchers.IO) {
        // Clone proxy to avoid mutation issues if needed, or just modify and return
        // Since ProxyItem is a data class with vars, we can modify it. 
        // But in Compose, it's better to return a new instance to trigger recomposition if we replace the item in the list.
        // actually, mutableState list updates need careful handling.
        val newProxy = proxy.copy(isChecking = true)
        
        try {
            val timeout = 2000 // 2 seconds timeout
            val time = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(newProxy.ip, newProxy.port), timeout)
                }
            }
            newProxy.latency = time
            newProxy.isWorking = true
        } catch (e: Exception) {
            newProxy.latency = -1
            newProxy.isWorking = false
        } finally {
            newProxy.isChecking = false
        }
        newProxy
    }
}