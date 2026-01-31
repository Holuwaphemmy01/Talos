package com.talos.guardian.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogDao {
    @Insert
    suspend fun insertLog(log: ActivityLogEntity)

    @Query("SELECT * FROM activity_logs")
    suspend fun getAllLogs(): List<ActivityLogEntity>

    @Delete
    suspend fun deleteLogs(logs: List<ActivityLogEntity>)
    
    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()
}
