package com.daisychain.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.ui.AppRoot
import com.daisychain.app.ui.theme.DaisychainTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    /** The shortcut another part of the app asked to open (the "Open shortcut" block). */
    private val openRequest = mutableStateOf<Long?>(null)

    private fun requestedShortcut(intent: Intent?): Long? = intent?.getLongExtra(EXTRA_OPEN_SHORTCUT, -1)?.takeIf { it > 0 }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest.value = requestedShortcut(intent)
    }

    companion object {
        const val EXTRA_OPEN_SHORTCUT = "open_shortcut_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SettingsRepository(applicationContext)
        openRequest.value = requestedShortcut(intent)
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.System)
            val scope = rememberCoroutineScope()
            DaisychainTheme(themeMode) {
                // The Surface sets the default text colour (white on dark backgrounds, black on light).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        requestedShortcut = openRequest.value,
                        onRequestHandled = { openRequest.value = null },
                        themeMode = themeMode,
                        onThemeModeChange = { mode -> scope.launch { settings.setThemeMode(mode) } },
                    )
                }
            }
        }
    }
}
