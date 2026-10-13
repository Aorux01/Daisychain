package com.daisychain.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.daisychain.app.data.ImportResult
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ShortcutImporter
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.ui.library.ImportResultDialog
import com.daisychain.app.ui.theme.DaisychainTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opens a `.shortcut` file given by another app, imports it and offers to open it. */
class ImportActivity : ComponentActivity() {
    private var result by mutableStateOf<ImportResult?>(null)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        setContent {
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.System)
            DaisychainTheme(theme) {
                result?.let { ImportResultDialog(it, onOpen = ::openShortcut, onDismiss = { finish() }) }
            }
        }
        val uri: Uri? = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        lifecycleScope.launch {
            val (bytes, name) = withContext(Dispatchers.IO) {
                var displayName = uri.lastPathSegment.orEmpty()
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { displayName = it }
                }
                val data = runCatching { contentResolver.openInputStream(uri)?.use { it.readNBytes(5 * 1024 * 1024) } }.getOrNull() ?: ByteArray(0)
                data to displayName
            }
            result = ShortcutImporter((application as DaisychainApp).shortcutRepository).import(bytes, name)
        }
    }

    private fun openShortcut(id: Long) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SHORTCUT, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
