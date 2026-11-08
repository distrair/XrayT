package app.xrayt

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import libXray.DialerController
import libXray.LibXray
import org.json.JSONObject
import java.io.File

class XrayVpnService : VpnService() {
    companion object {
        const val ACTION_STOP = "app.xrayt.STOP"
        val running = MutableStateFlow(false)
        val lastError = MutableStateFlow<String?>(null)
        private var envReady = false
    }

    private var pfd: ParcelFileDescriptor? = null

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
        Thread { startVpn() }.start()
        return START_NOT_STICKY
    }

    private fun startVpn() {
        try {
            val store = Store(this)
            val sub = store.loadSubs().firstOrNull { it.id == store.activeSub } ?: error("No subscription selected")
            val raw = sub.configs.getOrNull(store.activeIdx) ?: error("No config selected")

            initEnv()

            val b = Builder().setSession("Xray").setMtu(1500)
                .addAddress("10.255.0.1", 30).addRoute("0.0.0.0", 0)
                .addAddress("fd00:255::1", 126).addRoute("::", 0)
                .addDnsServer("1.1.1.1").addDnsServer("8.8.8.8")
            applyApps(b, store)
            val establishedPfd = b.establish() ?: error("VPN permission was revoked")
            pfd = establishedPfd

            LibXray.registerDialerController(object : DialerController {
                override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
            })

            val json = ConfigBuilder.build(raw, sub.replaceInbounds, establishedPfd.fd, filesDir.absolutePath)

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
            running.value = true
        } catch (e: Throwable) {
            lastError.value = e.message ?: e.toString()
            stopVpn()
        }
    }

    /**
     * Our own package is always kept OUT of the VPN, so Xray's outbound sockets (and subscription
     * updates) go directly and can't loop back into the TUN.
     */
    private fun applyApps(b: Builder, store: Store) {
        val sel = store.apps - packageName
        when (store.mode) {
            AppMode.PROXY_SELECTED -> {
                if (sel.isEmpty()) error("Select at least one app to proxy")
                sel.forEach { try { b.addAllowedApplication(it) } catch (_: PackageManager.NameNotFoundException) {} }
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
        running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = stopVpn()
    override fun onDestroy() { stopVpn(); super.onDestroy() }
}
