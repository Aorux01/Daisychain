package com.daisychain.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
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
import com.daisychain.app.DaisychainApp
import com.daisychain.app.R
import com.daisychain.app.data.SettingsRepository
import com.daisychain.app.data.ThemeMode
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.run.HomeShortcuts
import com.daisychain.app.run.ShortcutArt
import com.daisychain.app.ui.common.ShortcutPickList
import com.daisychain.app.ui.theme.DaisychainTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Which shortcut each widget runs. */
private object WidgetPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun get(context: Context, widgetId: Int): Long? = prefs(context).getLong("widget_$widgetId", -1).takeIf { it > 0 }
    fun set(context: Context, widgetId: Int, shortcutId: Long) = prefs(context).edit().putLong("widget_$widgetId", shortcutId).apply()
    fun remove(context: Context, ids: IntArray) = prefs(context).edit().also { e -> ids.forEach { e.remove("widget_$it") } }.apply()
}

/** A home screen tile that runs one shortcut when touched. */
class ShortcutWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = (context.applicationContext as DaisychainApp).shortcutRepository
                appWidgetIds.forEach { id -> manager.updateAppWidget(id, views(context, id, WidgetPrefs.get(context, id)?.let { repo.observeShortcut(it).first() })) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = WidgetPrefs.remove(context, appWidgetIds)

    companion object {
        fun views(context: Context, widgetId: Int, shortcut: ShortcutEntity?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_shortcut)
            if (shortcut == null) {
                views.setImageViewBitmap(R.id.widget_image, ShortcutArt.tile(context.getString(R.string.widget_choose), "bolt", "slate"))
                val configure = Intent(context, ShortcutWidgetConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                views.setOnClickPendingIntent(
                    R.id.widget_image,
                    PendingIntent.getActivity(context, widgetId, configure, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                views.setImageViewBitmap(R.id.widget_image, ShortcutArt.tile(shortcut.name, shortcut.iconKey, shortcut.colorKey))
                views.setOnClickPendingIntent(
                    R.id.widget_image,
                    PendingIntent.getActivity(context, widgetId, HomeShortcuts.runIntent(context, shortcut.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            return views
        }

        /** Redraws every widget (a shortcut was renamed, recoloured or deleted). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ShortcutWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, ShortcutWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }

        internal fun choose(context: Context, widgetId: Int, shortcut: ShortcutEntity) {
            WidgetPrefs.set(context, widgetId, shortcut.id)
            AppWidgetManager.getInstance(context).updateAppWidget(widgetId, views(context, widgetId, shortcut))
        }
    }
}

/** Opens when a widget is added: the user picks the shortcut it will run. */
class ShortcutWidgetConfigActivity : ComponentActivity() {
    private var shortcuts by mutableStateOf<List<ShortcutEntity>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val settings = SettingsRepository(applicationContext)
        setContent {
            val theme by settings.themeMode.collectAsState(initial = ThemeMode.System)
            DaisychainTheme(theme) {
                shortcuts?.let { list ->
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text(stringResource(R.string.widget_choose)) },
                        text = {
                            androidx.compose.foundation.layout.Box(Modifier.heightIn(max = 420.dp).padding(top = 4.dp)) {
                                ShortcutPickList(list) { picked ->
                                    ShortcutWidgetProvider.choose(this@ShortcutWidgetConfigActivity, widgetId, picked)
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                    finish()
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
            }
        }
        lifecycleScope.launch { shortcuts = (application as DaisychainApp).shortcutRepository.allShortcuts.first() }
    }
}
