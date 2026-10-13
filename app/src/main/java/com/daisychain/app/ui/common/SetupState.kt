package com.daisychain.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daisychain.app.run.PrivilegedShell
import com.daisychain.engine.spec.ActionDefinition
import com.daisychain.engine.spec.Requirement

/** Marks a block that exists but cannot work yet (it needs a setup the user has not done). */
val SetupOrange = Color(0xFFF59E0B)

/** Whether Shizuku is running and Daisychain is allowed; checked again each time the screen comes back. */
@Composable
fun rememberShizukuReady(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(PrivilegedShell.state(context) == PrivilegedShell.State.Ready) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) ready = PrivilegedShell.state(context) == PrivilegedShell.State.Ready
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return ready
}

/** True when [def] needs Shizuku and it is not ready. */
fun ActionDefinition?.needsShizukuSetup(shizukuReady: Boolean): Boolean = this != null && Requirement.Shizuku in requirements && !shizukuReady
