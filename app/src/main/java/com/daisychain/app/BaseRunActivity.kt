package com.daisychain.app

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.run.AndroidDevice
import com.daisychain.app.run.RunHost
import com.daisychain.app.run.RuntimePermissions
import com.daisychain.app.ui.editor.RunPromptDialog
import com.daisychain.app.ui.editor.SystemRequestHost
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.run.RunValue
import kotlinx.coroutines.launch

/**
 * An activity that runs a shortcut without the editor: it asks for the permissions the shortcut needs,
 * runs it, shows only the questions it asks, and says how it went with a toast that outlives the window.
 */
abstract class BaseRunActivity : ComponentActivity() {
    protected val host = RunHost(lifecycleScope)

    private class Pending(val name: String, val actions: List<ActionInstance>, val input: RunValue)

    private var pending: Pending? = null
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startPending() }

    /** The questions of the running shortcut (results, alerts, menus, pickers). Put this in the content. */
    @Composable
    protected fun RunPrompts() {
        val prompt by host.prompt.collectAsState()
        SystemRequestHost(prompt)
        prompt?.let { RunPromptDialog(it) }
    }

    protected fun run(shortcut: ShortcutEntity, input: RunValue = RunValue.Nothing) {
        val actions = ActionOps.sanitize(ActionsJson.decode(shortcut.actionsJson))
        pending = Pending(shortcut.name, actions, input)
        val missing = RuntimePermissions.missing(this, actions)
        if (missing.isNotEmpty()) permissionRequest.launch(missing) else startPending()
    }

    /** Called when the run is over (finished, failed or stopped). The default closes the window. */
    protected open fun onRunFinished() = finish()

    private fun startPending() {
        val p = pending ?: return
        lifecycleScope.launch {
            val app = applicationContext
            Toast.makeText(app, getString(R.string.running_shortcut, p.name), Toast.LENGTH_SHORT).show()
            // No need to light up blocks here, so no pause between them.
            val job = host.run(p.actions, AndroidDevice(app), stepDelayMs = 0, library = (application as DaisychainApp).shortcutRepository.library, shortcutInput = p.input)
            job.join()
            val error = host.error.value
            when {
                error != null -> Toast.makeText(app, error.resolve(resources.configuration.locales[0].language), Toast.LENGTH_LONG).show()
                !job.isCancelled -> Toast.makeText(app, getString(R.string.finished_shortcut, p.name), Toast.LENGTH_SHORT).show()
            }
            onRunFinished()
        }
    }
}
