package com.redbunny.nativ.model

data class ProxyItem(
    val ip: String,
    val port: Int,
    var country: String = "Unknown",
    var provider: String = "Unknown",
    var type: ProxyType = ProxyType.UNKNOWN,
    var uuid: String = "",
    var originalHost: String = "",
    var aid: Int = 0, // Alter ID for VMess
    var path: String = "", // WebSocket/HTTP Path
    
    // Status Pengecekan
    var latency: Long = -1,
    var isWorking: Boolean = false,
    var isChecking: Boolean = false,
    var speedtestAccess: Boolean = false
)

enum class ProxyType {
    VLESS, TROJAN, VMESS, UNKNOWN
}