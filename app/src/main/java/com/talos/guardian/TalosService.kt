package com.talos.guardian

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TalosService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is where The Oracle will live.
        // TODO: Initialize MediaProjection and Gemini Client
        return START_STICKY
    }
}
