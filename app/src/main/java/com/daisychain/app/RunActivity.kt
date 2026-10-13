package com.daisychain.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.ui.theme.DaisychainTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs one shortcut from the home screen, a widget or an automation. It has no window of its own:
 * it only shows the questions the shortcut asks and closes when the run is over.
 */
class RunActivity : BaseRunActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.System)
            DaisychainTheme(theme) { RunPrompts() }
        }

        val id = intent.getLongExtra(EXTRA_SHORTCUT_ID, -1)
        lifecycleScope.launch {
            val shortcut = (application as DaisychainApp).shortcutRepository.observeShortcut(id).first()
            if (shortcut == null) {
                Toast.makeText(this@RunActivity, R.string.shortcut_missing, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                run(shortcut)
            }
        }
    }

    companion object {
        const val ACTION_RUN = "com.daisychain.app.RUN_SHORTCUT"
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
    }
}
