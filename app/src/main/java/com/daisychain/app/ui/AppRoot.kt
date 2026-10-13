package com.daisychain.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daisychain.app.R
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.ui.common.ComingSoonScreen
import com.daisychain.app.ui.automation.AutomationsScreen
import com.daisychain.app.ui.editor.EditorScreen
import com.daisychain.app.ui.help.HelpScreen
import com.daisychain.app.ui.library.LibraryScreen
import com.daisychain.app.ui.settings.SettingsScreen
import com.daisychain.app.ui.theme.Teal

private enum class Destination(val label: Int, val icon: ImageVector) {
    Shortcuts(R.string.nav_shortcuts, Icons.Outlined.GridView),
    Automations(R.string.nav_automations, Icons.Outlined.Bolt),
    Gallery(R.string.nav_gallery, Icons.Outlined.Image),
    Settings(R.string.nav_settings, Icons.Outlined.Tune),
}

@Composable
fun AppRoot(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    requestedShortcut: Long? = null,
    onRequestHandled: () -> Unit = {},
) {
    var current by rememberSaveable { mutableStateOf(Destination.Shortcuts) }
    var openShortcutId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(requestedShortcut) {
        requestedShortcut?.let { openShortcutId = it; showHelp = false; onRequestHandled() }
    }
    if (showHelp) {
        HelpScreen(onBack = { showHelp = false })
        return
    }
    openShortcutId?.let { id ->
        EditorScreen(shortcutId = id, onBack = { openShortcutId = null }, onOpenHelp = { showHelp = true })
        return
    }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    val content: @Composable () -> Unit = {
        when (current) {
            Destination.Shortcuts -> LibraryScreen(onOpenShortcut = { openShortcutId = it })
            Destination.Automations -> AutomationsScreen()
            Destination.Gallery -> ComingSoonScreen(
                Destination.Gallery.label, R.string.gallery_description, Destination.Gallery.icon,
            )
            Destination.Settings -> SettingsScreen(themeMode, onThemeModeChange, onOpenHelp = { showHelp = true })
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                header = {
                    Box(
                        Modifier.padding(vertical = 16.dp).size(48.dp)
                            .clip(RoundedCornerShape(14.dp)).background(Teal),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Layers, null, tint = Color.White) }
                },
            ) {
                Destination.entries.forEach { d ->
                    NavigationRailItem(
                        selected = d == current,
                        onClick = { current = d },
                        icon = { Icon(d.icon, null) },
                        label = { NavLabel(stringResource(d.label)) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content() }
        }
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // The bar below already pads the system navigation bar: the content must not pad it a second time.
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = d == current,
                        onClick = { current = d },
                        icon = { Icon(d.icon, null) },
                        label = { NavLabel(stringResource(d.label)) },
                    )
                }
            }
        }
    }
}

/** Same size for every label; long ones ("Automatisations") shrink instead of making the bar uneven. */
@Composable
private fun NavLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.sp),
    )
}
