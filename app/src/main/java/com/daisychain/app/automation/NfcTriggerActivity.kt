package com.daisychain.app.automation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.daisychain.app.BaseRunActivity
import com.daisychain.app.DaisychainApp
import com.daisychain.app.R
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.ui.theme.DaisychainTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Scanning an NFC tag that holds the address `daisychain://nfc/<secret>` opens this screen, which runs the shortcut
 * of the automation with that secret. The tap is on screen, so every block can work.
 */
class NfcTriggerActivity : BaseRunActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.System)
            DaisychainTheme(theme) { RunPrompts() }
        }
        val token = intent.data?.lastPathSegment.orEmpty()
        lifecycleScope.launch {
            val repo = (application as DaisychainApp).shortcutRepository
            val automation = repo.automationsOfType(TriggerType.Nfc.id).firstOrNull { it.enabled && token.isNotEmpty() && TriggerParams.fromJson(it.param).token == token }
            val shortcut = automation?.let { repo.observeShortcut(it.shortcutId).first() }
            if (shortcut == null) {
                Toast.makeText(this@NfcTriggerActivity, R.string.nfc_not_found, Toast.LENGTH_SHORT).show()
                finish()
            } else {
                run(shortcut)
            }
        }
    }
}
