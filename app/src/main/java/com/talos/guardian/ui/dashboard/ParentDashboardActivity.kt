package com.talos.guardian.ui.dashboard

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
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
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.talos.guardian.data.ChildStatus
import com.talos.guardian.data.local.PinRepository
import com.talos.guardian.ui.pairing.ParentPairingActivity
import com.talos.guardian.ui.security.PinLockScreen
import com.talos.guardian.ui.security.PinMode
import com.talos.guardian.workers.WeeklyReportWorker
import com.talos.guardian.ui.theme.TalosTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.text.font.FontWeight

class ParentDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser

            // Schedule Weekly Report Worker
            try {
                scheduleWeeklyReport()
            } catch (e: Throwable) {
                android.util.Log.e("TalosCrash", "Worker Schedule Failed", e)
            }
            
            setContent {
                TalosTheme {
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
        } catch (e: Throwable) {
            android.util.Log.e("TalosCrash", "Dashboard Fatal Crash", e)
            android.widget.Toast.makeText(this, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            setContent {
                 MaterialTheme {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("Fatal Error: ${e.localizedMessage}")
                     }
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
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically() + fadeIn()
                    ) {
                        LiveStatusCard(parentId = currentUser.uid)
                    }
                } else {
                    Text(
                        "Please log in to see child status.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                
                // Quick Actions
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Quick Actions",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DashboardActionCard(
                                icon = Icons.Default.List,
                                label = "Reports",
                                onClick = { onNavigateToReports(currentUser?.uid ?: "") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            DashboardActionCard(
                                icon = Icons.Default.Lock,
                                label = "Settings",
                                onClick = { showPinScreen = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        DashboardActionCard(
                            icon = Icons.Default.Add,
                            label = "Link New Device",
                            onClick = onLinkNewDevice,
                            modifier = Modifier.fillMaxWidth(),
                            isPrimary = true
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp),
                tint = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun LiveStatusCard(parentId: String) {
    var childStatus by remember { mutableStateOf(ChildStatus.OFFLINE) }
    var childName by remember { mutableStateOf("Child Device") }

    LaunchedEffect(parentId) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("TalosCrash", "Firestore Init Failed", e)
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
