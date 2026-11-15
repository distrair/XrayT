package app.xrayt

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var store: Store

    private val vpnPerm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) launchService()
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpn() }

    private fun ensureProxyAppSelection(): Boolean {
        if (store.mode != AppMode.PROXY_SELECTED || (store.apps - packageName).isNotEmpty()) return true
        XrayVpnService.lastError.value = null
        Toast.makeText(this, "Select at least one app to proxy", Toast.LENGTH_SHORT).show()
        return false
    }

    private fun connect() {
        if (!ensureProxyAppSelection()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpn()
    }

    private fun requestVpn() {
        if (!ensureProxyAppSelection()) return
        val i = VpnService.prepare(this)
        if (i != null) vpnPerm.launch(i) else launchService()
    }

    private fun launchService() {
        if (!ensureProxyAppSelection()) return
        XrayVpnService.lastError.value = null
        val sub = store.loadSubs().firstOrNull { it.id == store.activeSub }
        val raw = sub?.configs?.getOrNull(store.activeIdx)
        if (sub == null || raw == null) {
            val msg = "Pick a config in Subscriptions"
            XrayVpnService.lastError.value = msg
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, XrayVpnService::class.java).apply {
            putExtra(XrayVpnService.EXTRA_RAW_CONFIG, raw)
            putExtra(XrayVpnService.EXTRA_APP_MODE, store.mode.name)
            putStringArrayListExtra(XrayVpnService.EXTRA_APPS, ArrayList(store.apps))
        }
        startForegroundService(intent)
    }

    private fun disconnect() {
        startService(Intent(this, XrayVpnService::class.java).setAction(XrayVpnService.ACTION_STOP))
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == XrayVpnService.ACTION_STATE_CHANGED) {
                XrayVpnService.running.value = intent.getBooleanExtra("running", false)
                XrayVpnService.lastError.value = intent.getStringExtra("error")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            IntentFilter(XrayVpnService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        sendBroadcast(Intent(XrayVpnService.ACTION_QUERY_STATE).setPackage(packageName))
        val cm = getSystemService(ConnectivityManager::class.java)
        val hasVpn = cm?.allNetworks?.any {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: false
        if (!hasVpn) {
            XrayVpnService.running.value = false
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        GeoUpdater.schedule(this, store.geoAuto)
        // first run without geo files: fetch them right away
        if (!File(filesDir, "geoip.dat").exists() || !File(filesDir, "geosite.dat").exists()) {
            Thread { try { GeoUpdater.update(this) } catch (_: Exception) {} }.start()
        }
        setContent {
            XrayTTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Root(store, ::connect, ::disconnect)
                }
            }
        }
    }
}
