package app.xrayt

import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun SubsTab(store: Store, subs: List<Subscription>, save: (List<Subscription>) -> Unit) {
    var edit by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    var importUriDialog by rememberSaveable { mutableStateOf(false) }
    var importUriText by rememberSaveable { mutableStateOf("") }
    var addMenuExpanded by rememberSaveable { mutableStateOf(false) }
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.name.ifBlank { s.url.ifBlank { "Imported profiles" } },
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Subscription actions")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    if (s.url.isNotBlank() || s.configs.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = { menuExpanded = false; edit = s }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = { menuExpanded = false; pendingDelete = s }
                                    )
                                }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                }
                            ) {
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
                                                    ?: ping.error
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
        BackHandler(addMenuExpanded) { addMenuExpanded = false }
        FloatingActionButtonMenu(
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            expanded = addMenuExpanded,
            button = {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                        if (addMenuExpanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                    ),
                    tooltip = {
                        PlainTooltip {
                            Text(if (addMenuExpanded) "Close profile actions" else "Add or import profiles")
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    ToggleFloatingActionButton(
                        modifier = Modifier.semantics {
                            contentDescription = "Profile actions"
                            stateDescription = if (addMenuExpanded) "Expanded" else "Collapsed"
                        },
                        checked = addMenuExpanded,
                        onCheckedChange = { addMenuExpanded = it }
                    ) {
                        val imageVector by remember {
                            derivedStateOf {
                                if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                            }
                        }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon(checkedProgress = { checkedProgress })
                        )
                    }
                }
            }
        ) {
            FloatingActionButtonMenuItem(
                onClick = { addMenuExpanded = false; edit = Subscription() },
                icon = { Icon(Icons.Filled.Subscriptions, contentDescription = null) },
                text = { Text("Add subscription") }
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    addMenuExpanded = false
                    importUriText = ""
                    importUriDialog = true
                },
                icon = { Icon(Icons.Filled.Link, contentDescription = null) },
                text = { Text("Import Xray URI") }
            )
            FloatingActionButtonMenuItem(
                onClick = { addMenuExpanded = false; jsonPicker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                text = { Text("Import JSON file") }
            )
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
