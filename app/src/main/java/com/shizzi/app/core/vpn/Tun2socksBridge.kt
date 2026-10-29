package com.shizzi.app.core.vpn

/**
 * JNI bridge to the native `hev-socks5-tunnel` engine
 * (https://github.com/heiher/hev-socks5-tunnel), which relays raw IP packets
 * read from the Android TUN file descriptor into the local SOCKS5 inbound
 * exposed by Xray-core ([com.shizzi.app.core.xray.XrayConfigBuilder.SOCKS_PORT]).
 *
 * The native shared library (`libhev-socks5-tunnel.so`) is a prebuilt binary
 * artifact and must be placed under `app/src/main/jniLibs/<abi>/` for each
 * supported ABI (arm64-v8a, armeabi-v7a, x86_64) before building the release
 * APK - see README.md "التبعيات الأصلية (Native)".
 */
object Tun2socksBridge {

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (!loaded) {
            System.loadLibrary("hev-socks5-tunnel")
            loaded = true
        }
    }

    /**
     * Starts the tunnel loop on a dedicated native thread.
     *
     * @param tunFd file descriptor returned by `VpnService.Builder.establish()`
     * @param socksHost local Xray SOCKS5 inbound host (127.0.0.1)
     * @param socksPort local Xray SOCKS5 inbound port (10808)
     * @param mtu interface MTU, matches the value passed to `Builder.setMtu`
     */
    fun start(tunFd: Int, socksHost: String, socksPort: Int, mtu: Int = 1500) {
        ensureLoaded()
        val yaml = buildConfigYaml(tunFd, socksHost, socksPort, mtu)
        nativeStart(yaml, tunFd)
    }

    fun stop() {
        if (loaded) {
            runCatching { nativeStop() }
        }
    }

    private fun buildConfigYaml(tunFd: Int, socksHost: String, socksPort: Int, mtu: Int): String = """
        tunnel:
          mtu: $mtu
        socks5:
          port: $socksPort
          address: '$socksHost'
          udp: 'udp'
        misc:
          task-stack-size: 20480
          log-level: warn
    """.trimIndent()

    private external fun nativeStart(configYaml: String, tunFd: Int)
    private external fun nativeStop()
}
