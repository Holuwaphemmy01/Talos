package com.talos.guardian.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.talos.guardian.data.local.AppDatabase
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.WriteBatch

class SyncLogsWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val dao = db.logDao()
            val firestore = FirebaseFirestore.getInstance()

            // Fetch pending logs (Limit 500 per batch to respect Firestore limits)
            val pendingLogs = dao.getAllLogs().take(500)
            if (pendingLogs.isEmpty()) {
                return Result.success()
            }

            Log.d("TalosSync", "Found ${pendingLogs.size} pending logs to sync.")

            val batch = firestore.batch()
            val logsToDelete = mutableListOf<com.talos.guardian.data.local.ActivityLogEntity>()

            for (logEntity in pendingLogs) {
                try {
                    val domainLog = logEntity.toDomainModel()
                    val childId = logEntity.childId

                    val docRef = firestore.collection("childs")
                        .document(childId)
                        .collection("logs")
                        .document()
                    
                    val logWithId = domainLog.copy(id = docRef.id)
                    batch.set(docRef, logWithId)
                    
                    logsToDelete.add(logEntity)

                } catch (e: Exception) {
                    Log.e("TalosSync", "Skipping corrupt log ID: ${logEntity.id}", e)
                }
            }

            if (logsToDelete.isNotEmpty()) {
                // Execute Batch Write (Atomic)
                batch.commit().await()
                
                // If batch succeeds, delete locally
                dao.deleteLogs(logsToDelete)
                Log.d("TalosSync", "Successfully synced and deleted ${logsToDelete.size} logs.")
            }

            if (pendingLogs.size == 500) {
                // If we hit the limit, there might be more. Return Retry to trigger again soon?
                // Or just Success and let next schedule handle it.
                Result.success() 
            } else {
                Result.success()
            }

        } catch (e: Exception) {
            Log.e("TalosSync", "Sync worker failed", e)
            Result.retry()
        }
    }
}
