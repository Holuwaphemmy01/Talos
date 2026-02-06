package com.talos.guardian.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface IAuthRepository {
    suspend fun register(email: String, pass: String, role: String): Result<Boolean>
    suspend fun login(email: String, pass: String): Result<Boolean>
    suspend fun getCurrentUserProfile(): TalosUser?
}

object AuthRepository : IAuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override suspend fun register(email: String, pass: String, role: String): Result<Boolean> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = authResult.user?.uid ?: return Result.failure(Exception("User creation failed"))

            val userMap = hashMapOf(
                "uid" to uid,
                "email" to email,
                "role" to role,
                "createdAt" to System.currentTimeMillis()
            )
            db.collection("users").document(uid).set(userMap).await()
            Result.success(true)
        } catch (e: Throwable) {
            Log.e("TalosAuth", "Register Failed", e)
            Result.failure(e)
        }
    }

    override suspend fun login(email: String, pass: String): Result<Boolean> {
        return try {
            auth.signInWithEmailAndPassword(email, pass).await()
            Result.success(true)
        } catch (e: Throwable) {
            Log.e("TalosAuth", "Login Failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getCurrentUserProfile(): TalosUser? {
        val current = auth.currentUser ?: return null
        return try {
            val snapshot = db.collection("users").document(current.uid).get().await()
            if (!snapshot.exists()) return null
            snapshot.toObject(TalosUser::class.java)
        } catch (e: Throwable) {
            Log.e("TalosAuth", "Load Profile Failed", e)
            null
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
