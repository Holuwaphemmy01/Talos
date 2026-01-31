package com.talos.guardian.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object ChildRepository {
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private const val COLLECTION_CHILDREN = "childs" // "childs" collection as requested

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
     */
    suspend fun logDetectionEvent(childID: String, log: ActivityLog) {
        try {
            // Create a new document with an auto-generated ID
            val docRef = db.collection(COLLECTION_CHILDREN)
                .document(childID)
                .collection("logs")
                .document()
            
            // Set the ID in the object itself
            val logWithId = log.copy(id = docRef.id)
            
            docRef.set(logWithId).await()
        } catch (e: Exception) {
            Log.e("TalosChild", "Failed to log detection event", e)
        }
    }
}
