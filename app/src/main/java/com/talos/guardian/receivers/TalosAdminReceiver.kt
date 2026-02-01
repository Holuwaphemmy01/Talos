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
        // Warning message shown to the user when they try to deactivate admin
        return "Disabling Talos Guardian will alert your parent and may lock the device. Are you sure?"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("TalosAdmin", "Device Admin Disabled")
        // TODO: Trigger an emergency alert to the parent via Cloud Functions if possible
    }
}
