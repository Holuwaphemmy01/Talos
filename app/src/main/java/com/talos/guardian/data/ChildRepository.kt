package com.talos.guardian.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

import android.content.Context
import com.talos.guardian.data.local.AppDatabase
import com.talos.guardian.data.local.ActivityLogEntity

object ChildRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION_CHILDREN = "childs" // "childs" collection as requested
    
    // We need context to access Room, so we'll init it lazily or pass it in
    private var database: AppDatabase? = null

    fun initialize(context: Context) {
        database = AppDatabase.getDatabase(context)
    }

    /**
     * Creates or updates a document in the "childs" collection.
     */
    suspend fun registerChildDevice(child: ChildUser): Boolean {
        return try {
            if (child.uid.isBlank()) return false
            db.collection(COLLECTION_CHILDREN).document(child.uid).set(child).await()
            true
        } catch (e: Exception) {
            Log.e("TalosChild", "Failed to register child device", e)
            false
        }
    }

    /**
     * Updates the status (Active/Alert) for a specific child device.
     */
    suspend fun updateChildStatus(childID: String, status: ChildStatus) {
        try {
            db.collection(COLLECTION_CHILDREN).document(childID)
                .update("currentStatus", status)
                .await()
        } catch (e: Exception) {
            Log.e("TalosChild", "Failed to update status", e)
        }
    }
    
    /**
     * Retrieves a ChildUser document by ID.
     */
    suspend fun getChild(childID: String): ChildUser? {
        return try {
            val snapshot = db.collection(COLLECTION_CHILDREN).document(childID).get().await()
            snapshot.toObject(ChildUser::class.java)
        } catch (e: Exception) {
            Log.e("TalosChild", "Failed to get child", e)
            null
        }
    }

    /**
     * Logs a detection event to the child's "logs" sub-collection.
     * Uses a "Try-Cloud, Fail-Local" strategy.
     */
    suspend fun logDetectionEvent(childID: String, log: ActivityLog) {
        try {
            // Attempt Cloud Upload
            val docRef = db.collection(COLLECTION_CHILDREN)
                .document(childID)
                .collection("logs")
                .document()
            
            val logWithId = log.copy(id = docRef.id)
            docRef.set(logWithId).await()
            
        } catch (e: Exception) {
            Log.e("TalosChild", "Cloud upload failed, saving locally", e)
            
            // Fallback: Save to Local Room DB
            try {
                val entity = ActivityLogEntity.fromDomainModel(log, childID)
                database?.logDao()?.insertLog(entity)
            } catch (localError: Exception) {
                Log.e("TalosChild", "CRITICAL: Failed to save log locally", localError)
            }
        }
    }
}
