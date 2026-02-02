package com.talos.guardian.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talos.guardian.data.AuthRepository
import com.talos.guardian.ui.pairing.ParentPairingActivity
import com.google.firebase.auth.FirebaseAuth

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.talos.guardian.workers.WeeklyReportWorker
import java.util.concurrent.TimeUnit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.google.firebase.firestore.FirebaseFirestore
import com.talos.guardian.data.ChildStatus

@Composable
fun LiveStatusCard(parentId: String) {
    var childStatus by remember { mutableStateOf(ChildStatus.OFFLINE) }
    var childName by remember { mutableStateOf("Child Device") }
    var lastActive by remember { mutableStateOf(0L) }

    LaunchedEffect(parentId) {
        val db = FirebaseFirestore.getInstance()
        // Listen to the first child linked to this parent (Simplified for MVP)
        db.collection("childs")
            .whereEqualTo("pairedParentID", parentId)
            .limit(1)
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null || snapshots.isEmpty) return@addSnapshotListener
                
                val doc = snapshots.documents[0]
                childName = doc.getString("deviceName") ?: "Child Device"
                val statusStr = doc.getString("currentStatus") ?: "OFFLINE"
                childStatus = try { ChildStatus.valueOf(statusStr) } catch(e: Exception) { ChildStatus.OFFLINE }
                
                // Check for "Ghost" state (heartbeat check could go here)
            }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when(childStatus) {
                ChildStatus.ACTIVE -> Color(0xFFE8F5E9) // Light Green
                ChildStatus.ALERT -> Color(0xFFFFEBEE)  // Light Red
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Indicator Dot
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Parent Dashboard", style = MaterialTheme.typography.headlineMedium)
                            Text("Welcome, ${currentUser?.email ?: "User"}", style = MaterialTheme.typography.bodyMedium)
                            
                            // Add Live Status Card here
                            if (currentUser != null) {
                                LiveStatusCard(parentId = currentUser.uid)
                            }
                            
                            Spacer(modifier = Modifier.height(32.dp))

                            Button(onClick = {
                                startActivity(android.content.Intent(this@ParentDashboardActivity, ParentPairingActivity::class.java))
                            }) {
                                Text("Link New Device (QR)")
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = { showReports = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("View Weekly Reports")
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(onClick = {
                                AuthRepository.logout()
                                startActivity(android.content.Intent(this@ParentDashboardActivity, com.talos.guardian.MainActivity::class.java))
                                finish()
                            }) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleWeeklyReport() {
        val workRequest = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(10, TimeUnit.SECONDS) // For testing: Start soon after launch. In prod, calculate delay to next Sunday.
            .addTag("weekly_report")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeeklyReportGeneration",
            ExistingPeriodicWorkPolicy.KEEP, // Don't replace if already scheduled
            workRequest
        )
    }
}