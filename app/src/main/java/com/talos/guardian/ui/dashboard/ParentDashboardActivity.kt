package com.talos.guardian.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.talos.guardian.data.ChildStatus
import com.talos.guardian.data.local.PinRepository
import com.talos.guardian.ui.pairing.ParentPairingActivity
import com.talos.guardian.ui.security.PinLockScreen
import com.talos.guardian.ui.security.PinMode
import com.talos.guardian.workers.WeeklyReportWorker
import java.util.concurrent.TimeUnit
import android.content.Intent

class ParentDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = FirebaseAuth.getInstance().currentUser

        // Schedule Weekly Report Worker
        scheduleWeeklyReport()
        
        setContent {
            MaterialTheme {
                var showReports by remember { mutableStateOf(false) }

                if (showReports) {
                    WeeklyReportScreen(
                        parentId = currentUser?.uid ?: "",
                        onBack = { showReports = false }
                    )
                } else {
                    ParentDashboard(
                        onNavigateToReports = { showReports = true },
                        onNavigateToSettings = {
                            // TODO: Navigate to real Settings screen
                        },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            finish()
                        },
                        onLinkNewDevice = {
                            startActivity(Intent(this, ParentPairingActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    private fun scheduleWeeklyReport() {
        val weeklyReportRequest = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(calculateInitialDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyReportGeneration",
            ExistingPeriodicWorkPolicy.KEEP,
            weeklyReportRequest
        )
    }

    private fun calculateInitialDelay(): Long {
        // Simple logic: Next Sunday at 8 AM
        // For MVP, start in 1 minute for testing if needed, or 7 days
        return TimeUnit.MINUTES.toMillis(1) 
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboard(
    onNavigateToReports: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onLinkNewDevice: () -> Unit
) {
    val context = LocalContext.current
    val pinRepo = remember { PinRepository(context) }
    var showPinScreen by remember { mutableStateOf(false) }
    var isPinSet by remember { mutableStateOf(pinRepo.isPinSet()) }
    val currentUser = FirebaseAuth.getInstance().currentUser

    if (showPinScreen) {
        PinLockScreen(
            mode = if (isPinSet) PinMode.UNLOCK else PinMode.CREATE,
            onPinVerified = {
                showPinScreen = false
                onNavigateToSettings() 
            },
            onPinCreated = { newPin ->
                pinRepo.setPin(newPin)
                isPinSet = true
                showPinScreen = false
            },
            onCancel = { showPinScreen = false }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Talos Dashboard") },
                    actions = {
                        IconButton(onClick = { 
                             if (isPinSet) {
                                 showPinScreen = true
                             } else {
                                 showPinScreen = true
                             }
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentUser != null) {
                    LiveStatusCard(parentId = currentUser.uid)
                } else {
                    Text("Please log in to see child status.")
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DashboardActionButton(
                        icon = Icons.Default.Assessment,
                        label = "Reports",
                        onClick = { onNavigateToReports(currentUser?.uid ?: "") }
                    )
                    
                    DashboardActionButton(
                        icon = Icons.Default.Security,
                        label = "Settings",
                         onClick = { 
                             showPinScreen = true
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onLinkNewDevice) {
                    Text("Link New Device (QR)")
                }
            }
        }
    }
}

@Composable
fun DashboardActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .padding(12.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LiveStatusCard(parentId: String) {
    var childStatus by remember { mutableStateOf(ChildStatus.OFFLINE) }
    var childName by remember { mutableStateOf("Child Device") }

    LaunchedEffect(parentId) {
        val db = FirebaseFirestore.getInstance()
        db.collection("childs")
            .whereEqualTo("pairedParentID", parentId)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                
                val doc = snapshots.documents[0]
                childName = doc.getString("deviceName") ?: "Child Device"
                val statusStr = doc.getString("currentStatus") ?: "OFFLINE"
                childStatus = try { ChildStatus.valueOf(statusStr) } catch(e: Exception) { ChildStatus.OFFLINE }
            }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when(childStatus) {
                ChildStatus.ACTIVE -> Color(0xFFE8F5E9)
                ChildStatus.ALERT -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when(childStatus) {
                            ChildStatus.ACTIVE -> Color.Green
                            ChildStatus.ALERT -> Color.Red
                            else -> Color.Gray
                        },
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = childName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when(childStatus) {
                        ChildStatus.ACTIVE -> "Currently Active & Protected"
                        ChildStatus.ALERT -> "⚠️ Safety Alert Detected!"
                        ChildStatus.OFFLINE -> "Offline / Inactive"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (childStatus == ChildStatus.ALERT) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
