package com.shizzi.app.core.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single proxy outbound saved by the user: manually pasted, imported from a
 * subscription link, or decoded from a raw Xray JSON config.
 */
@Entity(tableName = "servers")
data class ServerConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "protocol")
    val protocol: String,

    @ColumnInfo(name = "address")
    val address: String,

    @ColumnInfo(name = "port")
    val port: Int,

    /** uuid (vless/vmess), password (trojan), or "method:password" (shadowsocks). */
    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "raw_link")
    val rawLink: String,

    /** network, security, sni, path, host, flow, publicKey, shortId, fingerprint, method, aid... */
    @ColumnInfo(name = "params")
    val params: Map<String, String> = emptyMap(),

    @ColumnInfo(name = "ping_ms")
    val pingMs: Int? = null,

    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean = false,

    @ColumnInfo(name = "source")
    val source: String = "manual",

    @ColumnInfo(name = "subscription_url")
    val subscriptionUrl: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun protocolEnum(): Protocol = Protocol.fromId(protocol)
}
