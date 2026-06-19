package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "update_history")
data class UpdateHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val versionName: String,
    val androidVersion: String,
    val changelog: String,
    val downloadSize: Long,
    val status: String, // "COMPLETED", "FAILED", "INSTALLING"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface UpdateHistoryDao {
    @Query("SELECT * FROM update_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<UpdateHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: UpdateHistory)

    @Query("DELETE FROM update_history")
    suspend fun clearAll()
}

@Database(entities = [UpdateHistory::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun updateHistoryDao(): UpdateHistoryDao
}
