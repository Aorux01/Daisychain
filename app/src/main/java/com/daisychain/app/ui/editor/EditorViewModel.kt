package com.daisychain.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.daisychain.app.DaisychainApp
import com.daisychain.app.data.ShortcutRepository
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.app.run.RunHost
import com.daisychain.engine.edit.ActionOps
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.model.ParamValue
import com.daisychain.engine.run.DeviceApi
import com.daisychain.engine.spec.ActionDefinition
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Edits the actions of one shortcut in memory, saves them shortly after every change, and runs them. */
@OptIn(FlowPreview::class)
class EditorViewModel(private val repo: ShortcutRepository, private val id: Long) : ViewModel() {
    val shortcut: StateFlow<ShortcutEntity?> = repo.observeShortcut(id)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _actions = MutableStateFlow<List<ActionInstance>>(emptyList())
    val actions: StateFlow<List<ActionInstance>> = _actions.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val host = RunHost(viewModelScope)
    val runState = host.state
    val prompt = host.prompt
    val error = host.error

    private var saved: List<ActionInstance> = emptyList()

    init {
        viewModelScope.launch {
            repo.observeShortcut(id).first()?.let {
                saved = ActionsJson.decode(it.actionsJson)
                val clean = ActionOps.sanitize(saved)
                _actions.value = clean
                save(clean) // writes only if something was reset
            }
            _loaded.value = true
            _actions.drop(1).debounce(300).collect { save(it) }
        }
    }

    private suspend fun save(actions: List<ActionInstance>) {
        if (actions == saved) return
        repo.saveActions(id, actions)
        saved = actions
    }

    /** Saves right now; called when leaving the editor. */
    fun flush() { viewModelScope.launch { save(_actions.value) } }

    private fun edit(f: (List<ActionInstance>) -> List<ActionInstance>) = _actions.update(f)

    /** Adds [def] at position [index] of the action list, or at the end. */
    fun addAction(def: ActionDefinition, index: Int? = null) =
        edit { ActionOps.insertAt(it, index ?: it.size, ActionOps.create(def)) }

    fun remove(uuid: String) = edit { ActionOps.remove(it, uuid) }
    fun duplicate(uuid: String) = edit { ActionOps.duplicate(it, uuid) }
    fun move(uuid: String, index: Int) = edit { ActionOps.move(it, uuid, index) }
    fun setParam(uuid: String, key: String, value: ParamValue?) = edit { ActionOps.setParam(it, uuid, key, value) }
    /** Creates the variable [name] just before the block [beforeUuid] (see [ActionOps.insertSetVariableBefore]). */
    fun createVariable(beforeUuid: String, name: String) = edit { ActionOps.insertSetVariableBefore(it, beforeUuid, name) }

    fun setOutputName(uuid: String, name: String?) = edit { ActionOps.setOutputName(it, uuid, name) }
    fun replaceBlock(uuid: String, def: ActionDefinition) = edit { ActionOps.replace(it, uuid, def) }
    fun addSegment(uuid: String) = edit { ActionOps.addSegment(it, uuid) }
    fun addRow(uuid: String) = edit { ActionOps.addRow(it, uuid) }
    fun setRowParam(uuid: String, row: Int, key: String, value: ParamValue?) = edit { ActionOps.setRowParam(it, uuid, row, key, value) }
    fun removeRow(uuid: String, row: Int) = edit { ActionOps.removeRow(it, uuid, row) }

    fun updateAppearance(name: String, iconKey: String, colorKey: String) {
        viewModelScope.launch { repo.updateAppearance(id, name, iconKey, colorKey) }
    }

    fun run(device: DeviceApi) {
        flush()
        host.run(_actions.value, device, library = repo.library)
    }

    fun stop() = host.stop()
    fun clearError() = host.clearError()

    class Factory(private val id: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DaisychainApp
            return EditorViewModel(app.shortcutRepository, id) as T
        }
    }
}
