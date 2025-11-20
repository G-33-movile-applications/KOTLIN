package com.mobile.mymeds.data.reminders

import android.content.Context
import androidx.room.*
import java.util.UUID

// ───────────────── ENTITY ─────────────────

@Entity(tableName = "medication_reminders")
data class MedicationReminderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val medicationName: String,
    val time: String,              // HH:mm
    val recurrence: String,        // "ONCE", "DAILY", "WEEKLY"
    val isActive: Boolean,
    val createdAtMillis: Long? = null,
    val lastFiredAtMillis: Long? = null
)

// ───────────────── DAO ─────────────────

@Dao
interface MedicationRemindersDao {

    @Query("SELECT * FROM medication_reminders ORDER BY time")
    suspend fun getAllOnce(): List<MedicationReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: MedicationReminderEntity)

    @Query("UPDATE medication_reminders SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean)

    @Query("DELETE FROM medication_reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ───────────────── DATABASE ─────────────────

@Database(
    entities = [MedicationReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RemindersDatabase : RoomDatabase() {
    abstract fun medicationRemindersDao(): MedicationRemindersDao

    companion object {
        @Volatile
        private var INSTANCE: RemindersDatabase? = null

        fun getInstance(context: Context): RemindersDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RemindersDatabase::class.java,
                    "reminders_db"
                ).build().also { INSTANCE = it }
            }
    }
}
