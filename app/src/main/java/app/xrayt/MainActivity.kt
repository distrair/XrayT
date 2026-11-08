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
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private lateinit var store: Store

    private val vpnPerm = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) launchService()
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpn() }

    private fun connect() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpn()
    }
    private fun requestVpn() {
        val i = VpnService.prepare(this)
        if (i != null) vpnPerm.launch(i) else launchService()
    }
    private fun launchService() {
        XrayVpnService.lastError.value = null
        startForegroundService(Intent(this, XrayVpnService::class.java))
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

@Composable
fun XrayTTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? ComponentActivity)?.window
                ?: (view.context as? android.app.Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun Root(store: Store, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var subs by remember { mutableStateOf(store.loadSubs()) }
    val save: (List<Subscription>) -> Unit = { subs = it; store.saveSubs(it) }
    val navItems = remember {
        listOf(
            NavItem("Connect", Icons.Filled.PowerSettingsNew, Icons.Outlined.PowerSettingsNew),
            NavItem("Subscriptions", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
            NavItem("Apps", Icons.Filled.Apps, Icons.Outlined.Apps)
        )
    }
    Scaffold(bottomBar = {
        NavigationBar {
            navItems.forEachIndexed { i, item ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = {
                        Icon(
                            imageVector = if (tab == i) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label
                        )
                    },
                    label = { Text(item.label) }
                )
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ConnectTab(store, subs, onConnect, onDisconnect)
                1 -> SubsTab(store, subs, save)
                else -> AppsTab(store)
            }
        }
    }
}

@Composable
fun ConnectTab(store: Store, subs: List<Subscription>, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val running by XrayVpnService.running.collectAsState()
    val err by XrayVpnService.lastError.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var geoAuto by remember { mutableStateOf(store.geoAuto) }
    var geoTs by remember { mutableLongStateOf(store.geoUpdated) }
    var geoBusy by remember { mutableStateOf(false) }
    val sub = subs.firstOrNull { it.id == store.activeSub }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (running) "Connected" else "Disconnected", style = MaterialTheme.typography.headlineMedium)
        Text(sub?.let { "${it.name.ifBlank { it.url }} / ${it.configs.getOrNull(store.activeIdx)?.let(::configName) ?: "-"}" }
            ?: "Pick a config in Subscriptions")
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { if (running) onDisconnect() else onConnect() }) { Text(if (running) "Disconnect" else "Connect") }
        Text("Changes to per-app settings apply on the next connect.", style = MaterialTheme.typography.bodySmall)

        HorizontalDivider()
        Text("Geo files (Loyalsoldier/v2ray-rules-dat)", style = MaterialTheme.typography.titleMedium)
        Text(if (geoTs == 0L) "Not downloaded yet" else "Updated: " + DateFormat.getDateTimeInstance().format(Date(geoTs)))
        Button(enabled = !geoBusy, onClick = {
            scope.launch {
                geoBusy = true
                try {
                    withContext(Dispatchers.IO) { GeoUpdater.update(ctx) }
                    geoTs = store.geoUpdated
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: "Geo update failed", Toast.LENGTH_LONG).show()
                }
                geoBusy = false
            }
        }) { Text(if (geoBusy) "Updating…" else "Update now") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(geoAuto, { geoAuto = it; store.geoAuto = it; GeoUpdater.schedule(ctx, it) })
            Spacer(Modifier.width(8.dp))
            Text("Auto-update daily")
        }
        Text("New geo files are used on the next connect.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun SubsTab(store: Store, subs: List<Subscription>, save: (List<Subscription>) -> Unit) {
    var edit by remember { mutableStateOf<Subscription?>(null) }
    var activeSub by remember { mutableStateOf(store.activeSub) }
    var activeIdx by remember { mutableIntStateOf(store.activeIdx) }
    var busy by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    Column {
        Button(onClick = { edit = Subscription() }, modifier = Modifier.padding(16.dp)) { Text("Add subscription") }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(subs, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(s.name.ifBlank { s.url }, style = MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    busy = s.id
                                    try {
                                        val c = withContext(Dispatchers.IO) { Fetcher.fetch(s) }
                                        save(subs.map { if (it.id == s.id) it.copy(configs = c, updatedAt = System.currentTimeMillis()) else it })
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, e.message ?: "Update failed", Toast.LENGTH_LONG).show()
                                    }
                                    busy = null
                                }
                            }) { Text(if (busy == s.id) "Updating…" else "Update") }
                            TextButton(onClick = { edit = s }) { Text("Edit") }
                            TextButton(onClick = { save(subs.filter { it.id != s.id }) }) { Text("Delete") }
                        }
                        s.configs.forEachIndexed { i, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    activeSub = s.id; activeIdx = i; store.activeSub = s.id; store.activeIdx = i
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = activeSub == s.id && activeIdx == i, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(configName(c))
                            }
                        }
                    }
                }
            }
        }
    }
    edit?.let { cur ->
        SubDialog(cur, onDismiss = { edit = null }) { n ->
            save(if (subs.any { it.id == n.id }) subs.map { if (it.id == n.id) n else it } else subs + n)
            edit = null
        }
    }
}

