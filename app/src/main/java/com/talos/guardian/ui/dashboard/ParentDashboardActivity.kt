package com.talos.guardian.ui.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talos.guardian.ui.pairing.ParentPairingActivity

class ParentDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Parent Dashboard\n(Coming Soon)", style = MaterialTheme.typography.headlineMedium)
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        androidx.compose.material3.Button(onClick = {
                            startActivity(android.content.Intent(this@ParentDashboardActivity, ParentPairingActivity::class.java))
                        }) {
                            Text("Link New Device (QR)")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        androidx.compose.material3.Button(onClick = {
                            com.talos.guardian.data.AuthRepository.logout()
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
