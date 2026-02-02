package com.talos.guardian.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class TalosAdminReceiver : DeviceAdminReceiver() {
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("TalosAdmin", "Device Admin Enabled")
        Toast.makeText(context, "Talos Guardian Protection Active", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // Balanced Approach: Warn the child but do not lock the device.
        // Trust that the parent-child relationship handles the rest via notification.
        return "This will disable your Talos Safety Shield. Your parent will be notified immediately."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("TalosAdmin", "Device Admin Disabled")
        // TODO: Trigger an emergency alert to the parent via Cloud Functions if possible
    }
}
