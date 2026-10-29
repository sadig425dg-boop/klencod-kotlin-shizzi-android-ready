package com.shizzi.app.core.parser

import android.util.Base64
import com.shizzi.app.core.model.Protocol
import com.shizzi.app.core.model.ServerConfig
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * Parses vless:// vmess:// trojan:// ss:// share links (and raw Xray JSON
 * outbound configs) into [ServerConfig] rows ready to persist with Room.
 *
 * Mirrors the TypeScript reference implementation used by the Shizzi web
 * control center (`src/lib/vpn/parser.ts`) so both surfaces stay in sync.
 */
object ConfigParser {

    class ParseException(message: String) : Exception(message)

    fun detectProtocol(link: String): Protocol? {
        val trimmed = link.trim()
        return when {
            trimmed.startsWith("vless://") -> Protocol.VLESS
            trimmed.startsWith("vmess://") -> Protocol.VMESS
            trimmed.startsWith("trojan://") -> Protocol.TROJAN
            trimmed.startsWith("ss://") -> Protocol.SHADOWSOCKS
            else -> null
        }
    }

    fun parse(link: String): ServerConfig {
        val trimmed = link.trim()
        return when (detectProtocol(trimmed)) {
            Protocol.VLESS -> parseVless(trimmed)
            Protocol.VMESS -> parseVmess(trimmed)
            Protocol.TROJAN -> parseTrojan(trimmed)
            Protocol.SHADOWSOCKS -> parseShadowsocks(trimmed)
            null -> throw ParseException("رابط غير مدعوم: $trimmed")
        }
    }

