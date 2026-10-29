package com.shizzi.app.core.xray

import com.shizzi.app.core.model.Protocol
import com.shizzi.app.core.model.ServerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a complete Xray-core JSON configuration (inbounds + outbound +
 * routing) from a saved [ServerConfig]. The resulting JSON is handed to
 * [com.shizzi.app.core.xray.V2RayCoreManager.start].
 */
object XrayConfigBuilder {

    const val SOCKS_PORT = 10808
    const val HTTP_PORT = 10809

    fun build(server: ServerConfig, enableSniffing: Boolean = true): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("inbounds", buildInbounds(enableSniffing))
        root.put("outbounds", JSONArray().put(buildOutbound(server)).put(buildDirectOutbound()).put(buildBlockOutbound()))
        root.put("routing", buildRouting())
        return root.toString()
    }

    private fun buildInbounds(enableSniffing: Boolean): JSONArray {
        val sniffing = JSONObject()
            .put("enabled", enableSniffing)
            .put("destOverride", JSONArray().put("http").put("tls"))

        val socks = JSONObject()
            .put("tag", "socks-in")
            .put("protocol", "socks")
            .put("listen", "127.0.0.1")
            .put("port", SOCKS_PORT)
            .put("settings", JSONObject().put("udp", true).put("auth", "noauth"))
            .put("sniffing", sniffing)

        val http = JSONObject()
            .put("tag", "http-in")
            .put("protocol", "http")
            .put("listen", "127.0.0.1")
            .put("port", HTTP_PORT)
            .put("sniffing", sniffing)

        return JSONArray().put(socks).put(http)
    }

    private fun buildOutbound(server: ServerConfig): JSONObject {
        val params = server.params
        val streamSettings = buildStreamSettings(params)

        val outbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", server.protocol)
            .put("streamSettings", streamSettings)

        when (server.protocolEnum()) {
            Protocol.VLESS -> {
                val user = JSONObject()
                    .put("id", server.userId)
                    .put("encryption", params["encryption"] ?: "none")
                    .put("flow", params["flow"] ?: "")
                val vnext = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            }
            Protocol.VMESS -> {
                val user = JSONObject()
                    .put("id", server.userId)
                    .put("alterId", (params["aid"] ?: "0").toIntOrNull() ?: 0)
                    .put("security", params["scy"] ?: "auto")
                val vnext = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("users", JSONArray().put(user))
                outbound.put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            }
            Protocol.TROJAN -> {
                val entry = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("password", server.userId)
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(entry)))
            }
            Protocol.SHADOWSOCKS -> {
                val (method, password) = server.userId.split(":", limit = 2).let {
                    (it.getOrNull(0) ?: "aes-256-gcm") to (it.getOrNull(1) ?: "")
                }
                val entry = JSONObject()
                    .put("address", server.address)
                    .put("port", server.port)
                    .put("method", method)
                    .put("password", password)
                outbound.put("settings", JSONObject().put("servers", JSONArray().put(entry)))
            }
        }

        return outbound
    }

    private fun buildStreamSettings(params: Map<String, String>): JSONObject {
        val network = params["net"] ?: params["type"]?.takeIf { it in listOf("ws", "grpc", "tcp", "kcp") } ?: "tcp"
        val security = params["security"] ?: (if (params["tls"] == "tls") "tls" else "none")

        val stream = JSONObject().put("network", network).put("security", security)

        if (network == "ws") {
            val headers = JSONObject()
            if (!params["host"].isNullOrBlank()) headers.put("Host", params["host"])
            stream.put(
                "wsSettings",
                JSONObject().put("path", params["path"] ?: "/").put("headers", headers),
            )
        }
        if (network == "grpc") {
            stream.put("grpcSettings", JSONObject().put("serviceName", params["path"] ?: ""))
        }

        when (security) {
            "tls" -> stream.put(
                "tlsSettings",
                JSONObject()
                    .put("serverName", params["sni"] ?: params["host"] ?: "")
                    .put("allowInsecure", false)
                    .apply {
                        if (!params["fp"].isNullOrBlank()) put("fingerprint", params["fp"])
                        if (!params["alpn"].isNullOrBlank()) put("alpn", JSONArray(params["alpn"]!!.split(",")))
                    },
            )
            "reality" -> stream.put(
                "realitySettings",
                JSONObject()
                    .put("serverName", params["sni"] ?: "")
                    .put("fingerprint", params["fp"] ?: "chrome")
                    .put("publicKey", params["pbk"] ?: "")
                    .put("shortId", params["sid"] ?: "")
                    .put("spiderX", params["spx"] ?: ""),
            )
        }

        return stream
    }

    private fun buildDirectOutbound(): JSONObject =
        JSONObject().put("tag", "direct").put("protocol", "freedom").put("settings", JSONObject())

    private fun buildBlockOutbound(): JSONObject =
        JSONObject().put("tag", "block").put("protocol", "blackhole").put("settings", JSONObject())

    private fun buildRouting(): JSONObject {
        val privateRule = JSONObject()
            .put("type", "field")
            .put("ip", JSONArray().put("geoip:private"))
            .put("outboundTag", "direct")
        return JSONObject().put("domainStrategy", "IPIfNonMatch").put("rules", JSONArray().put(privateRule))
    }
}
