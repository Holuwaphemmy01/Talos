package com.talos.guardian.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talos.guardian.data.AuthRepository
import com.talos.guardian.ui.dashboard.ParentDashboardActivity
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AuthScreen()
            }
        }
    }
}

class AuthViewModel : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    fun login(context: Context, onSuccess: () -> Unit) {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            isLoading = true
            val success = AuthRepository.login(email, password)
            isLoading = false
            
            if (success) {
                onSuccess()
            } else {
                Toast.makeText(context, "Login failed. Check credentials.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun register(context: Context, onSuccess: () -> Unit) {
        if (email.isBlank() || password.length < 6) {
            Toast.makeText(context, "Password must be 6+ chars", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            isLoading = true
            val success = AuthRepository.register(email, password, "PARENT")
            isLoading = false
            
            if (success) {
                onSuccess()
            } else {
                Toast.makeText(context, "Registration failed.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun AuthScreen(viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isLoginMode) "Welcome Back, Guardian" else "Create Parent Account",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.email,
            onValueChange = { viewModel.email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (isLoginMode) {
                        viewModel.login(context) {
                            context.startActivity(Intent(context, ParentDashboardActivity::class.java))
                        }
                    } else {
                        viewModel.register(context) {
                            context.startActivity(Intent(context, ParentDashboardActivity::class.java))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isLoginMode) "Sign In" else "Create Account")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(if (isLoginMode) "Need an account? Sign Up" else "Already have an account? Sign In")
        }
    }
}

