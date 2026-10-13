package com.daisychain.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** "When [triggerType] happens, run the shortcut [shortcutId]." */
@Entity(
    tableName = "automations",
    foreignKeys = [
        ForeignKey(
            entity = ShortcutEntity::class,
            parentColumns = ["id"],
            childColumns = ["shortcutId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("shortcutId")],
)
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** See `TriggerType`. */
    val triggerType: String,
    /** Settings of the trigger as JSON (time of day, battery level, network name...). */
    val param: String,
    val shortcutId: Long,
    /** Ask with a notification before running, instead of running on its own. */
    val askFirst: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations WHERE enabled = 1")
    suspend fun enabled(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun find(id: Long): AutomationEntity?

    @Query("SELECT * FROM automations WHERE triggerType = :type")
    suspend fun ofType(type: String): List<AutomationEntity>

    @Insert
    suspend fun insert(automation: AutomationEntity): Long

    @Update
    suspend fun update(automation: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
