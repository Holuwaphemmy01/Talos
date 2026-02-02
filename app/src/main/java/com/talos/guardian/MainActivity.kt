package com.talos.guardian

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

import android.app.AppOpsManager
import android.os.Process
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.talos.guardian.data.AuthRepository
import com.talos.guardian.ui.auth.LoginActivity
import com.talos.guardian.ui.dashboard.ParentDashboardActivity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import com.talos.guardian.receivers.TalosAdminReceiver

class MainActivity : ComponentActivity() {

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startTalosService(result.resultCode, result.data!!)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Check if permission is granted after returning from settings
        if (Settings.canDrawOverlays(this)) {
            checkUsageStatsPermissionAndStart()
        }
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasUsageStatsPermission()) {
            requestMediaProjection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and Request Device Admin
        checkDeviceAdmin()
        
        // Check for existing session
        if (AuthRepository.isUserLoggedIn()) {
            // TODO: Determine if user is Parent or Child (Need to store role in Prefs or Firestore)
            // For now, assume Parent if logged in, or we can route to dashboard to check
            startActivity(Intent(this, ParentDashboardActivity::class.java))
            finish()
            return
        }

        setContent {
            TalosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Temporary: We will have a "Role Selection" screen here later
                    // For now, let's provide buttons for "Child Mode" and "Parent Login"
                    Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text("Talos Guardian", style = MaterialTheme.typography.headlineLarge)
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(onClick = { 
                            startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                        }) {
                            Text("Parent Login / Setup")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(onClick = { checkOverlayPermissionAndStart() }) {
                            Text("Activate Child Shield")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(onClick = {
                            startActivity(Intent(this@MainActivity, com.talos.guardian.ui.pairing.ChildPairingActivity::class.java))
                        }) {
                            Text("Link to Parent (Child)")
                        }
                    }
                }
            }
        }
    }

    private fun checkOverlayPermissionAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            checkUsageStatsPermissionAndStart()
        }
    }

    private fun checkUsageStatsPermissionAndStart() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsPermissionLauncher.launch(intent)
        } else {
            requestMediaProjection()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestMediaProjection() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }


    private fun startTalosService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, TalosService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("RESULT_DATA", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, TalosAdminReceiver::class.java)

        if (!dpm.isAdminActive(componentName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Talos Guardian requires Device Admin to prevent unauthorized removal and ensure child safety.")
            startActivity(intent)
        }
    }
}

@Composable
fun MainScreen(
    onNavigateToChild: () -> Unit,
    onNavigateToParent: () -> Unit,
    onNavigateToPairing: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Talos Guardian\nShield is Ready.",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNavigateToChild) {
                Text(text = "Activate Shield")
            }
        }
    }
}

@Composable
fun TalosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}
