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
                // Correct Path: childs/{childId}/logs
                val logsSnapshot = db.collection("childs")
                    .document(childId)
                    .collection("logs")
                    .whereGreaterThanOrEqualTo("timestamp", oneWeekAgo)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()

                if (logsSnapshot.isEmpty) {
                    Log.d(TAG, "No logs found for child $childId in the last week.")
                    continue
                }

                // 3. Prepare data for AI (Filter for BLUR events)
                val logsText = StringBuilder("Activity Logs for $childName (Last 7 Days):\n")
                var interventionCount = 0
                
                logsSnapshot.forEach { logDoc ->
                    val risk = logDoc.getString("riskCategory") ?: "SAFE"
                    
                    // Filter: Only include UNSAFE/BLUR events for the report context
                    if (risk != "SAFE") {
                        interventionCount++
                        val appName = logDoc.getString("appName") ?: "Unknown App"
                        val reasoning = logDoc.getString("aiReasoning") ?: "No details"
                        val time = java.text.DateFormat.getDateTimeInstance().format(logDoc.getLong("timestamp") ?: 0L)
                        
                        logsText.append("- [$time] App: $appName, Risk: $risk, Context: $reasoning\n")
                    }
                }
                
                if (interventionCount == 0) {
                     logsText.append("No high-risk interventions occurred this week. The device was used safely.\n")
                }

                // 4. Generate Report via Gemini (The Scholar Persona)
                val prompt = """
                    You are "The Scholar," a compassionate Digital Safety Counselor for the Talos Guardian app.
                    Your goal is to help parents understand their child's digital week without causing unnecessary panic.
                    
                    TASK:
                    Analyze the following activity logs for a child named "$childName". These logs represent "Blur" events where our AI intervened to block unsafe content.
                    
                    LOGS (Interventions):
                    $logsText
                    
                    INSTRUCTIONS:
                    1. **Analyze Context**: Understand *why* the block happened. Was it accidental? Curiosity? Malicious?
                    2. **Tone**: Supportive, educational, and non-alarmist. Focus on *guidance*.
                    3. **Safety Protocol**: This is a safety analysis for a guardian. Do not refuse to analyze the "Risk" categories. Treat them as data points to explain.
                    
                    OUTPUT FORMAT (Markdown):
                    ### 🛡️ Weekly Snapshot
                    [A 2-sentence summary of the week's safety status. E.g., "Safe week with no incidents" or "3 interventions occurred related to..."]

                    ### ⚠️ Key Interventions
                    [If logs exist, explain the *context* of the blocks. Don't be graphic, but be clear. E.g., "We blocked an adult site accessed via Chrome."]
                    [If no logs, say: "No high-risk content was detected this week."]

                    ### 💬 Counselor's Guidance
                    [Provide 1-2 specific conversation starters or advice based on the detected risks. E.g., "It might be a good time to talk about safe search settings."]
                """.trimIndent()

                var reportContent = "Safety Report Unavailable"
                try {
                    val response = generativeModel.generateContent(prompt)
                    reportContent = response.text ?: "The Scholar could not generate a summary at this time."
                } catch (e: Exception) {
                    Log.e(TAG, "Gemini Generation Failed", e)
                    reportContent = "⚠️ AI Analysis Unavailable. \n\nRaw Log Count: $interventionCount interventions detected."
                }

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