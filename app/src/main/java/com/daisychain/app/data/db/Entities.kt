package com.daisychain.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int = 0,
)

@Entity(
    tableName = "shortcuts",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId")],
)
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconKey: String,
    val colorKey: String,
    val isFavorite: Boolean = false,
    val folderId: Long? = null,
    /** The blocks of the shortcut as JSON (see `ActionsJson`). */
    @ColumnInfo(defaultValue = "'[]'") val actionsJson: String = "[]",
    /** Number of blocks, kept next to [actionsJson] so the library does not have to parse it. */
    val actionCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
