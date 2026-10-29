package com.shizzi.app.viewmodel

import android.app.Application
import android.content.Intent
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shizzi.app.ShizziApplication
import com.shizzi.app.core.model.ServerConfig
import com.shizzi.app.core.ping.PingManager
import com.shizzi.app.core.shizuku.HotspotManager
import com.shizzi.app.core.vpn.ShizziVpnService
import com.shizzi.app.data.ShizukuMode
import com.shizzi.app.data.ShizziSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val vpnConnected: Boolean = false,
    val hotspotShared: Boolean = false,
    val selectedServer: ServerConfig? = null,
    val banner: String? = null,
    val isBusy: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ShizziApplication
    private val hotspotManager = HotspotManager(application, app.shizukuBridge)

    private val _banner = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        app.settingsRepository.settingsFlow,
        app.serverRepository.observeServers(),
        _banner,
        _isBusy,
    ) { settings: ShizziSettings, servers, banner, busy ->
        HomeUiState(
            vpnConnected = settings.vpnConnected,
            hotspotShared = settings.hotspotShared,
            selectedServer = servers.firstOrNull { it.isSelected }
                ?: servers.firstOrNull { it.id == settings.selectedServerId },
            banner = banner,
            isBusy = busy,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, HomeUiState())

    /** Call once from `MainActivity` with the result of `VpnService.prepare()`. */
    fun onVpnPrepared(serverId: Long) {
        ShizziVpnService.start(getApplication(), serverId)
    }

    fun requestVpnPermission(activity: androidx.activity.ComponentActivity, launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        val prepareIntent = VpnService.prepare(activity)
        if (prepareIntent != null) {
            launcher.launch(prepareIntent)
        } else {
            toggleVpn(true)
        }
    }

    fun toggleVpn(enable: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            if (enable) {
                var selected = app.serverRepository.getSelected()
                if (selected == null) {
                    val servers = app.serverRepository.getServers()
                    if (servers.isEmpty()) {
                        _banner.value = "لا يوجد أي سيرفر محفوظ - الرجاء استيراد كود اتصال أولاً"
                        _isBusy.value = false
                        return@launch
                    }
                    val pings = PingManager.pingAll(servers)
                    val fastestId = PingManager.fastest(pings)
                    pings.forEach { (id, latency) -> app.serverRepository.updatePing(id, latency) }
                    val chosenId = fastestId ?: servers.first().id
                    app.serverRepository.select(chosenId)
                    selected = app.serverRepository.getServers().firstOrNull { it.id == chosenId }
                }
                selected?.let {
                    app.settingsRepository.setSelectedServerId(it.id)
                    ShizziVpnService.start(getApplication(), it.id)
                    app.settingsRepository.setVpnConnected(true)
                    _banner.value = "تم الاتصال عبر ${it.name}"
                }
            } else {
                ShizziVpnService.stop(getApplication())
                app.settingsRepository.setVpnConnected(false)
                app.settingsRepository.setHotspotShared(false)
                _banner.value = "تم إيقاف VPN"
            }
            _isBusy.value = false
        }
    }

    fun toggleHotspot(enable: Boolean) {
        viewModelScope.launch {
            _isBusy.value = true
            if (enable) {
                hotspotManager.startHotspot(ssid = "Shizzi-Hotspot", mode = ShizukuMode.FOLLOW_VPN) { success ->
                    viewModelScope.launch {
                        app.settingsRepository.setHotspotShared(success)
                        _banner.value = if (success) {
                            "تم تفعيل مشاركة نقطة الاتصال عبر Shizzi"
                        } else {
                            "تعذّر تفعيل المشاركة - تأكد من صلاحية Shizuku"
                        }
                        _isBusy.value = false
                    }
                }
            } else {
                hotspotManager.stopHotspot { _ ->
                    viewModelScope.launch {
                        app.settingsRepository.setHotspotShared(false)
                        _banner.value = "تم إيقاف مشاركة الشبكة"
                        _isBusy.value = false
                    }
                }
            }
        }
    }

    fun dismissBanner() {
        _banner.value = null
    }
}
