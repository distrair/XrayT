package app.xrayt

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

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

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val compactHeight = maxHeight < 680.dp
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxHeight().fillMaxWidth()
                .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 20.dp, vertical = if (compactHeight) 8.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compactHeight) 10.dp else 16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(profileName, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            Box(
                Modifier.then(
                    if (compactHeight) Modifier.fillMaxWidth().height(252.dp)
                    else Modifier.weight(1f).fillMaxWidth()
                ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.offset(y = if (compactHeight) 0.dp else 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compactHeight) 12.dp else 20.dp)
                ) {
                    Button(
                        onClick = { if (running) onDisconnect() else onConnect() },
                        modifier = Modifier.size(if (compactHeight) 192.dp else 200.dp),
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
                        Text(if (running) "Connected" else "Disconnected", style = MaterialTheme.typography.titleLargeEmphasized)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
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
