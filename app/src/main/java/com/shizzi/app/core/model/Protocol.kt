package com.shizzi.app.core.model

/** Supported outbound protocols handled by the Xray-core engine. */
enum class Protocol(val id: String) {
    VLESS("vless"),
    VMESS("vmess"),
    TROJAN("trojan"),
    SHADOWSOCKS("ss");

    companion object {
        fun fromScheme(scheme: String): Protocol? = when (scheme.lowercase()) {
            "vless" -> VLESS
            "vmess" -> VMESS
            "trojan" -> TROJAN
            "ss" -> SHADOWSOCKS
            else -> null
        }

        fun fromId(id: String): Protocol = entries.firstOrNull { it.id == id } ?: VLESS
    }
}
