package com.shizzi.app.core.parser

import com.shizzi.app.core.model.Protocol
import com.shizzi.app.core.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

/** Extracts [ServerConfig] entries from a full Xray/V2Ray JSON client config
 *  (an `outbounds` array, or a single outbound object). */
object XrayJsonParser {

    fun parse(jsonText: String): List<ServerConfig> {
        val root = JSONObject(wrapIfArray(jsonText))
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray().apply { put(root) }
        val result = mutableListOf<ServerConfig>()

        for (i in 0 until outbounds.length()) {
            val outbound = outbounds.getJSONObject(i)
            val protocol = outbound.optString("protocol")
            val settings = outbound.optJSONObject("settings") ?: JSONObject()
            val stream = outbound.optJSONObject("streamSettings") ?: JSONObject()
            val tag = outbound.optString("tag").ifBlank { null }

            val streamParams = mutableMapOf(
                "net" to stream.optString("network", "tcp"),
                "security" to stream.optString("security", "none"),
            )
            stream.optJSONObject("wsSettings")?.let { ws ->
                streamParams["path"] = ws.optString("path", "/")
                streamParams["host"] = ws.optJSONObject("headers")?.optString("Host").orEmpty()
            }
            stream.optJSONObject("tlsSettings")?.let { tls ->
                streamParams["sni"] = tls.optString("serverName", "")
            }
            stream.optJSONObject("realitySettings")?.let { reality ->
                streamParams["sni"] = reality.optString("serverName", "")
                streamParams["pbk"] = reality.optString("publicKey", "")
                streamParams["sid"] = reality.optString("shortId", "")
                streamParams["fp"] = reality.optString("fingerprint", "chrome")
            }

            when (protocol) {
                "vless", "vmess" -> {
                    val vnext = settings.optJSONArray("vnext") ?: JSONArray()
                    for (v in 0 until vnext.length()) {
                        val entry = vnext.getJSONObject(v)
                        val users = entry.optJSONArray("users") ?: JSONArray()
                        for (u in 0 until users.length()) {
                            val user = users.getJSONObject(u)
                            result.add(
                                ServerConfig(
                                    name = tag ?: "${protocol.uppercase()}-${entry.optString("address")}",
                                    protocol = Protocol.fromScheme(protocol)?.id ?: protocol,
                                    address = entry.optString("address"),
                                    port = entry.optInt("port", 443),
                                    userId = user.optString("id"),
                                    rawLink = outbound.toString(),
                                    params = streamParams + mapOf(
                                        "flow" to user.optString("flow", ""),
                                        "encryption" to user.optString("encryption", "none"),
                                        "aid" to user.optInt("alterId", 0).toString(),
                                    ),
                                    source = "json",
                                ),
                            )
                        }
                    }
                }
                "trojan" -> {
                    val servers = settings.optJSONArray("servers") ?: JSONArray()
                    for (s in 0 until servers.length()) {
                        val entry = servers.getJSONObject(s)
                        result.add(
                            ServerConfig(
                                name = tag ?: "Trojan-${entry.optString("address")}",
                                protocol = Protocol.TROJAN.id,
                                address = entry.optString("address"),
                                port = entry.optInt("port", 443),
                                userId = entry.optString("password"),
                                rawLink = outbound.toString(),
                                params = streamParams,
                                source = "json",
                            ),
                        )
                    }
                }
                "shadowsocks" -> {
                    val servers = settings.optJSONArray("servers") ?: JSONArray()
                    for (s in 0 until servers.length()) {
                        val entry = servers.getJSONObject(s)
                        val method = entry.optString("method", "aes-256-gcm")
                        result.add(
                            ServerConfig(
                                name = tag ?: "SS-${entry.optString("address")}",
                                protocol = Protocol.SHADOWSOCKS.id,
                                address = entry.optString("address"),
                                port = entry.optInt("port", 8388),
                                userId = "$method:${entry.optString("password")}",
                                rawLink = outbound.toString(),
                                params = mapOf("method" to method),
                                source = "json",
                            ),
                        )
                    }
                }
            }
        }
        return result
    }

    private fun wrapIfArray(jsonText: String): String {
        val trimmed = jsonText.trim()
        return if (trimmed.startsWith("[")) "{\"outbounds\":$trimmed}" else trimmed
    }
}
