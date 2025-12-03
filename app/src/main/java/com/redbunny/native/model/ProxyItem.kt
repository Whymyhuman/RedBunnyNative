package com.redbunny.native.model

data class ProxyItem(
    val ip: String,
    val port: Int,
    val country: String = "Unknown",
    val provider: String = "Unknown",
    val type: ProxyType = ProxyType.UNKNOWN
)

enum class ProxyType {
    VLESS, TROJAN, VMESS, UNKNOWN
}
