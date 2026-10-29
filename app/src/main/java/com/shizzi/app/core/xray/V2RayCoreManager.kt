package com.shizzi.app.core.xray

import android.util.Log
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Thin Kotlin wrapper around the Xray-core Go bindings published by the
 * AndroidLibXrayLite project (https://github.com/2dust/AndroidLibXrayLite,
 * pulled through JitPack - see app/build.gradle.kts).
 *
 * The native Go core is compiled with `gomobile bind` and exposes a small
 * `CoreController` surface: start(configJson), stop(), queryStats(), and a
 * `CoreCallbackHandler` used to report VPN-protect() requests and traffic
 * stat callbacks back into Kotlin/the [android.net.VpnService].
 */
class V2RayCoreManager(private val callback: Callback) {

    interface Callback {
        /** Must call [android.net.VpnService.protect] on the underlying fd so the
         *  core's own sockets bypass the VPN tunnel (loop prevention). */
        fun onProtectSocket(fd: Int): Boolean
        fun onCoreStateChanged(running: Boolean)
        fun onStats(uplink: Long, downlink: Long)
    }

    private var controller: CoreController? = null

    val isRunning: Boolean
        get() = controller?.isRunning == true

    fun initOnce() {
        if (!initialized) {
            Seq.setContext(null)
            Libv2ray.initCoreEnv("", "")
            initialized = true
        }
    }

    fun start(configJson: String) {
        initOnce()
        val handler = object : CoreCallbackHandler {
            override fun startup(): Long = 0

            override fun shutdown(): Long {
                callback.onCoreStateChanged(false)
                return 0
            }

            override fun onEmitStatus(code: Long, message: String?): Long {
                Log.d(TAG, "xray-core status[$code]: $message")
                return 0
            }
        }

        controller = Libv2ray.newCoreController(handler).also {
            it.startLoop(configJson)
            callback.onCoreStateChanged(true)
        }
    }

    fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
        callback.onCoreStateChanged(false)
    }

    fun queryStats(tag: String = "proxy", direct: String = "downlink"): Long =
        runCatching { controller?.queryStats(tag, direct) ?: 0 }.getOrDefault(0)

    suspend fun measureDelay(configJson: String, url: String = "https://www.gstatic.com/generate_204"): Long =
        withContext(Dispatchers.IO) {
            runCatching { Libv2ray.measureOutboundDelay(configJson, url) }.getOrDefault(-1)
        }

    companion object {
        private const val TAG = "V2RayCoreManager"
        private var initialized = false
    }
}
