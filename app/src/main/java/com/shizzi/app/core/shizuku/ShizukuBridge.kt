package com.shizzi.app.core.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

data class ShizukuStatus(
    val binderAlive: Boolean = false,
    val permissionGranted: Boolean = false,
    val uid: Int = -1,
    val serviceBound: Boolean = false,
)

/**
 * Wraps the Shizuku API (`dev.rikka.shizuku:api`) lifecycle: binder discovery,
 * runtime permission request, and binding the [HotspotUserService] AIDL
 * service that actually executes privileged commands.
 *
 * Mirrors the integration pattern used by github.com/carlelieser/shizzi for
 * sharing the device hotspot without root.
 */
class ShizukuBridge(private val context: Context) {

    private val _status = MutableStateFlow(ShizukuStatus())
    val status: StateFlow<ShizukuStatus> = _status

    private var userServiceConnection: ServiceConnection? = null
    var hotspotService: IHotspotUserService? = null
        private set

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        _status.value = _status.value.copy(binderAlive = true)
        refreshPermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _status.value = ShizukuStatus(binderAlive = false)
        hotspotService = null
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == PERMISSION_REQUEST_CODE) {
            _status.value = _status.value.copy(permissionGranted = grantResult == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refreshPermissionState()
    }

    fun dispose() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        unbindUserService()
    }

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        if (!isBinderAlive()) return false
        if (Shizuku.isPreV11()) return false
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (!isBinderAlive()) return
        if (Shizuku.isPreV11()) return
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Host activity should surface an explanatory dialog before calling this again.
        }
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun refreshPermissionState() {
        _status.value = _status.value.copy(
            binderAlive = isBinderAlive(),
            permissionGranted = hasPermission(),
            uid = runCatching { Shizuku.getUid() }.getOrDefault(-1),
        )
    }

    /** Binds [HotspotUserService], which runs in a separate privileged process. */
    fun bindHotspotService(onReady: (IHotspotUserService?) -> Unit) {
        if (!hasPermission()) {
            onReady(null)
            return
        }
        val args = Shizuku.UserServiceArgs(ComponentName(context, HotspotUserService::class.java))
            .daemon(false)
            .processNameSuffix("hotspot_service")
            .debuggable(false)
            .version(1)

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                hotspotService = IHotspotUserService.Stub.asInterface(binder)
                _status.value = _status.value.copy(serviceBound = true)
                onReady(hotspotService)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                hotspotService = null
                _status.value = _status.value.copy(serviceBound = false)
            }
        }
        userServiceConnection = connection
        Shizuku.bindUserService(args, connection)
    }

    fun unbindUserService() {
        userServiceConnection?.let { connection ->
            runCatching {
                val args = Shizuku.UserServiceArgs(ComponentName(context, HotspotUserService::class.java))
                Shizuku.unbindUserService(args, connection, true)
            }
        }
        userServiceConnection = null
        hotspotService = null
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 9426
    }
}
