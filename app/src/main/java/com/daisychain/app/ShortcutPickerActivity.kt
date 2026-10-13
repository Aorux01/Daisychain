package com.daisychain.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.ui.common.ShortcutPickList
import com.daisychain.app.ui.theme.DaisychainTheme
import com.daisychain.engine.actions.mimeFor
import com.daisychain.engine.run.RunValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Run a shortcut on this": it is what the share menu of other apps opens, and what the quick settings tile opens.
 * The user picks a shortcut; what was shared becomes its "Shortcut Input".
 */
class ShortcutPickerActivity : BaseRunActivity() {
    private var shortcuts by mutableStateOf<List<ShortcutEntity>?>(null)
    private var running by mutableStateOf(false)
    private var input: RunValue = RunValue.Nothing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.System)
            DaisychainTheme(theme) {
                val list = shortcuts
                if (list != null && !running) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text(stringResource(R.string.pick_shortcut_title)) },
                        text = {
                            androidx.compose.foundation.layout.Box(Modifier.heightIn(max = 420.dp).padding(top = 4.dp)) {
                                ShortcutPickList(list) { picked ->
                                    running = true
                                    run(picked, input)
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
                RunPrompts()
            }
        }
        lifecycleScope.launch {
            input = readInput(intent)
            shortcuts = (application as DaisychainApp).shortcutRepository.allShortcuts.first()
        }
    }

    /** What another app shared with us: some text, an image, a file or several of them. */
    @Suppress("DEPRECATION")
    private suspend fun readInput(intent: Intent): RunValue = withContext(Dispatchers.IO) {
        fun fileOf(uri: Uri): RunValue {
            val resolver = contentResolver
            var name = uri.lastPathSegment.orEmpty()
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
            val mime = resolver.getType(uri) ?: mimeFor(name)
            val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readNBytes(30 * 1024 * 1024) } }.getOrNull() ?: return RunValue.Nothing
            return if (mime.startsWith("image/")) RunValue.Image(bytes, mime) else RunValue.File(name, mime, bytes)
        }
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) fileOf(stream) else RunValue.Text(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            }
            Intent.ACTION_SEND_MULTIPLE ->
                RunValue.Items(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().map(::fileOf))
            else -> RunValue.Nothing
        }
    }

    companion object {
        const val ACTION_PICK = "com.daisychain.app.PICK_SHORTCUT"
    }
}
