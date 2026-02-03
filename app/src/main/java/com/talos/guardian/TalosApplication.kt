package com.talos.guardian

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class TalosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Global Exception Handler to prevent crashes
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TalosCrash", "Uncaught Exception: ${throwable.message}", throwable)
            // Ideally, log to Firebase Crashlytics here
            
            // Prevent app from dying immediately if possible (though for uncaught exceptions, it usually must die)
            // But we can restart it or show a toast if we were in an Activity context (which we aren't here directly)
            
            // For now, let's just log and let the OS handle it, BUT we can try to suppress known issues if needed.
            oldHandler?.uncaughtException(thread, throwable)
        }

        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("TalosInit", "Firebase Init Failed", e)
        }
    }
}
