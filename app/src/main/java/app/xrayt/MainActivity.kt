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
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
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
        else -> expressiveLightColorScheme()
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

    MaterialExpressiveTheme(
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var subs by remember { mutableStateOf(store.loadSubs()) }
    val save: (List<Subscription>) -> Unit = { subs = it; store.saveSubs(it) }
    val navItems = remember {
        listOf(
            NavItem("Connect", Icons.Filled.PowerSettingsNew, Icons.Outlined.PowerSettingsNew),
            NavItem("Subscriptions", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
            NavItem("Apps", Icons.Filled.Apps, Icons.Outlined.Apps)
        )
    }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEachIndexed { i, destination ->
                item(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = {
                        Icon(
                            imageVector = if (tab == i) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label
                        )
                    },
                    label = { Text(destination.label) }
                )
            }
        }
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            AppPageContent(tab, store, subs, save, onConnect, onDisconnect)
        }
    }
}

@Composable
private fun AppPageContent(
    tab: Int,
    store: Store,
    subs: List<Subscription>,
    save: (List<Subscription>) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val scope = rememberCoroutineScope()
    when (tab) {
        0 -> ConnectTab(store, subs, onConnect, onDisconnect, scope)
        1 -> SubsTab(store, subs, save)
        else -> AppsTab(store)
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ConnectTab(store: Store, subs: List<Subscription>, onConnect: () -> Unit, onDisconnect: () -> Unit, scope: CoroutineScope) {
    val running by XrayVpnService.running.collectAsState()
    val err by XrayVpnService.lastError.collectAsState()
    val ctx = LocalContext.current
    var geoAuto by remember { mutableStateOf(store.geoAuto) }
    var geoTs by remember { mutableLongStateOf(store.geoUpdated) }
    var geoBusy by remember { mutableStateOf(false) }
    val sub = subs.firstOrNull { it.id == store.activeSub }
    val config = sub?.configs?.getOrNull(store.activeIdx)
    val profileName = config?.let(::configName) ?: "No profile selected"
    val buttonContainer by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "connectionButtonContainer"
    )
    val buttonContent by animateColorAsState(
        targetValue = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "connectionButtonContent"
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxHeight().fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(profileName, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                sub?.name?.ifBlank { sub.url } ?: "Choose a profile from Subscriptions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        err?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.offset(y = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Button(
                    onClick = { if (running) onDisconnect() else onConnect() },
                    modifier = Modifier.size(184.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainer,
                        contentColor = buttonContent
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(40.dp))
                        Text(if (running) "Disconnect" else "Connect", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (running) "Connected" else "Ready when you are", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Per-app routing settings apply next time you connect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Geo files", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (geoTs == 0L) "Not downloaded yet" else "Updated ${DateFormat.getDateTimeInstance().format(Date(geoTs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(enabled = !geoBusy, onClick = {
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
                    }) {
                        if (geoBusy) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Text("Update")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-update daily", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = geoAuto, onCheckedChange = {
                        geoAuto = it
                        store.geoAuto = it
                        GeoUpdater.schedule(ctx, it)
                    })
                }
            }
        }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun SubsTab(store: Store, subs: List<Subscription>, save: (List<Subscription>) -> Unit) {
    var edit by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    var importUriDialog by rememberSaveable { mutableStateOf(false) }
    var importUriText by rememberSaveable { mutableStateOf("") }
    var addMenuExpanded by remember { mutableStateOf(false) }
    var activeSub by remember { mutableStateOf(store.activeSub) }
    var activeIdx by remember { mutableIntStateOf(store.activeIdx) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pingingSub by remember { mutableStateOf<String?>(null) }
    var pingResults by remember { mutableStateOf<Map<String, List<ProfilePing>>>(emptyMap()) }
    val vpnRunning by XrayVpnService.running.collectAsState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val (fileName, body) = withContext(Dispatchers.IO) {
                        val name = ctx.contentResolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: uri.lastPathSegment.orEmpty()
                        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                            ?.use { it.readText() } ?: error("Could not read the selected file")
                        name to text
                    }
                    val configs = withContext(Dispatchers.IO) { Fetcher.parse(body) }
                    check(configs.isNotEmpty()) { "No supported profiles found" }
                    val name = fileName.substringBeforeLast('.', fileName).ifBlank { "Imported JSON" }
                    save(subs + Subscription(
                        name = name,
                        configs = configs,
                        updatedAt = System.currentTimeMillis()
                    ))
                    Toast.makeText(ctx, "Imported ${configs.size} profile(s)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: "Could not import JSON file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Box(Modifier.widthIn(max = 840.dp).fillMaxHeight().fillMaxWidth()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your profiles", style = MaterialTheme.typography.headlineSmall)
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            items(subs, key = { it.id }) { s ->
                var menuExpanded by remember { mutableStateOf(false) }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name.ifBlank { s.url.ifBlank { "Imported profiles" } },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (s.url.isNotBlank()) TextButton(
                              enabled = busy == null && pingingSub == null,
                              onClick = {
                                scope.launch {
                                    busy = s.id
                                    try {
                                        val c = withContext(Dispatchers.IO) { Fetcher.fetch(s) }
                                        save(subs.map { if (it.id == s.id) it.copy(configs = c, updatedAt = System.currentTimeMillis()) else it })
                                        pingResults = pingResults - s.id
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, e.message ?: "Update failed", Toast.LENGTH_LONG).show()
                                    }
                                    busy = null
                                }
                            }) {
                                if (busy == s.id) {
                                    LoadingIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Update")
                                }
                            }
                            TextButton(
                              enabled = busy == null && pingingSub == null && s.configs.isNotEmpty(),
                              onClick = {
                                if (vpnRunning) {
                                  Toast.makeText(ctx, "Disconnect to ping profiles", Toast.LENGTH_SHORT).show()
                                  return@TextButton
                                }
                                scope.launch {
                                    pingingSub = s.id
                                    try {
                                        val results = withContext(Dispatchers.IO) { XrayPinger.ping(s.configs) }
                                        pingResults = pingResults + (s.id to results)
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, e.message ?: "Ping failed", Toast.LENGTH_LONG).show()
                                    } finally {
                                        pingingSub = null
                                    }
                                }
                              }
                            ) {
                                if (pingingSub == s.id) {
                                    LoadingIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Ping")
                                }
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Subscription actions")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        onClick = { menuExpanded = false; edit = s }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = { menuExpanded = false; pendingDelete = s }
                                    )
                                }
                            }
                        }
                        if (s.url.isNotBlank() && s.configs.isEmpty()) {
                            Text("No profiles yet. Update this subscription to fetch them.",
                                Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                            s.configs.forEachIndexed { i, c ->
                                val selected = activeSub == s.id && activeIdx == i
                                SegmentedListItem(
                                    selected = selected,
                                    onClick = {
                                        activeSub = s.id
                                        activeIdx = i
                                        store.activeSub = s.id
                                        store.activeIdx = i
                                    },
                                    shapes = ListItemDefaults.segmentedShapes(index = i, count = s.configs.size),
                                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                                    content = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                configName(c),
                                                Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            pingResults[s.id]?.getOrNull(i)?.let { ping ->
                                                val label = ping.delayMs?.let { "$it ms" }
                                                    ?: if (ping.timedOut) "Timeout" else "Failed"
                                                Text(
                                                    label,
                                                    Modifier.padding(start = 12.dp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (ping.delayMs != null) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            FloatingActionButton(
                onClick = { addMenuExpanded = true },
                shape = MaterialTheme.shapes.large
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add subscription or import profiles")
            }
            DropdownMenu(
                expanded = addMenuExpanded,
                onDismissRequest = { addMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Add subscription") },
                    onClick = { addMenuExpanded = false; edit = Subscription() }
                )
                DropdownMenuItem(
                    text = { Text("Import Xray URI") },
                    onClick = {
                        addMenuExpanded = false
                        importUriText = ""
                        importUriDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import JSON file") },
                    onClick = { addMenuExpanded = false; jsonPicker.launch(arrayOf("*/*")) }
                )
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
    if (importUriDialog) {
        AlertDialog(
            onDismissRequest = { importUriDialog = false },
            title = { Text("Import Xray URI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importUriText,
                        onValueChange = { importUriText = it },
                        label = { Text("URI") },
                        placeholder = { Text("vless://…") },
                        supportingText = { Text("Enter one Xray-compatible URI") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importUriText.isNotBlank(),
                    onClick = {
                        val value = importUriText.trim()
                        scope.launch {
                            try {
                                check(value.contains("://") && !value.contains(Regex("\\s"))) {
                                    "Enter one Xray-compatible URI"
                                }
                                val config = withContext(Dispatchers.IO) {
                                    Fetcher.parse(value).singleOrNull()
                                        ?: error("URI must contain exactly one supported profile")
                                }
                                val name = configName(config).takeUnless { it == "Config" } ?: "Imported URI"
                                save(subs + Subscription(
                                    name = name,
                                    configs = listOf(config),
                                    updatedAt = System.currentTimeMillis()
                                ))
                                importUriDialog = false
                                Toast.makeText(ctx, "URI imported", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, e.message ?: "Could not import URI", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { importUriDialog = false }) { Text("Cancel") }
            }
        )
    }
    pendingDelete?.let { sub ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete subscription?") },
            text = { Text("${sub.name.ifBlank { sub.url.ifBlank { "Imported profiles" } }} and its profiles will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    save(subs.filter { it.id != sub.id })
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
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
    var advancedExpanded by remember { mutableStateOf(hwid.isNotBlank() || ua.isNotBlank() || hdr.isNotBlank()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subscription") },
        confirmButton = {
            TextButton(onClick = {
                onSave(s.copy(name = name.trim(), url = url.trim(), hwid = hwid.trim(), userAgent = ua.trim(),
                    extraHeaders = hdr))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                F("Name", name) { name = it }
                F("URL", url) { url = it }
                TextButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    Text(if (advancedExpanded) "Hide advanced settings" else "Advanced settings")
                }
                if (advancedExpanded) {
                    F("x-hwid", hwid) { hwid = it }
                    OutlinedTextField(
                        value = ua,
                        onValueChange = { ua = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("User-Agent") },
                        placeholder = { Text(Fetcher.defaultUserAgent) },
                        singleLine = true
                    )
                    F("Extra request headers (Name: value per line)", hdr, 3) { hdr = it }
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

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.widthIn(max = 840.dp).fillMaxHeight().fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("App routing", style = MaterialTheme.typography.headlineSmall)
            Text("Choose which apps use your Xray connection", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val modeOptions = listOf(
            AppMode.PROXY_SELECTED to "Proxy",
            AppMode.BYPASS_SELECTED to "Bypass"
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modeOptions.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { mode = option; store.mode = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size)
                ) {
                    Text(label)
                }
            }
        }
        Text(
            when (mode) {
                AppMode.PROXY_SELECTED -> "Only selected apps use Xray."
                AppMode.BYPASS_SELECTED -> "Selected apps bypass Xray; other apps use it."
            },
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(q, { q = it }, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, singleLine = true)
        val visibleApps = apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            itemsIndexed(visibleApps, key = { _, app -> app.pkg }) { index, a ->
                val checked = a.pkg in sel
                SegmentedListItem(
                    checked = checked,
                    onCheckedChange = {
                        sel = if (it) sel + a.pkg else sel - a.pkg
                        store.apps = sel
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = visibleApps.size),
                    leadingContent = { Checkbox(checked, null) },
                    supportingContent = { Text(a.pkg, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    content = { Text(a.label) }
                )
            }
        }
      }
    }
}
