package app.xrayt

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

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
