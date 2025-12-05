package com.redbunny.nativ.model

data class ProxyItem(
    val ip: String,
    val port: Int,
    var country: String = "Unknown",
    var provider: String = "Unknown",
    var type: ProxyType = ProxyType.UNKNOWN,
    var uuid: String = "", // Untuk VLESS/Trojan/VMess
    var originalHost: String = "", // Host asli dari config, bukan IP
    
    // Status Pengecekan
    var latency: Long = -1, // -1 berarti belum dicek atau timeout
    var isWorking: Boolean = false, // Apakah bisa connect
    var isChecking: Boolean = false, // Sedang proses cek?
    var speedtestAccess: Boolean = false // Spesifik bisa buka speedtest.net
)

enum class ProxyType {
    VLESS, TROJAN, VMESS, UNKNOWN
}
