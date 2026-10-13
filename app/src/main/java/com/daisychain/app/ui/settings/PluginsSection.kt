package com.daisychain.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.daisychain.app.R
import com.daisychain.app.plugin.PluginManager
import com.daisychain.engine.plugin.Plugin
import com.daisychain.engine.plugin.PluginException
import com.daisychain.engine.plugin.PluginParser

/** Lists the installed plugins and installs a new one from a .json file after showing what it can do. */
@Composable
fun PluginsSection() {
    val context = LocalContext.current
    val plugins by PluginManager.plugins.collectAsState()
    var pending by remember { mutableStateOf<Pair<Plugin, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { String(it.readNBytes(1_000_000), Charsets.UTF_8) } }.getOrNull()
        try {
            pending = PluginParser.parse(text ?: throw PluginException("Unreadable file")) to text
        } catch (e: PluginException) {
            error = e.message
        }
    }

    Column {
        if (plugins.isEmpty()) {
            Text(stringResource(R.string.plugins_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
        plugins.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.plugins_blocks, p.blocks.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { PluginManager.remove(context, p) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text(stringResource(R.string.plugins_import)) }
    }

    pending?.let { (plugin, text) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.plugins_install_title, plugin.name)) },
            text = {
                Column {
                    Text(stringResource(R.string.plugins_install_blocks, plugin.blocks.size))
                    if (plugin.needsNetwork) Text("• " + stringResource(R.string.plugins_cap_network))
                    if (plugin.usesIntents) Text("• " + stringResource(R.string.plugins_cap_intents))
                    Text(stringResource(R.string.plugins_install_warning), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { PluginManager.install(context, text); pending = null }) { Text(stringResource(R.string.plugins_install)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    error?.let {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text(stringResource(R.string.plugins_invalid)) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}
