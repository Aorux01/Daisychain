package com.daisychain.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.daisychain.app.R
import com.daisychain.app.data.ThemeMode

/** Language tag ("" = follow the system) to label. Language names are not translated. */
private val languages = listOf("" to null, "en" to "English", "fr" to "Français", "it" to "Italiano", "de" to "Deutsch", "es" to "Español")

@Composable
fun SettingsScreen(themeMode: ThemeMode, onThemeModeChange: (ThemeMode) -> Unit, onOpenHelp: () -> Unit) {
    val context = LocalContext.current
    var language by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-'))
    }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(
            stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenHelp),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.settings_help),
                    Modifier.weight(1f).padding(start = 14.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SectionTitle(R.string.settings_theme)
        listOf(
            ThemeMode.System to R.string.theme_system,
            ThemeMode.Light to R.string.theme_light,
            ThemeMode.Dark to R.string.theme_dark,
        ).forEach { (mode, label) ->
            OptionRow(stringResource(label), themeMode == mode) { onThemeModeChange(mode) }
        }

        SectionTitle(R.string.settings_language)
        languages.forEach { (tag, name) ->
            OptionRow(name ?: stringResource(R.string.language_system), language == tag) {
                language = tag
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
        }

        SectionTitle(R.string.settings_advanced)
        AdvancedControlSection()

        SectionTitle(R.string.settings_plugins)
        PluginsSection()

        SectionTitle(R.string.settings_about)
        Text(
            stringResource(R.string.app_name) + " · " + stringResource(R.string.settings_version, version),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun SectionTitle(label: Int) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
