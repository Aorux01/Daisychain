package com.daisychain.app.plugin

import android.content.Context
import com.daisychain.engine.plugin.Plugin
import com.daisychain.engine.plugin.PluginParser
import com.daisychain.engine.spec.ActionRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Keeps the plugin files in the app's private folder and their blocks in [ActionRegistry]. */
object PluginManager {
    private val _plugins = MutableStateFlow<List<Plugin>>(emptyList())
    val plugins: StateFlow<List<Plugin>> = _plugins

    private fun dir(context: Context) = File(context.filesDir, "plugins").apply { mkdirs() }

    private fun fileOf(context: Context, id: String) = File(dir(context), "$id.json")

    private fun activate(plugin: Plugin) {
        ActionRegistry.unregisterPlugin(plugin.id)
        plugin.blocks.forEach { ActionRegistry.register(it, plugin.id) }
        _plugins.value = _plugins.value.filter { it.id != plugin.id } + plugin
    }

    /** Loads every installed plugin; a broken file is skipped. */
    fun loadAll(context: Context) {
        dir(context).listFiles { f -> f.extension == "json" }?.forEach { f ->
            runCatching { activate(PluginParser.parse(f.readText())) }
        }
    }

    /** Checks [text] and installs it. Throws `PluginException` with a readable reason when it is not a valid plugin. */
    fun install(context: Context, text: String): Plugin {
        val plugin = PluginParser.parse(text)
        // A plugin must not take over a built-in block: ids are prefixed with "plugin.", so only its own are replaced.
        fileOf(context, plugin.id).writeText(text)
        activate(plugin)
        return plugin
    }

    fun remove(context: Context, plugin: Plugin) {
        ActionRegistry.unregisterPlugin(plugin.id)
        fileOf(context, plugin.id).delete()
        _plugins.value = _plugins.value.filter { it.id != plugin.id }
    }
}
