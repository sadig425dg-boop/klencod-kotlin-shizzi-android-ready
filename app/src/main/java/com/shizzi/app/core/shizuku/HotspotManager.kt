package com.shizzi.app.core.shizuku

import android.content.Context
import android.net.NetworkInterface
import android.util.Log
import com.shizzi.app.data.ShizukuMode

/**
 * High level hotspot-sharing API used by the UI and by [com.shizzi.app.core.vpn.ShizziVpnService].
 * Delegates the privileged work to [HotspotUserService] through [ShizukuBridge].
 */
class HotspotManager(
    private val context: Context,
    private val bridge: ShizukuBridge,
) {

    fun startHotspot(ssid: String, mode: ShizukuMode, onResult: (Boolean) -> Unit) {
        bridge.bindHotspotService { service ->
            if (service == null) {
                Log.w(TAG, "Shizuku service unavailable - cannot start hotspot sharing")
                onResult(false)
                return@bindHotspotService
            }
            runCatching {
                service.setHotspotEnabled(true, ssid)
                if (mode == ShizukuMode.FOLLOW_VPN) {
                    findTunInterfaceName()?.let { tun ->
                        service.setFollowVpnRouting(true, tun)
                    }
                }
            }
            onResult(true)
        }
    }

    fun stopHotspot(onResult: (Boolean) -> Unit) {
        val service = bridge.hotspotService
        if (service == null) {
            onResult(false)
            return
        }
        runCatching {
            findTunInterfaceName()?.let { tun -> service.setFollowVpnRouting(false, tun) }
            service.setHotspotEnabled(false, "")
        }
        onResult(true)
    }

    /** Called by the VPN service whenever the tunnel connects/disconnects so that, in
     *  "Follow VPN" mode, hotspot clients are (re)routed automatically. */
    fun onVpnStateChanged(connected: Boolean) {
        val service = bridge.hotspotService ?: return
        val tun = findTunInterfaceName() ?: return
        runCatching { service.setFollowVpnRouting(connected, tun) }
    }

    private fun findTunInterfaceName(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .firstOrNull { it.name.startsWith("tun") }
            ?.name
    }.getOrNull()

    companion object {
        private const val TAG = "HotspotManager"
    }
}
