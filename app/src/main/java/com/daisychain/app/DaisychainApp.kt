package com.daisychain.app

import android.app.Application
import com.daisychain.app.automation.AutomationController
import com.daisychain.app.data.ShortcutRepository
import com.daisychain.app.plugin.PluginManager
import com.daisychain.app.data.db.AppDatabase
import com.daisychain.app.run.HomeShortcuts
import com.daisychain.app.widget.ShortcutWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DaisychainApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val shortcutRepository by lazy { ShortcutRepository(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        PluginManager.loadAll(this)
        // Keep the app icon's long-press menu (favourites) and pinned copies in step with the library.
        appScope.launch {
            shortcutRepository.allShortcuts
                // Only what the launcher shows matters; edits to the actions do not.
                .map { list -> list.map { it.copy(actionsJson = "", actionCount = 0, createdAt = 0, updatedAt = 0) } }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { list ->
                    HomeShortcuts.sync(this@DaisychainApp, list.filter { it.isFavorite }, list)
                    ShortcutWidgetProvider.refreshAll(this@DaisychainApp)
                }
        }
        // Keep alarms and the listening service in step with the automations.
        appScope.launch {
            shortcutRepository.allAutomations.distinctUntilChanged().debounce(500).collect { AutomationController.sync(this@DaisychainApp, it) }
        }
    }
}
