package com.shizzi.app.core.ping

import com.shizzi.app.core.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/** Measures raw TCP connect latency to each saved server, in milliseconds. */
object PingManager {

    private const val DEFAULT_TIMEOUT_MS = 4000

    suspend fun pingOnce(address: String, port: Int, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(timeoutMs.toLong()) {
                runCatching {
                    val start = System.currentTimeMillis()
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(address, port), timeoutMs)
                    }
                    (System.currentTimeMillis() - start).toInt()
                }.getOrNull()
            }
        }

    /** Pings every server concurrently and returns a map of id -> latency (null = unreachable). */
    suspend fun pingAll(servers: List<ServerConfig>): Map<Long, Int?> = coroutineScope {
        servers.map { server ->
            async { server.id to pingOnce(server.address, server.port) }
        }.map { it.await() }.toMap()
    }

    /** Returns the id of the server with the lowest latency, or null if none reachable. */
    fun fastest(results: Map<Long, Int?>): Long? =
        results.entries.filter { it.value != null }.minByOrNull { it.value!! }?.key
}
