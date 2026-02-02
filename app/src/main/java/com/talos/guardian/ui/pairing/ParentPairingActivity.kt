package com.talos.guardian.ui.pairing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.talos.guardian.utils.QRCodeGenerator

class ParentPairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ParentPairingScreen(
                        onBackClick = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun ParentPairingScreen(onBackClick: () -> Unit) {
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val parentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "ERROR_NO_USER"

    LaunchedEffect(parentUid) {
        // Generate QR code on a background thread
        try {
             qrBitmap = QRCodeGenerator.generateQRCode(parentUid)
        } catch (e: Exception) {
             e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Link Child Device",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        if (qrBitmap != null) {
            Image(
                bitmap = qrBitmap!!.asImageBitmap(),
                contentDescription = "Pairing QR Code",
                modifier = Modifier.size(250.dp)
            )
        } else {
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Open the Talos Guardian app on your child's device and scan this code to link it to your account.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(onClick = onBackClick) {
            Text("Done")
        }
    }
}
