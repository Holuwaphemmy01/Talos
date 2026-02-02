package com.talos.guardian.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.talos.guardian.BuildConfig
import com.talos.guardian.data.ActivityLog
import com.talos.guardian.data.ChildRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class TalosNotificationService : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val geminiModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    // List of apps we care about (Social Media, Messaging)
    private val monitoredPackages = setOf(
        "com.whatsapp",
        "com.instagram.android",
        "com.snapchat.android",
        "com.facebook.orca", // Messenger
        "com.discord",
        "com.twitter.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.google.android.apps.messaging" // SMS
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        
        // 1. Filter: Ignore apps we don't care about
        if (!monitoredPackages.contains(packageName)) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // 2. Filter: Ignore empty or system messages
        if (text.isBlank() || isSystemMessage(text)) return

        Log.d("TalosNotif", "Intercepted from $packageName: $title - $text")

        // 3. Analyze with Gemini (Text Mode)
        analyzeNotificationText(packageName, title, text)
    }

    private fun isSystemMessage(text: String): Boolean {
        // Simple heuristic to ignore non-chat messages
        return text.contains("new messages") || 
               text.contains("missed call") || 
               text.contains("listening to") ||
               text.contains("battery")
    }

    private fun analyzeNotificationText(appName: String, sender: String, message: String) {
        scope.launch {
            try {
                val prompt = """
                    Analyze this message for: Cyberbullying, Predatory Grooming, Sexual Harassment, or Threats.
                    Sender: $sender
                    Message: "$message"
                    
                    Return ONLY valid JSON:
                    {
                        "isSafe": boolean,
                        "category": "BULLYING" | "GROOMING" | "THREAT" | "SAFE" | "OTHER",
                        "confidence": float (0.0 to 1.0),
                        "reasoning": "short explanation"
                    }
                """.trimIndent()

                val response = geminiModel.generateContent(prompt)
                val json = response.text?.replace("```json", "")?.replace("```", "")?.trim()

                if (json != null) {
                    val result = JSONObject(json)
                    val isSafe = result.optBoolean("isSafe", true)

                    if (!isSafe) {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                        val log = ActivityLog(
                            timestamp = System.currentTimeMillis(),
                            appName = appName,
                            riskCategory = result.optString("category", "UNSAFE"),
                            aiReasoning = "Message from $sender: " + result.optString("reasoning", "Harmful text detected")
                        )
                        ChildRepository.logDetectionEvent(uid, log)
                        Log.w("TalosNotif", "Harmful text detected: ${log.aiReasoning}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
