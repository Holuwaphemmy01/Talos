package com.talos.guardian.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    suspend fun register(email: String, pass: String, role: String): Boolean {
        return try {
            // 1. Create User in Auth
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: return false

            // 2. Store Role in Firestore
            val userMap = hashMapOf(
                "email" to email,
                "role" to role,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid).set(userMap).await()
            true
        } catch (e: Exception) {
            Log.e("TalosAuth", "Register Failed", e)
            false
        }
    }

    suspend fun login(email: String, pass: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            true
        } catch (e: Exception) {
            Log.e("TalosAuth", "Login Failed", e)
            false
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logout() {
        auth.signOut()
    }
    
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }
}
