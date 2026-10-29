package com.shizzi.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shizzi.app.ShizziApplication
import com.shizzi.app.core.model.ServerConfig
import com.shizzi.app.core.parser.ConfigParser
import com.shizzi.app.core.parser.SubscriptionManager
import com.shizzi.app.core.ping.PingManager
import com.shizzi.app.core.shizuku.ShizukuStatus
import com.shizzi.app.data.ShizukuMode
import com.shizzi.app.data.ShizziSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val servers: List<ServerConfig> = emptyList(),
    val settings: ShizziSettings = ShizziSettings(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus(),
    val isImporting: Boolean = false,
    val isPinging: Boolean = false,
    val message: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ShizziApplication
    private val subscriptionManager = SubscriptionManager()

    private val _isImporting = MutableStateFlow(false)
    private val _isPinging = MutableStateFlow(false)
    private val _message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        app.serverRepository.observeServers(),
        app.settingsRepository.settingsFlow,
        app.shizukuBridge.status,
        _isImporting,
        _message,
    ) { servers, settings, shizukuStatus, importing, message ->
        SettingsUiState(
            servers = servers,
            settings = settings,
            shizukuStatus = shizukuStatus,
            isImporting = importing,
            message = message,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, SettingsUiState())

    fun importText(text: String) {
        viewModelScope.launch {
            _isImporting.value = true
            _message.value = null
            runCatching {
                val parsed = if (subscriptionManager.isUrl(text)) {
                    subscriptionManager.fetch(text.trim())
                } else {
                    ConfigParser.parseImportText(text)
                }
                if (parsed.isEmpty()) {
                    _message.value = "تعذر العثور على أي سيرفر صالح داخل الكود المدخل"
                } else {
                    app.serverRepository.saveAll(parsed)
                    _message.value = "تم استيراد ${parsed.size} سيرفر بنجاح"
                }
            }.onFailure {
                _message.value = "فشل الاستيراد: ${it.message}"
            }
            _isImporting.value = false
        }
    }

    fun deleteServer(server: ServerConfig) {
        viewModelScope.launch { app.serverRepository.delete(server) }
    }

    fun selectServer(server: ServerConfig) {
        viewModelScope.launch {
            app.serverRepository.select(server.id)
            app.settingsRepository.setSelectedServerId(server.id)
        }
    }

    fun pingAll() {
        viewModelScope.launch {
            _isPinging.value = true
            val servers = app.serverRepository.getServers()
            val results = PingManager.pingAll(servers)
            results.forEach { (id, latency) -> app.serverRepository.updatePing(id, latency) }
            _isPinging.value = false
        }
    }

    fun selectFastest() {
        viewModelScope.launch {
            _isPinging.value = true
            val servers = app.serverRepository.getServers()
            val results = PingManager.pingAll(servers)
            results.forEach { (id, latency) -> app.serverRepository.updatePing(id, latency) }
            val fastestId = PingManager.fastest(results)
            fastestId?.let {
                app.serverRepository.select(it)
                app.settingsRepository.setSelectedServerId(it)
                _message.value = "تم اختيار أسرع سيرفر تلقائياً"
            }
            _isPinging.value = false
        }
    }

    fun setShizukuMode(mode: ShizukuMode) {
        viewModelScope.launch { app.settingsRepository.setShizukuMode(mode) }
    }

    fun setAutoSelectFastest(enabled: Boolean) {
        viewModelScope.launch { app.settingsRepository.setAutoSelectFastest(enabled) }
    }

    fun setFontFamily(value: String) {
        viewModelScope.launch { app.settingsRepository.setFontFamily(value) }
    }

    fun checkShizukuPermission() {
        if (!app.shizukuBridge.hasPermission()) {
            app.shizukuBridge.requestPermission()
        }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
