package com.shizzi.app.core.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Implementation bound through `Shizuku.bindUserService`. This class executes
 * in a *separate* privileged process owned by the Shizuku service (UID 2000
 * "shell" on stock Android / UID 0 if the user granted Shizuku via root), NOT
 * inside Shizzi's own sandboxed app process - that privilege separation is
 * exactly what lets Shizzi toggle tethering / iptables without root.
 */
class HotspotUserService : IHotspotUserService.Stub() {

    override fun getCallingUid(): Int = android.os.Process.myUid()

    override fun execShell(command: String): String {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            process.waitFor()
            output
        }.getOrElse { "ERROR: ${it.message}" }
    }

    override fun isHotspotEnabled(): Boolean {
        val output = execShell("dumpsys wifi | grep -i 'softap state' | head -n1")
        return output.contains("ENABLED", ignoreCase = true)
    }

    override fun setHotspotEnabled(enabled: Boolean, ssid: String) {
        if (enabled) {
            execShell("cmd wifi start-tethering")
        } else {
            execShell("cmd wifi stop-tethering")
        }
    }

    override fun setFollowVpnRouting(enabled: Boolean, tunInterfaceName: String) {
        // Bridges the hotspot AP interface (ap0 / wlan0 depending on OEM) into the
        // same tun interface the VpnService owns, so tethered clients share the
        // exact same Xray/V2Ray route as the host device ("Follow VPN").
        val apInterface = detectApInterface()
        if (enabled) {
            execShell("iptables -t nat -A POSTROUTING -o $tunInterfaceName -j MASQUERADE")
            execShell("iptables -A FORWARD -i $apInterface -o $tunInterfaceName -j ACCEPT")
            execShell("iptables -A FORWARD -i $tunInterfaceName -o $apInterface -m state --state RELATED,ESTABLISHED -j ACCEPT")
        } else {
            execShell("iptables -t nat -D POSTROUTING -o $tunInterfaceName -j MASQUERADE")
            execShell("iptables -D FORWARD -i $apInterface -o $tunInterfaceName -j ACCEPT")
            execShell("iptables -D FORWARD -i $tunInterfaceName -o $apInterface -m state --state RELATED,ESTABLISHED -j ACCEPT")
        }
    }

    private fun detectApInterface(): String {
        val candidates = listOf("ap0", "wlan1", "swlan0", "wlan0")
        for (candidate in candidates) {
            val result = execShell("ip link show $candidate")
            if (!result.contains("does not exist") && !result.contains("ERROR")) {
                return candidate
            }
        }
        return "ap0"
    }

    override fun destroy() {
        // No-op: Shizuku tears down the hosting process once unbound.
    }
}
