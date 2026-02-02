package com.talos.guardian.ui.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PinMode {
    CREATE,
    UNLOCK,
    CONFIRM
}

@Composable
fun PinLockScreen(
    mode: PinMode = PinMode.UNLOCK,
    onPinVerified: () -> Unit,
    onPinCreated: (String) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    var pin by remember { mutableStateOf("") }
    var tempPin by remember { mutableStateOf("") } // For confirmation step
    var currentMode by remember { mutableStateOf(mode) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = when (currentMode) {
                PinMode.CREATE -> "Create Security PIN"
                PinMode.CONFIRM -> "Confirm PIN"
                PinMode.UNLOCK -> "Enter PIN"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // PIN Dots
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        if (errorMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        keys.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { key ->
                    KeypadButton(key = key) {
                        errorMessage = ""
                        when (key) {
                            "DEL" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            "" -> {} // Empty placeholder
                            else -> {
                                if (pin.length < 4) {
                                    pin += key
                                    if (pin.length == 4) {
                                        // Auto-submit when full
                                        handlePinSubmit(pin, currentMode, tempPin,
                                            onSuccess = {
                                                if (currentMode == PinMode.CREATE) {
                                                    tempPin = pin
                                                    pin = ""
                                                    currentMode = PinMode.CONFIRM
                                                } else if (currentMode == PinMode.CONFIRM) {
                                                    onPinCreated(pin)
                                                } else {
                                                    onPinVerified()
                                                }
                                            },
                                            onError = { msg ->
                                                errorMessage = msg
                                                pin = ""
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
fun KeypadButton(key: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (key.isNotEmpty()) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(enabled = key.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "DEL") {
            Text("⌫", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(key, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun handlePinSubmit(
    pin: String,
    mode: PinMode,
    tempPin: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    when (mode) {
        PinMode.CREATE -> onSuccess()
        PinMode.CONFIRM -> {
            if (pin == tempPin) onSuccess() else onError("PINs do not match. Try again.")
        }
        PinMode.UNLOCK -> onSuccess() // Validation happens in the parent component via callback or repository check
    }
}
