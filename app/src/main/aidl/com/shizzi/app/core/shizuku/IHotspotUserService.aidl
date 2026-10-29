// IHotspotUserService.aidl
package com.shizzi.app.core.shizuku;

/**
 * Runs with the Shizuku privileged UID (2000 / shell on most OEMs), so these
 * calls succeed without root: enabling Wi-Fi tethering and wiring iptables
 * NAT rules so hotspot clients are routed through the same TUN interface
 * used by the VPN tunnel ("Follow VPN" mode).
 */
interface IHotspotUserService {

    /** UID this privileged process is actually running as (2000 = shell). */
    int getCallingUid();

    /** Runs an arbitrary shell command and returns combined stdout/stderr. */
    String execShell(String command);

    boolean isHotspotEnabled();

    /** Starts/stops local Wi-Fi tethering through `cmd wifi start-tethering`. */
    void setHotspotEnabled(boolean enabled, String ssid);

    /** Routes (or stops routing) hotspot clients into the active VPN tun interface. */
    void setFollowVpnRouting(boolean enabled, String tunInterfaceName);

    void destroy();
}
