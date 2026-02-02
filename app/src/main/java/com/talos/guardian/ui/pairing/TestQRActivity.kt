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
import androidx.compose.ui.unit.dp
import com.talos.guardian.utils.QRCodeGenerator

class TestQRActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    var error by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        try {
                            bitmap = QRCodeGenerator.generateQRCode("TEST_QR_CODE")
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("QR Code Test")
                        Spacer(modifier = Modifier.height(20.dp))
                        if (bitmap != null) {
                            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Test QR")
                        } else if (error != null) {
                            Text("Error: $error")
                        } else {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}