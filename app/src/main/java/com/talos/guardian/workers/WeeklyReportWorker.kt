package com.talos.guardian.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.talos.guardian.BuildConfig
import com.talos.guardian.data.WeeklyReport
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class WeeklyReportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Initialize Gemini 3 Flash
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting Weekly Report Generation Worker")
        
        val parentId = auth.currentUser?.uid
        if (parentId == null) {
            Log.e(TAG, "No parent logged in, skipping report generation")
            return Result.failure()
        }

        try {
            // 1. Find all children linked to this parent
            val childrenSnapshot = db.collection("child_users")
                .whereEqualTo("pairedParentID", parentId)
                .get()
                .await()

            if (childrenSnapshot.isEmpty) {
                Log.d(TAG, "No children found for parent $parentId")
                return Result.success()
            }

            val oneWeekAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
            }.timeInMillis

            for (childDoc in childrenSnapshot.documents) {
                val childId = childDoc.id
                val childName = childDoc.getString("deviceName") ?: "Child Device"

                // 2. Fetch logs for this child from the last week
                val logsSnapshot = db.collection("activity_logs")
                    .whereEqualTo("childUid", childId)
                    .whereGreaterThanOrEqualTo("timestamp", oneWeekAgo)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                if (logsSnapshot.isEmpty) {
                    Log.d(TAG, "No logs found for child $childId in the last week.")
                    continue
                }

                // 3. Prepare data for AI
                val logsText = StringBuilder("Activity Logs for $childName (Last 7 Days):\n")
                logsSnapshot.forEach { logDoc ->
                    val appName = logDoc.getString("appName") ?: "Unknown App"
                    val event = logDoc.getString("eventDescription") ?: ""
                    val risk = logDoc.getString("riskLevel") ?: "LOW"
                    val time = java.text.DateFormat.getDateTimeInstance().format(logDoc.getLong("timestamp") ?: 0L)
                    logsText.append("- [$time] App: $appName, Event: $event, Risk: $risk\n")
                }

                // 4. Generate Report via Gemini
                val prompt = """
                    You are an expert child safety analyst for the Talos Guardian app.
                    Analyze the following activity logs for a child named "$childName".
                    
                    Logs:
                    $logsText

                    Please provide a "Weekly Safety Report" for the parent.
                    Structure:
                    1. **Overview**: General screen time and usage patterns.
                    2. **Risk Analysis**: Highlight any high-risk events (pornography, violence, sexting) detected.
                    3. **Positive Behavior**: Acknowledge safe usage.
                    4. **Recommendations**: 1-2 actionable tips for the parent based on this week's activity.
                    
                    Tone: Professional, supportive, vigilant, but not alarmist.
                    Format: Markdown.
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val reportContent = response.text ?: "Unable to generate report content."

                // 5. Save Report to Firestore
                val reportRef = db.collection("weekly_reports").document()
                val report = WeeklyReport(
                    id = reportRef.id,
                    parentId = parentId,
                    childId = childId,
                    childName = childName,
                    weekStarting = oneWeekAgo,
                    generatedAt = System.currentTimeMillis(),
                    content = reportContent,
                    isRead = false
                )
                
                reportRef.set(report).await()
                Log.i(TAG, "Report generated and saved for child $childId")
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating weekly report", e)
            return Result.retry()
        }
    }

    companion object {
        private const val TAG = "WeeklyReportWorker"
    }
}