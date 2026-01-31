package com.talos.guardian.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.talos.guardian.data.local.AppDatabase
import kotlinx.coroutines.tasks.await

class SyncLogsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.logDao()
            val firestore = FirebaseFirestore.getInstance()

            val pendingLogs = dao.getAllLogs()
            if (pendingLogs.isEmpty()) {
                return Result.success()
            }

            Log.d("TalosSync", "Found ${pendingLogs.size} pending logs to sync.")

            val logsToDelete = mutableListOf<com.talos.guardian.data.local.ActivityLogEntity>()

            for (logEntity in pendingLogs) {
                try {
                    val domainLog = logEntity.toDomainModel()
                    val childId = logEntity.childId

                    // Upload to Firestore
                    // We manually create the path because ChildRepository logic is slightly different
                    val docRef = firestore.collection("childs")
                        .document(childId)
                        .collection("logs")
                        .document()
                    
                    val logWithId = domainLog.copy(id = docRef.id)
                    docRef.set(logWithId).await()

                    // Mark for deletion if successful
                    logsToDelete.add(logEntity)

                } catch (e: Exception) {
                    Log.e("TalosSync", "Failed to sync log ID: ${logEntity.id}", e)
                    // If one fails, we continue trying others, but don't delete the failed one
                }
            }

            // Clean up local DB
            if (logsToDelete.isNotEmpty()) {
                dao.deleteLogs(logsToDelete)
                Log.d("TalosSync", "Successfully synced and deleted ${logsToDelete.size} logs.")
            }

            if (logsToDelete.size == pendingLogs.size) {
                Result.success()
            } else {
                // If some failed, retry later
                Result.retry()
            }

        } catch (e: Exception) {
            Log.e("TalosSync", "Sync worker failed", e)
            Result.retry()
        }
    }
}
