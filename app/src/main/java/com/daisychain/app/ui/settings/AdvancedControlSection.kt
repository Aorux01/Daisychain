package com.daisychain.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daisychain.app.R
import com.daisychain.app.run.PrivilegedShell

/** Wi-Fi, Bluetooth, mobile data and airplane mode blocks need the Shizuku app: shows where that stands. */
@Composable
fun AdvancedControlSection() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(PrivilegedShell.state(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state = PrivilegedShell.state(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column {
        Text(stringResource(R.string.advanced_explain), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        Text(
            stringResource(
                when (state) {
                    PrivilegedShell.State.Ready -> R.string.shizuku_ready
                    PrivilegedShell.State.NeedsPermission -> R.string.shizuku_needs_permission
                    PrivilegedShell.State.NotRunning -> R.string.shizuku_not_running
                    PrivilegedShell.State.NotInstalled -> R.string.shizuku_not_installed
                },
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        when (state) {
            PrivilegedShell.State.Ready -> Unit
            PrivilegedShell.State.NeedsPermission -> OutlinedButton(onClick = { PrivilegedShell.requestPermission() }) { Text(stringResource(R.string.shizuku_allow)) }
            PrivilegedShell.State.NotRunning -> OutlinedButton(onClick = { PrivilegedShell.openShizuku(context) }) { Text(stringResource(R.string.shizuku_open)) }
            PrivilegedShell.State.NotInstalled -> OutlinedButton(onClick = { PrivilegedShell.openShizuku(context) }) { Text(stringResource(R.string.shizuku_install)) }
        }
    }
}
