package com.talos.guardian

import android.app.Application
import com.google.firebase.FirebaseApp

class TalosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
    }
}
