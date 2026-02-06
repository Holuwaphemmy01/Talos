package com.talos.guardian

import android.app.Activity
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.media.projection.MediaProjectionManager
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.unit.dp
import com.talos.guardian.receivers.TalosAdminReceiver
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.talos.guardian.ui.theme.TalosTheme
import com.talos.guardian.data.AuthRepository
import com.talos.guardian.ui.dashboard.ParentDashboardActivity
import com.talos.guardian.ui.auth.LoginActivity

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

        checkDeviceAdmin()
        checkNotificationPermission()

        if (AuthRepository.isUserLoggedIn()) {
            lifecycleScope.launch {
                val profile = AuthRepository.getCurrentUserProfile()
                if (profile != null && profile.role == com.talos.guardian.data.UserRole.PARENT) {
                    startActivity(Intent(this@MainActivity, ParentDashboardActivity::class.java))
                    finish()
                } else {
                    AuthRepository.logout()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
            }
            return
        }

        setContent {
            TalosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var isVisible by remember { mutableStateOf(false) }
                    
                    LaunchedEffect(Unit) {
                        isVisible = true
                    }

                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = slideInVertically() + fadeIn()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Logo Placeholder (if we had the resource ID, e.g. R.mipmap.ic_launcher)
                                // Image(painter = painterResource(id = R.mipmap.ic_launcher), contentDescription = "Logo", modifier = Modifier.size(120.dp))
                                
                                Text(
                                    text = "Talos Guardian",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Advanced Child Safety",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                        
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ActionCard(
                                    title = "Parent Dashboard",
                                    subtitle = "Monitor and manage settings",
                                    onClick = { startActivity(Intent(this@MainActivity, LoginActivity::class.java)) }
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                ActionCard(
                                    title = "Activate Child Shield",
                                    subtitle = "Start protection on this device",
                                    onClick = { checkOverlayPermissionAndStart() }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                ActionCard(
                                    title = "Link to Parent",
                                    subtitle = "Connect this device as a Child",
                                    onClick = { startActivity(Intent(this@MainActivity, com.talos.guardian.ui.pairing.ChildPairingActivity::class.java)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    private fun checkNotificationPermission() {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        if (!enabledPackages.contains(packageName)) {
            // Redirect user to Settings
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
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
