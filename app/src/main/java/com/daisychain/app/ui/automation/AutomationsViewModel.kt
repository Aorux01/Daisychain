package com.daisychain.app.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import com.daisychain.app.DaisychainApp
import com.daisychain.app.data.ShortcutRepository
import com.daisychain.app.data.db.AutomationEntity
import com.daisychain.app.data.db.ShortcutEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AutomationsViewModel(private val repo: ShortcutRepository) : ViewModel() {
    val automations: StateFlow<List<AutomationEntity>> =
        repo.allAutomations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shortcuts: StateFlow<List<ShortcutEntity>> =
        repo.allShortcuts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(a: AutomationEntity) = viewModelScope.launch { repo.saveAutomation(a) }
    fun delete(a: AutomationEntity) = viewModelScope.launch { repo.deleteAutomation(a.id) }
    fun setEnabled(a: AutomationEntity, enabled: Boolean) = viewModelScope.launch { repo.setAutomationEnabled(a.id, enabled) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DaisychainApp
                AutomationsViewModel(app.shortcutRepository)
            }
        }
    }
}
