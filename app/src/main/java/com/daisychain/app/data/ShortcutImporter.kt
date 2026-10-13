package com.daisychain.app.data

import com.daisychain.engine.apple.ImportError
import com.daisychain.engine.apple.ShortcutFile
import com.daisychain.engine.spec.LocalizedText

/** What happened when a `.shortcut` file was imported. */
sealed interface ImportResult {
    data class Success(val id: Long, val name: String, val blockCount: Int, val warnings: List<LocalizedText>) : ImportResult
    data class Failure(val reason: ImportError.Reason) : ImportResult
}

/** Reads a `.shortcut` file and adds it to the library. */
class ShortcutImporter(private val repo: ShortcutRepository) {
    suspend fun import(bytes: ByteArray, fileName: String, folderId: Long? = null): ImportResult {
        val imported = try {
            ShortcutFile.import(bytes, fileName)
        } catch (e: ImportError) {
            return ImportResult.Failure(e.reason)
        }
        val id = repo.createShortcut(imported.name, imported.iconKey, imported.colorKey, folderId)
        repo.saveActions(id, imported.actions)
        return ImportResult.Success(id, imported.name, imported.actions.size, imported.warnings)
    }
}
