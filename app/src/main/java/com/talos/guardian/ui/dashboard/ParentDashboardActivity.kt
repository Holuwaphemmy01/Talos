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