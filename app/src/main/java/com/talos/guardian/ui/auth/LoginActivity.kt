package com.talos.guardian.ui.auth

import android.app.Activity
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
import com.talos.guardian.ui.theme.TalosTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.talos.guardian.data.AuthRepository
import com.talos.guardian.data.IAuthRepository
import com.talos.guardian.data.UserRole
import com.talos.guardian.ui.dashboard.ParentDashboardActivity

sealed class AuthEffect {
    data class ShowToast(val message: String) : AuthEffect()
    data object NavigateToDashboard : AuthEffect()
}

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TalosTheme {
                AuthScreen()
            }
        }
    }
}

class AuthViewModel(
    private val repository: IAuthRepository = AuthRepository
) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    private val _authEffect = Channel<AuthEffect>()
    val authEffect = _authEffect.receiveAsFlow()

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            sendEffect(AuthEffect.ShowToast("Please fill all fields"))
            return
        }

        viewModelScope.launch {
            try {
                isLoading = true
                val result = repository.login(email, password)
                if (result.isSuccess) {
                    val profile = repository.getCurrentUserProfile()
                    isLoading = false
                    if (profile != null && profile.role == UserRole.PARENT) {
                        sendEffect(AuthEffect.NavigateToDashboard)
                    } else {
                        sendEffect(AuthEffect.ShowToast("Account setup incomplete or not a parent account."))
                    }
                } else {
                    isLoading = false
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Login failed. Check credentials."
                    sendEffect(AuthEffect.ShowToast(errorMsg))
                }
            } catch (e: Throwable) {
                isLoading = false
                sendEffect(AuthEffect.ShowToast("Error: ${e.message}"))
            }
        }
    }

    fun register() {
        if (email.isBlank() || password.length < 6) {
            sendEffect(AuthEffect.ShowToast("Password must be 6+ chars"))
            return
        }

        viewModelScope.launch {
            try {
                isLoading = true
                val result = repository.register(email, password, "PARENT")
                if (result.isSuccess) {
                    val profile = repository.getCurrentUserProfile()
                    isLoading = false
                    if (profile != null && profile.role == UserRole.PARENT) {
                        sendEffect(AuthEffect.NavigateToDashboard)
                    } else {
                        sendEffect(AuthEffect.ShowToast("Account setup incomplete."))
                    }
                } else {
                    isLoading = false
                    val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Registration failed."
                    sendEffect(AuthEffect.ShowToast(errorMsg))
                }
            } catch (e: Throwable) {
                isLoading = false
                sendEffect(AuthEffect.ShowToast("Error: ${e.message}"))
            }
        }
    }

    private fun sendEffect(effect: AuthEffect) {
        viewModelScope.launch {
            _authEffect.send(effect)
        }
    }
}

@Composable
fun AuthScreen(viewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    var isLoginMode by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = true) {
        viewModel.authEffect.collect { effect ->
            when (effect) {
                is AuthEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is AuthEffect.NavigateToDashboard -> {
                    context.startActivity(Intent(context, ParentDashboardActivity::class.java))
                    // Optionally finish the current activity if context is Activity
                    if (context is Activity) {
                        context.finish()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically() + fadeIn()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Talos Guardian",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isLoginMode) "Welcome Back" else "Join the Shield",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    OutlinedTextField(
                        value = viewModel.email,
                        onValueChange = { viewModel.email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = viewModel.password,
                        onValueChange = { viewModel.password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (viewModel.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Button(
                            onClick = {
                                if (isLoginMode) {
                                    viewModel.login()
                                } else {
                                    viewModel.register()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isLoginMode) "Sign In" else "Create Account",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = { isLoginMode = !isLoginMode }) {
            Text(
                text = if (isLoginMode) "Need an account? Sign Up" else "Already have an account? Sign In",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

