package com.talos.guardian.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.talos.guardian.data.ChildRepository
import com.talos.guardian.utils.QrCodeAnalyzer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.ui.text.input.TextFieldValue

class ChildPairingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Repository (ensure context is set)
        ChildRepository.initialize(applicationContext)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ChildPairingScreen(
                        onPairingSuccess = { parentUid, childName ->
                            lifecycleScope.launch {
                                val success = ChildRepository.linkChildToParent(parentUid, childName)
                                if (success) {
                                    Toast.makeText(this@ChildPairingActivity, "Successfully Linked to Parent!", Toast.LENGTH_LONG).show()
                                    // Navigate to Main Screen (which will now show "Shield Ready")
                                    finish()
                                } else {
                                    Toast.makeText(this@ChildPairingActivity, "Linking Failed. Check Internet.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChildPairingScreen(onPairingSuccess: (String, String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State for Name Input
    var childName by remember { mutableStateOf(TextFieldValue("My Device")) }
    var isNameConfirmed by remember { mutableStateOf(false) }

    // State for Camera Permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // STEP 1: Name Input Screen
    if (!isNameConfirmed) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = "Name This Device",
                    style = MaterialTheme.typography.headlineSmall
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = childName,
                    onValueChange = { childName = it },
                    label = { Text("Device Name (e.g. John's Phone)") }
                )
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = { 
                        if (childName.text.isNotBlank()) {
                            isNameConfirmed = true
                        } else {
                            Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Next: Scan QR Code")
                }
            }
        }
        return
    }

    // STEP 2: QR Scanner
    if (hasCameraPermission) {
        var isScanning by remember { mutableStateOf(true) }
        
        Box(modifier = Modifier.fillMaxSize()) {
            if (isScanning) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()

                            imageAnalysis.setAnalyzer(
                                Executors.newSingleThreadExecutor(),
                                QrCodeAnalyzer { parentUid ->
                                    isScanning = false
                                    // Trigger Success Callback on Main Thread
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        onPairingSuccess(parentUid, childName.text)
                                    }
                                    cameraProvider.unbindAll() // Stop camera immediately
                                }
                            )

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "Scan Parent's QR Code",
                        style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            } else {
                 Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Linking to Parent...",
                        modifier = Modifier.padding(top = 48.dp)
                    )
                }
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Camera permission is required to scan QR code.")
        }
    }
}
