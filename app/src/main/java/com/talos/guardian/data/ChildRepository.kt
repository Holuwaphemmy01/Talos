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
    private var appContext: Context? = null
    private const val PREF_NAME = "talos_child_prefs"
    private const val KEY_PARENT_UID = "paired_parent_uid"
    private const val KEY_CHILD_UID = "child_uid"
    private const val KEY_CHILD_NAME = "child_name"

    fun initialize(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.getDatabase(context)
    }
    
    /**
     * Links this device to a Parent UID.
     * Generates a unique Child UID if one doesn't exist.
     * Saves to Firestore and Local Prefs.
     */
    suspend fun linkChildToParent(parentUid: String, childName: String): Boolean {
        val context = appContext ?: return false
        
        try {
            // 1. Get or Create Child UID (UUID for now, since no Auth)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            var childUid = prefs.getString(KEY_CHILD_UID, null)
            
            if (childUid == null) {
                childUid = java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_CHILD_UID, childUid).apply()
            }
            
            // 2. Create ChildUser Object
            val childUser = ChildUser(
                uid = childUid,
                deviceID = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown_id",
                pairedParentID = parentUid,
                deviceName = childName,
                currentStatus = ChildStatus.ACTIVE
            )
            
            // 3. Save to Firestore
            db.collection(COLLECTION_CHILDREN).document(childUid).set(childUser).await()
            
            // 4. Save Parent UID Locally (Mark as Paired)
            prefs.edit()
                .putString(KEY_PARENT_UID, parentUid)
                .putString(KEY_CHILD_NAME, childName)
                .apply()
                
            return true
        } catch (e: Exception) {
            Log.e("TalosChild", "Linking failed", e)
            return false
        }
    }

    /**
     * Checks if this device is already paired.
     */
    fun isDevicePaired(): Boolean {
        val context = appContext ?: return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_PARENT_UID, null) != null
    }

    /**
     * Gets the current Child ID.
     */
    fun getCurrentChildId(): String? {
        val context = appContext ?: return null
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CHILD_UID, null)
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
     * Logs a detection event.
     * STRATEGY: Always save locally first (Buffer), then trigger background sync.
     * This ensures 100% data persistence even if offline.
     */
    suspend fun logDetectionEvent(childID: String, log: ActivityLog) {
        try {
            // 1. Always Save to Local Room DB First (The Buffer)
            val entity = ActivityLogEntity.fromDomainModel(log, childID)
            database?.logDao()?.insertLog(entity)
            Log.d("TalosChild", "Log saved locally to buffer.")

            // 2. Trigger Background Sync (WorkManager)
            // We use OneTimeWorkRequest to attempt upload immediately if possible,
            // or queue it if offline.
            scheduleSync()
            
        } catch (e: Exception) {
            Log.e("TalosChild", "Critical Error: Failed to save log locally", e)
        }
    }

    private fun scheduleSync() {
        val context = appContext ?: return
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.talos.guardian.workers.SyncLogsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
            .build()

        androidx.work.WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "SyncLogsWork",
                androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                syncRequest
            )
    }
}
