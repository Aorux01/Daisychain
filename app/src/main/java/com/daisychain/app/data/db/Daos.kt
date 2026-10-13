package com.daisychain.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ShortcutEntity>>

    @Insert
    suspend fun insert(shortcut: ShortcutEntity): Long

    @Query("SELECT * FROM shortcuts WHERE id = :id")
    fun observe(id: Long): Flow<ShortcutEntity?>

    @Query("SELECT name FROM shortcuts ORDER BY name COLLATE NOCASE")
    suspend fun names(): List<String>

    @Query("SELECT * FROM shortcuts WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ShortcutEntity?

    @Query("UPDATE shortcuts SET name = :name, iconKey = :iconKey, colorKey = :colorKey, updatedAt = :now WHERE id = :id")
    suspend fun updateAppearance(id: Long, name: String, iconKey: String, colorKey: String, now: Long)

    @Query("UPDATE shortcuts SET actionsJson = :json, actionCount = :count, updatedAt = :now WHERE id = :id")
    suspend fun updateActions(id: Long, json: String, count: Int, now: Long)

    @Delete
    suspend fun delete(shortcut: ShortcutEntity)

    @Query("UPDATE shortcuts SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE shortcuts SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY position, id")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM folders")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}
