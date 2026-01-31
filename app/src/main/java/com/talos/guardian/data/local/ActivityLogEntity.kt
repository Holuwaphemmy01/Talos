package com.talos.guardian.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.talos.guardian.data.ActivityLog

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val appName: String,
    val riskCategory: String,
    val aiReasoning: String,
    val childId: String // We need to know which child this log belongs to when syncing
) {
    fun toDomainModel(): ActivityLog {
        return ActivityLog(
            id = id.toString(), // We use the Room ID temporarily, Firestore will assign a new one
            timestamp = timestamp,
            appName = appName,
            riskCategory = riskCategory,
            aiReasoning = aiReasoning
        )
    }

    companion object {
        fun fromDomainModel(log: ActivityLog, childId: String): ActivityLogEntity {
            return ActivityLogEntity(
                timestamp = log.timestamp,
                appName = log.appName,
                riskCategory = log.riskCategory,
                aiReasoning = log.aiReasoning,
                childId = childId
            )
        }
    }
}
