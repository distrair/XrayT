package app.xrayt

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class XrayVpnService : VpnService() {
    companion object {
        const val ACTION_STOP = "app.xrayt.STOP"
        const val ACTION_STATE_CHANGED = "app.xrayt.VPN_STATE_CHANGED"
        const val ACTION_QUERY_STATE = "app.xrayt.VPN_QUERY_STATE"

        const val EXTRA_RAW_CONFIG = "app.xrayt.RAW_CONFIG"
        const val EXTRA_APP_MODE = "app.xrayt.APP_MODE"
        const val EXTRA_APPS = "app.xrayt.APPS"

        val running = MutableStateFlow(false)
        val lastError = MutableStateFlow<String?>(null)
        private var envReady = false
        private val starting = AtomicBoolean(false)
    }

    private var pfd: ParcelFileDescriptor? = null

    private val queryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_QUERY_STATE) {
                broadcastState(running.value, lastError.value)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            queryReceiver,
            IntentFilter(ACTION_QUERY_STATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun broadcastState(isRunning: Boolean, err: String? = null) {
        running.value = isRunning
        lastError.value = err
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra("running", isRunning)
            putExtra("error", err)
        }
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopVpn(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vpn", "VPN", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "vpn").setContentTitle("Xray is running")
            .setSmallIcon(android.R.drawable.ic_lock_lock).build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (!starting.compareAndSet(false, true)) return START_NOT_STICKY
        Thread { try { startVpn(intent) } finally { starting.set(false) } }.start()
        return START_NOT_STICKY
    }

    private data class VpnConfig(
        val rawConfig: String,
        val mode: AppMode,
        val apps: Set<String>
    )

    private fun resolveConfig(intent: Intent?): VpnConfig {
        val rawFromIntent = intent?.getStringExtra(EXTRA_RAW_CONFIG)
        if (!rawFromIntent.isNullOrBlank()) {
            val modeStr = intent.getStringExtra(EXTRA_APP_MODE)
            val mode = modeStr?.let { runCatching { AppMode.valueOf(it) }.getOrNull() } ?: AppMode.PROXY_SELECTED
            val appsList = intent.getStringArrayListExtra(EXTRA_APPS) ?: emptyList<String>()
            return VpnConfig(rawFromIntent, mode, appsList.toSet())
        }

        val store = Store(this)
        val sub = store.loadSubs().firstOrNull { it.id == store.activeSub } ?: error("No subscription selected")
        val raw = sub.configs.getOrNull(store.activeIdx) ?: error("No config selected")
        return VpnConfig(raw, store.mode, store.apps)
    }

    private fun startVpn(intent: Intent?) {
        try {
            val config = resolveConfig(intent)

            initEnv()

            val b = Builder().setSession("Xray").setMtu(1500)
                .addAddress("10.255.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fd00:255::1", 126).addRoute("::", 0)
                .addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
            applyApps(b, config.mode, config.apps)
            val establishedPfd = b.establish() ?: error("VPN permission was revoked")
            pfd = establishedPfd

            LibXray.registerDialerController(object : DialerController {
                override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
            })

            val json = ConfigBuilder.build(config.rawConfig, establishedPfd.fd, filesDir.absolutePath)

            val req = JSONObject().apply {
                put("apiVersion", LibXray.LibXrayAPIVersion)
                put("method", "runXray")
                put("payload", JSONObject().apply {
                    put("xrayJson", json)
                })
            }
            val resStr = LibXray.invoke(req.toString())
            val res = JSONObject(resStr)
            if (!res.optBoolean("success")) {
                val err = res.optString("error").ifBlank { "Failed to start Xray" }
                error(err)
            }
            broadcastState(true)
        } catch (e: Throwable) {
            val errMsg = e.message ?: e.toString()
            broadcastState(false, errMsg)
            stopVpn()
        }
    }

    /**
     * Our own package is always kept OUT of the VPN, so Xray's outbound sockets (and subscription
     * updates) go directly and can't loop back into the TUN.
     */
    private fun applyApps(b: Builder, mode: AppMode, apps: Set<String>) {
        val sel = apps - packageName
        when (mode) {
            AppMode.PROXY_SELECTED -> {
                if (sel.isEmpty()) error("Select at least one app to proxy")
                var added = 0
                sel.forEach {
                    try {
                        b.addAllowedApplication(it)
                        added++
                    } catch (_: PackageManager.NameNotFoundException) {}
                }
                if (added == 0) error("None of the selected apps are installed. Select an installed app to proxy.")
            }
            AppMode.BYPASS_SELECTED -> {
                (sel + packageName).forEach {
                    try { b.addDisallowedApplication(it) } catch (_: PackageManager.NameNotFoundException) {}
                }
            }
        }
    }

    private fun initEnv() {
        if (envReady) return
        for (f in listOf("geoip.dat", "geosite.dat")) {
            val out = File(filesDir, f)
            if (!out.exists()) try {
                assets.open(f).use { i -> out.outputStream().use { i.copyTo(it) } }
            } catch (_: Exception) {}
        }
        envReady = true
    }

    private fun stopVpn() {
        try {
            val req = JSONObject().apply {
                put("apiVersion", LibXray.LibXrayAPIVersion)
                put("method", "stopXray")
            }
            LibXray.invoke(req.toString())
        } catch (_: Throwable) {}
        try { pfd?.close() } catch (_: Throwable) {}
        pfd = null
        broadcastState(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stopVpn()
    override fun onDestroy() {
        try { unregisterReceiver(queryReceiver) } catch (_: Exception) {}
        stopVpn()
        super.onDestroy()
    }
}
