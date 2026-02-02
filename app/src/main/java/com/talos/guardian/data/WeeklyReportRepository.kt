package com.talos.guardian.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyReportRepository @Inject constructor() {
    private val db = FirebaseFirestore.getInstance()
    private val REPORTS_COLLECTION = "weekly_reports"

    suspend fun getReportsForParent(parentId: String): List<WeeklyReport> {
        return try {
            val snapshot = db.collection(REPORTS_COLLECTION)
                .whereEqualTo("parentId", parentId)
                .orderBy("generatedAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(WeeklyReport::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun markReportAsRead(reportId: String) {
        try {
            db.collection(REPORTS_COLLECTION).document(reportId)
                .update("isRead", true)
                .await()
        } catch (e: Exception) {
            // Log error
        }
    }
}