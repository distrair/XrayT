package app.xrayt

import android.content.Context
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private enum class ConnectionState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting
}

private fun isReducedMotionEnabled(context: Context): Boolean = runCatching {
    val resolver = context.contentResolver
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
        Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
}.getOrDefault(false)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ConnectTab(store: Store, subs: List<Subscription>, onConnect: () -> Unit, onDisconnect: () -> Unit, scope: CoroutineScope) {
    val running by XrayVpnService.running.collectAsState()
    val err by XrayVpnService.lastError.collectAsState()
    val ctx = LocalContext.current
    val reducedMotion = remember(ctx) { isReducedMotionEnabled(ctx) }

    var transientState by remember { mutableStateOf<ConnectionState?>(null) }
    LaunchedEffect(running, err) {
        transientState = null
    }

    val connectionState = when {
        transientState != null -> transientState!!
        running -> ConnectionState.Connected
        else -> ConnectionState.Disconnected
    }

    var geoAuto by remember { mutableStateOf(store.geoAuto) }
    var geoTs by remember { mutableLongStateOf(store.geoUpdated) }
    var geoBusy by remember { mutableStateOf(false) }
    val sub = subs.firstOrNull { it.id == store.activeSub }
    val config = sub?.configs?.getOrNull(store.activeIdx)
    val profileName = config?.let(::configName) ?: "No profile selected"

    val colorEffectsSpec = if (reducedMotion) snap<Color>() else MaterialTheme.motionScheme.fastEffectsSpec()
    val floatSpatialSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastSpatialSpec()
    val dpSpatialSpec = if (reducedMotion) snap<Dp>() else MaterialTheme.motionScheme.fastSpatialSpec()
    val floatEffectsSpec = if (reducedMotion) snap<Float>() else MaterialTheme.motionScheme.fastEffectsSpec()

    val targetContainer = when (connectionState) {
        ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val targetContent = when (connectionState) {
        ConnectionState.Connected -> MaterialTheme.colorScheme.onPrimary
        ConnectionState.Connecting -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val buttonContainer by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = colorEffectsSpec,
        label = "connectionButtonContainer"
    )
    val buttonContent by animateColorAsState(
        targetValue = targetContent,
        animationSpec = colorEffectsSpec,
        label = "connectionButtonContent"
    )

    val targetScale = when (connectionState) {
        ConnectionState.Connected -> 1.04f
        ConnectionState.Connecting, ConnectionState.Disconnecting -> 0.96f
        ConnectionState.Disconnected -> 1.0f
    }
    val buttonScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = floatSpatialSpec,
        label = "connectionButtonScale"
    )

    val targetOffsetY = when (connectionState) {
        ConnectionState.Connected -> (-4).dp
        ConnectionState.Connecting, ConnectionState.Disconnecting -> 2.dp
        ConnectionState.Disconnected -> 0.dp
    }
    val buttonOffsetY by animateDpAsState(
        targetValue = targetOffsetY,
        animationSpec = dpSpatialSpec,
        label = "connectionButtonOffsetY"
    )

    val targetElevation = when (connectionState) {
        ConnectionState.Connected -> 6.dp
        ConnectionState.Connecting -> 1.dp
        ConnectionState.Disconnecting -> 0.dp
        ConnectionState.Disconnected -> 2.dp
    }
    val buttonElevation by animateDpAsState(
        targetValue = targetElevation,
        animationSpec = dpSpatialSpec,
        label = "connectionButtonElevation"
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
                    val canInteract = connectionState == ConnectionState.Disconnected || connectionState == ConnectionState.Connected
                    Button(
                        onClick = {
                            if (!canInteract) return@Button
                            if (connectionState == ConnectionState.Connected) {
                                transientState = ConnectionState.Disconnecting
                                onDisconnect()
                            } else {
                                transientState = ConnectionState.Connecting
                                onConnect()
                            }
                        },
                        modifier = Modifier
                            .size(if (compactHeight) 192.dp else 200.dp)
                            .offset(y = buttonOffsetY)
                            .graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            },
                        shape = CircleShape,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = buttonElevation),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainer,
                            contentColor = buttonContent
                        )
                    ) {
                        AnimatedContent(
                            targetState = connectionState,
                            transitionSpec = {
                                if (reducedMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(animationSpec = floatEffectsSpec) togetherWith fadeOut(animationSpec = floatEffectsSpec)
                                }
                            },
                            label = "buttonContentTransition"
                        ) { state ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                when (state) {
                                    ConnectionState.Connecting, ConnectionState.Disconnecting -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = buttonContent,
                                            strokeWidth = 3.5.dp
                                        )
                                    }
                                    ConnectionState.Connected, ConnectionState.Disconnected -> {
                                        Icon(
                                            Icons.Filled.PowerSettingsNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Text(
                                    when (state) {
                                        ConnectionState.Connected -> "Disconnect"
                                        ConnectionState.Connecting -> "Connecting…"
                                        ConnectionState.Disconnecting -> "Disconnecting…"
                                        ConnectionState.Disconnected -> "Connect"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AnimatedContent(
                            targetState = connectionState,
                            transitionSpec = {
                                if (reducedMotion) {
                                    EnterTransition.None togetherWith ExitTransition.None
                                } else {
                                    fadeIn(animationSpec = floatEffectsSpec) togetherWith fadeOut(animationSpec = floatEffectsSpec)
                                }
                            },
                            label = "statusTextTransition"
                        ) { state ->
                            Text(
                                when (state) {
                                    ConnectionState.Connected -> "Connected"
                                    ConnectionState.Connecting -> "Connecting…"
                                    ConnectionState.Disconnecting -> "Disconnecting…"
                                    ConnectionState.Disconnected -> "Disconnected"
                                },
                                style = MaterialTheme.typography.titleLargeEmphasized
                            )
                        }
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
