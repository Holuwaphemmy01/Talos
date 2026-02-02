package com.talos.guardian

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.talos.guardian.data.ActivityLog
import com.talos.guardian.data.ChildRepository
import com.talos.guardian.workers.SyncLogsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.util.Log

class TalosService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "talos_guardian_channel"
        const val CHANNEL_NAME = "Talos Active Protection"
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    // Screen State Management
    private var screenReceiver: BroadcastReceiver? = null
    private var screenDensity = 0
    private var screenWidth = 720
    private var screenHeight = 1280
    
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    private var isProcessing = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private val geminiModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // Initialize Room DB access via Repository
        ChildRepository.initialize(applicationContext)
        
        // Schedule initial sync check
        scheduleLogSync()
    }

    private fun scheduleLogSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncLogsWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueue(syncRequest)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startProjection(resultCode, resultData)
        }
        
        return START_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        // 1. Get Screen Metrics (Scaled down for performance/Gemini)
        // We use 720p width (approx) to save battery and token costs
        val metrics = resources.displayMetrics
        val density = metrics.densityDpi
        val width = 720
        val height = (metrics.heightPixels * width) / metrics.widthPixels // maintain aspect ratio

        // 2. Setup ImageReader (maxImages=1 for latest frame only)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        imageReader?.setOnImageAvailableListener({ reader ->
            if (isProcessing) {
                // Throttle: Drop frame if we are already busy
                reader.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            processImage(image)
        }, null)

        // 3. Create Virtual Display
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "TalosScreen",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun processImage(image: Image) {
        isProcessing = true
        scope.launch {
            try {
                // 1. Convert to Bitmap
                val bitmap = imageToBitmap(image)
                image.close() // Close ASAP to free buffer

                if (bitmap != null) {
                    // 2. Send to Gemini
                    analyzeFrame(bitmap)
                    
                    bitmap.recycle() // Clean up bitmap memory
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Throttle: Wait 5 seconds before next frame
                delay(5000) 
                isProcessing = false
            }
        }
    }

    private suspend fun analyzeFrame(bitmap: Bitmap) {
        try {
            val prompt = """
                You are a real-time safety guard. This is a current frame from a child's screen video stream.
                Analyze it for: Nudity, Pornography, Gore, or Sexual Violence.
                Return ONLY valid JSON:
                {
                    "isSafe": boolean,
                    "category": "NUDITY" | "VIOLENCE" | "SAFE" | "OTHER",
                    "confidence": float (0.0 to 1.0),
                    "reasoning": "short explanation of what was detected"
                }
            """.trimIndent()

            val response = geminiModel.generateContent(
                content {
                    image(bitmap)
                    text(prompt)
                }
            )
            
            val json = response.text?.replace("```json", "")?.replace("```", "")?.trim()
            if (json != null) {
                val result = JSONObject(json)
                val isSafe = result.optBoolean("isSafe", true)
                
                withContext(Dispatchers.Main) {
                    if (!isSafe) {
                        showOverlay()
                    } else {
                        hideOverlay()
                    }
                }

                if (!isSafe) {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
                    val log = ActivityLog(
                        timestamp = System.currentTimeMillis(),
                        appName = getForegroundApp(),
                        riskCategory = result.optString("category", "UNSAFE"),
                        aiReasoning = result.optString("reasoning", "Harmful content detected")
                    )
                    ChildRepository.logDetectionEvent(uid, log)
                }
            }
            
        } catch (e: Exception) {
            // Safety Block -> Show Overlay if analysis fails insecurely? 
            // Better to fail open (safe) or fail closed (safe)?
            // For now, let's log it.
            e.printStackTrace()
        }
    }

    private fun getForegroundApp(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time)
        
        return if (stats != null && stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            sortedStats[0].packageName
        } else {
            "Unknown"
        }
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        if (overlayView == null) {
            overlayView = createOverlayView()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Fill entire screen including status bar
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager?.addView(overlayView, params)
        isOverlayShowing = true
    }

    private fun hideOverlay() {
        if (!isOverlayShowing) return
        
        windowManager?.removeView(overlayView)
        isOverlayShowing = false
    }

    private fun createOverlayView(): View {
        val layout = FrameLayout(this)
        layout.setBackgroundColor(0xDD000000.toInt()) // 85% Black Overlay (Frosted Glass simulation)

        val message = TextView(this).apply {
            text = "⚠️ HARMFUL CONTENT DETECTED ⚠️\n\nTalos Guardian has blocked this screen."
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        layout.addView(message, params)
        return layout
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        
        // Create bitmap
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running background analysis for child safety"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Talos Guardian Active")
            .setContentText("Monitoring for harmful content...")
            .setSmallIcon(android.R.drawable.ic_secure) // TODO: Use real app icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver)
            screenReceiver = null
        }
        if (isOverlayShowing) {
            windowManager?.removeView(overlayView)
        }
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }
}