@Composable
private fun F(label: String, v: String, lines: Int = 1, on: (String) -> Unit) =
    OutlinedTextField(v, on, label = { Text(label) }, minLines = lines, modifier = Modifier.fillMaxWidth())

@Composable
fun SubDialog(s: Subscription, onDismiss: () -> Unit, onSave: (Subscription) -> Unit) {
    var name by remember { mutableStateOf(s.name) }
    var url by remember { mutableStateOf(s.url) }
    var hwid by remember { mutableStateOf(s.hwid) }
    var ua by remember { mutableStateOf(s.userAgent) }
    var hdr by remember { mutableStateOf(s.extraHeaders) }
    var rep by remember { mutableStateOf(s.replaceInbounds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subscription") },
        confirmButton = {
            TextButton(onClick = {
                onSave(s.copy(name = name.trim(), url = url.trim(), hwid = hwid.trim(), userAgent = ua.trim(),
                    extraHeaders = hdr, replaceInbounds = rep))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                F("Name", name) { name = it }
                F("URL", url) { url = it }
                F("x-hwid", hwid) { hwid = it }
                F("User-Agent", ua) { ua = it }
                F("Extra request headers (Name: value per line)", hdr, 3) { hdr = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(rep, { rep = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Replace all inbounds with core TUN")
                }
            }
        }
    )
}

data class AppInfo(val pkg: String, val label: String)

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }.filter { it.pkg != ctx.packageName }.sortedBy { it.label.lowercase() }
}

@Composable
fun AppsTab(store: Store) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(store.mode) }
    var sel by remember { mutableStateOf(store.apps) }
    var q by remember { mutableStateOf("") }
    val apps by produceState(emptyList<AppInfo>()) { value = withContext(Dispatchers.IO) { loadApps(ctx) } }

    Column(Modifier.padding(16.dp)) {
        listOf(
            AppMode.PROXY_SELECTED to "Proxy only the selected apps",
            AppMode.BYPASS_SELECTED to "Selected apps connect directly (everything else is proxied)"
        ).forEach { (m, t) ->
            Row(Modifier.fillMaxWidth().clickable { mode = m; store.mode = m }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RadioButton(mode == m, null); Spacer(Modifier.width(8.dp)); Text(t)
            }
        }
        OutlinedTextField(q, { q = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        LazyColumn {
            items(apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth().clickable {
                    sel = if (a.pkg in sel) sel - a.pkg else sel + a.pkg
                    store.apps = sel
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(a.pkg in sel, null)
                    Spacer(Modifier.width(8.dp))
                    Column { Text(a.label); Text(a.pkg, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
