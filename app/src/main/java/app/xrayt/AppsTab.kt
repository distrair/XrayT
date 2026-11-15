package app.xrayt

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        Text(
            "Per-app routing settings apply next time you connect",
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
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
