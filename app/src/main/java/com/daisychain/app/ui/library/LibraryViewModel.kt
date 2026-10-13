package com.daisychain.app.ui.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daisychain.app.DaisychainApp
import com.daisychain.app.data.ImportResult
import com.daisychain.app.data.ShortcutImporter
import com.daisychain.app.data.ShortcutRepository
import com.daisychain.app.data.db.FolderEntity
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.spec.ActionRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LibraryFilter {
    data object All : LibraryFilter
    data object Favorites : LibraryFilter
    data class Folder(val id: Long) : LibraryFilter
}

data class LibraryUiState(
    val loaded: Boolean = false,
    val shortcuts: List<ShortcutEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.All,
    val query: String = "",
    /** Shortcuts that contain a block this version of the app does not know. */
    val missing: Set<Long> = emptySet(),
)

class LibraryViewModel(private val repo: ShortcutRepository) : ViewModel() {
    private val filter = MutableStateFlow<LibraryFilter>(LibraryFilter.All)
    private val query = MutableStateFlow("")

    val state: StateFlow<LibraryUiState> = combine(
        repo.allShortcuts, repo.allFolders, filter, query,
    ) { shortcuts, folders, filter, query ->
        // A deleted folder falls back to "All".
        val effective = if (filter is LibraryFilter.Folder && folders.none { it.id == filter.id }) {
            LibraryFilter.All
        } else filter
        val byFilter = when (effective) {
            LibraryFilter.All -> shortcuts
            LibraryFilter.Favorites -> shortcuts.filter { it.isFavorite }
            is LibraryFilter.Folder -> shortcuts.filter { it.folderId == effective.id }
        }
        val q = query.trim()
        LibraryUiState(
            loaded = true,
            shortcuts = if (q.isEmpty()) byFilter else byFilter.filter { it.name.contains(q, ignoreCase = true) },
            folders = folders,
            filter = effective,
            query = query,
            missing = shortcuts.filter { s ->
                ActionsJson.decode(s.actionsJson).any { ActionRegistry.find(it.identifier) == null }
            }.map { it.id }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    private val importer = ShortcutImporter(repo)

    /** Reads a chosen `.shortcut` file (at most 5 MB) and adds it to the library. */
    fun importFile(resolver: ContentResolver, uri: Uri, onResult: (ImportResult) -> Unit) = viewModelScope.launch {
        val (bytes, name) = withContext(Dispatchers.IO) {
            var displayName = uri.lastPathSegment.orEmpty()
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { displayName = it }
            }
            val data = runCatching { resolver.openInputStream(uri)?.use { it.readNBytes(5 * 1024 * 1024) } }.getOrNull() ?: ByteArray(0)
            data to displayName
        }
        val folderId = (state.value.filter as? LibraryFilter.Folder)?.id
        onResult(importer.import(bytes, name, folderId))
    }

    fun setFilter(f: LibraryFilter) { filter.value = f }
    fun setQuery(q: String) { query.update { q } }

    fun createShortcut(name: String, iconKey: String, colorKey: String, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            val folderId = (state.value.filter as? LibraryFilter.Folder)?.id
            onCreated(repo.createShortcut(name, iconKey, colorKey, folderId))
        }

    fun updateAppearance(s: ShortcutEntity, name: String, iconKey: String, colorKey: String) =
        viewModelScope.launch { repo.updateAppearance(s.id, name, iconKey, colorKey) }

    fun duplicate(s: ShortcutEntity, copyName: String) = viewModelScope.launch { repo.duplicate(s, copyName) }
    fun delete(s: ShortcutEntity) = viewModelScope.launch { repo.deleteShortcut(s) }
    fun toggleFavorite(s: ShortcutEntity) = viewModelScope.launch { repo.setFavorite(s.id, !s.isFavorite) }
    fun moveToFolder(s: ShortcutEntity, folderId: Long?) = viewModelScope.launch { repo.moveToFolder(s.id, folderId) }

    fun createFolder(name: String) = viewModelScope.launch {
        filter.value = LibraryFilter.Folder(repo.createFolder(name))
    }

    fun renameFolder(id: Long, name: String) = viewModelScope.launch { repo.renameFolder(id, name) }
    fun deleteFolder(id: Long) = viewModelScope.launch { repo.deleteFolder(id) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DaisychainApp
                LibraryViewModel(app.shortcutRepository)
            }
        }
    }
}