    /** Parses any pasted text: a single link, multiple links (one per line),
     * a base64-encoded subscription blob, or a raw Xray JSON config. */
    fun parseImportText(text: String): List<ServerConfig> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return XrayJsonParser.parse(trimmed)
        }

        val schemes = listOf("vless://", "vmess://", "trojan://", "ss://")
        if (schemes.any { trimmed.contains(it) }) {
            return trimmed.lineSequence()
                .map { it.trim() }
                .filter { line -> schemes.any { line.startsWith(it) } }
                .ifEmpty { sequenceOf(trimmed) }
                .mapNotNull { runCatching { parse(it) }.getOrNull() }
                .toList()
        }

        // base64 subscription blob
        return runCatching {
            val decoded = decodeBase64(trimmed)
            if (schemes.any { decoded.contains(it) }) {
                decoded.lineSequence()
                    .map { it.trim() }
                    .filter { line -> schemes.any { s -> line.startsWith(s) } }
                    .mapNotNull { runCatching { parse(it) }.getOrNull() }
                    .toList()
            } else emptyList()
        }.getOrDefault(emptyList())
    }

    private fun parseVless(link: String): ServerConfig {
        val withoutScheme = link.removePrefix("vless://")
        val (beforeHash, hash) = splitHash(withoutScheme)
        val remark = hash?.let { decodeUri(it) } ?: "VLESS Server"
        val (credentials, query) = splitQuery(beforeHash)
        val atIndex = credentials.lastIndexOf('@')
        val uuid = credentials.substring(0, atIndex)
        val hostPort = credentials.substring(atIndex + 1)
        val (host, port) = splitHostPort(hostPort)
        return ServerConfig(
            name = remark,
            protocol = Protocol.VLESS.id,
            address = host,
            port = port,
            userId = uuid,
            rawLink = link,
            params = parseQuery(query),
        )
    }

    private fun parseTrojan(link: String): ServerConfig {
        val withoutScheme = link.removePrefix("trojan://")
        val (beforeHash, hash) = splitHash(withoutScheme)
        val remark = hash?.let { decodeUri(it) } ?: "Trojan Server"
        val (credentials, query) = splitQuery(beforeHash)
        val atIndex = credentials.lastIndexOf('@')
        val password = decodeUri(credentials.substring(0, atIndex))
        val hostPort = credentials.substring(atIndex + 1)
        val (host, port) = splitHostPort(hostPort)
        return ServerConfig(
            name = remark,
            protocol = Protocol.TROJAN.id,
            address = host,
            port = port,
            userId = password,
            rawLink = link,
            params = parseQuery(query),
        )
    }

    private fun parseVmess(link: String): ServerConfig {
        val b64 = link.removePrefix("vmess://")
        val decoded = decodeBase64(b64)
        val json = JSONObject(decoded)
        val address = json.optString("add")
        val port = json.optString("port", "443").toIntOrNull() ?: 443
        val id = json.optString("id")
        val remark = json.optString("ps", "VMess Server")
        val params = mapOf(
            "net" to json.optString("net", "tcp"),
            "type" to json.optString("type", "none"),
            "host" to json.optString("host", ""),
            "path" to json.optString("path", ""),
            "tls" to json.optString("tls", ""),
            "sni" to json.optString("sni", ""),
            "alpn" to json.optString("alpn", ""),
            "fp" to json.optString("fp", ""),
            "aid" to json.optString("aid", "0"),
            "scy" to json.optString("scy", "auto"),
        )
        return ServerConfig(
            name = remark,
            protocol = Protocol.VMESS.id,
            address = address,
            port = port,
            userId = id,
            rawLink = link,
            params = params,
        )
    }

    private fun parseShadowsocks(link: String): ServerConfig {
        val withoutScheme = link.removePrefix("ss://")
        val (beforeHash, hash) = splitHash(withoutScheme)
        val remark = hash?.let { decodeUri(it) } ?: "Shadowsocks Server"

        var method = "aes-256-gcm"
        var password: String
        val host: String
        val port: Int
        var params: Map<String, String> = emptyMap()

        if (beforeHash.contains("@")) {
            val userInfoRaw = beforeHash.substringBefore("@")
            val hostPortAndQuery = beforeHash.substringAfter("@")
            val (hostPort, query) = splitQuery(hostPortAndQuery)
            val userInfo = runCatching { decodeBase64(userInfoRaw) }.getOrElse { decodeUri(userInfoRaw) }
            val sep = userInfo.indexOf(':')
            method = if (sep >= 0) userInfo.substring(0, sep) else method
            password = if (sep >= 0) userInfo.substring(sep + 1) else userInfo
            val hp = splitHostPort(hostPort)
            host = hp.first
            port = hp.second
            params = parseQuery(query)
        } else {
            val decoded = decodeBase64(beforeHash)
            val atIndex = decoded.lastIndexOf('@')
            val methodPassword = decoded.substring(0, atIndex)
            val hostPort = decoded.substring(atIndex + 1)
            val sep = methodPassword.indexOf(':')
            method = if (sep >= 0) methodPassword.substring(0, sep) else method
            password = if (sep >= 0) methodPassword.substring(sep + 1) else ""
            val hp = splitHostPort(hostPort)
            host = hp.first
            port = hp.second
        }

        return ServerConfig(
            name = remark,
            protocol = Protocol.SHADOWSOCKS.id,
            address = host,
            port = port,
            userId = "$method:$password",
            rawLink = link,
            params = params + mapOf("method" to method),
        )
    }

    private fun splitHash(text: String): Pair<String, String?> {
        val index = text.indexOf('#')
        return if (index == -1) text to null else text.substring(0, index) to text.substring(index + 1)
    }

    private fun splitQuery(text: String): Pair<String, String> {
        val index = text.indexOf('?')
        return if (index == -1) text to "" else text.substring(0, index) to text.substring(index + 1)
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        if (hostPort.startsWith("[")) {
            val closeIndex = hostPort.indexOf(']')
            val address = hostPort.substring(1, closeIndex)
            val portPart = hostPort.substring(closeIndex + 2)
            return address to (portPart.toIntOrNull() ?: 443)
        }
        val lastColon = hostPort.lastIndexOf(':')
        if (lastColon == -1) return hostPort to 443
        return hostPort.substring(0, lastColon) to (hostPort.substring(lastColon + 1).toIntOrNull() ?: 443)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split("&").mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index == -1) null else decodeUri(pair.substring(0, index)) to decodeUri(pair.substring(index + 1))
        }.toMap()
    }

    private fun decodeUri(value: String): String = runCatching {
        URLDecoder.decode(value, "UTF-8")
    }.getOrDefault(value)

    private fun decodeBase64(value: String): String {
        val normalized = value.trim().replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = Base64.decode(padded, Base64.DEFAULT)
        return String(bytes, Charsets.UTF_8)
    }

    /** Validates a URI quickly before attempting a full parse (used by the UI). */
    fun isValidShareLink(link: String): Boolean = runCatching {
        detectProtocol(link) != null && URI(link.replace(" ", "%20")).scheme != null
    }.getOrDefault(false)
}
