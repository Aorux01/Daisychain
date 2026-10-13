package com.daisychain.app.data

import com.daisychain.app.data.db.AppDatabase
import com.daisychain.app.data.db.AutomationEntity
import com.daisychain.app.data.db.FolderEntity
import com.daisychain.app.data.db.ShortcutEntity
import com.daisychain.engine.model.ActionInstance
import com.daisychain.engine.model.ActionsJson
import com.daisychain.engine.run.ShortcutLibrary
import kotlinx.coroutines.flow.Flow

class ShortcutRepository(db: AppDatabase) {
    private val shortcuts = db.shortcutDao()
    private val folders = db.folderDao()
    private val automations = db.automationDao()

    val allShortcuts: Flow<List<ShortcutEntity>> = shortcuts.observeAll()
    val allFolders: Flow<List<FolderEntity>> = folders.observeAll()

    suspend fun createShortcut(name: String, iconKey: String, colorKey: String, folderId: Long?): Long =
        shortcuts.insert(ShortcutEntity(name = name, iconKey = iconKey, colorKey = colorKey, folderId = folderId))

    suspend fun updateAppearance(id: Long, name: String, iconKey: String, colorKey: String) =
        shortcuts.updateAppearance(id, name, iconKey, colorKey, System.currentTimeMillis())

    /** Lets one shortcut run another by name ("Run shortcut" block). */
    val library: ShortcutLibrary = object : ShortcutLibrary {
        override suspend fun find(name: String): List<ActionInstance>? =
            shortcuts.findByName(name)?.let { ActionsJson.decode(it.actionsJson) }

        override suspend fun names(): List<String> = shortcuts.names()
        override suspend fun idOf(name: String): Long? = shortcuts.findByName(name)?.id
    }

    fun observeShortcut(id: Long): Flow<ShortcutEntity?> = shortcuts.observe(id)

    suspend fun saveActions(id: Long, actions: List<ActionInstance>) =
        shortcuts.updateActions(id, ActionsJson.encode(actions), actions.size, System.currentTimeMillis())

    suspend fun duplicate(s: ShortcutEntity, copyName: String) {
        val now = System.currentTimeMillis()
        shortcuts.insert(s.copy(id = 0, name = copyName, createdAt = now, updatedAt = now))
    }

    suspend fun deleteShortcut(s: ShortcutEntity) = shortcuts.delete(s)
    suspend fun setFavorite(id: Long, favorite: Boolean) = shortcuts.setFavorite(id, favorite)
    suspend fun moveToFolder(id: Long, folderId: Long?) = shortcuts.moveToFolder(id, folderId)

    // ---- automations
    val allAutomations: Flow<List<AutomationEntity>> = automations.observeAll()
    suspend fun enabledAutomations(): List<AutomationEntity> = automations.enabled()
    suspend fun automation(id: Long): AutomationEntity? = automations.find(id)
    suspend fun automationsOfType(type: String): List<AutomationEntity> = automations.ofType(type)
    suspend fun saveAutomation(a: AutomationEntity): Long = if (a.id == 0L) automations.insert(a) else { automations.update(a); a.id }
    suspend fun deleteAutomation(id: Long) = automations.delete(id)
    suspend fun setAutomationEnabled(id: Long, enabled: Boolean) = automations.setEnabled(id, enabled)

    suspend fun createFolder(name: String): Long =
        folders.insert(FolderEntity(name = name, position = folders.nextPosition()))

    suspend fun renameFolder(id: Long, name: String) = folders.rename(id, name)
    suspend fun deleteFolder(id: Long) = folders.delete(id)
}
