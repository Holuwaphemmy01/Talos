package com.talos.guardian

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class FirebaseConnectionTest {

    @Test
    fun testFirebaseInitialization() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        
        // 1. Check Context
        assertEquals("com.talos.guardian", appContext.packageName)

        // 2. Initialize Firebase
        // Firebase usually inits automatically via ContentProvider, but checking it ensures no crash.
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            val app = FirebaseApp.getInstance()
            assertNotNull("FirebaseApp instance should not be null", app)
        } catch (e: Exception) {
            fail("FirebaseApp initialization failed: ${e.message}")
        }

        // 3. Check Auth Instance
        try {
            val auth = FirebaseAuth.getInstance()
            assertNotNull("FirebaseAuth instance should not be null", auth)
            // Log to confirm we got here
            println("TalosTest: FirebaseAuth initialized successfully")
        } catch (e: Exception) {
             fail("FirebaseAuth.getInstance() crashed: ${e.message}")
        }

        // 4. Check Firestore Instance
        try {
            val db = FirebaseFirestore.getInstance()
            assertNotNull("FirebaseFirestore instance should not be null", db)
            println("TalosTest: FirebaseFirestore initialized successfully")
        } catch (e: Exception) {
             fail("FirebaseFirestore.getInstance() crashed: ${e.message}")
        }

        // 5. Check Options (loaded from google-services.json)
        try {
            val options = FirebaseApp.getInstance().options
            println("Firebase Project ID: ${options.projectId}")
            if (options.apiKey.isNullOrEmpty() || options.applicationId.isNullOrEmpty()) {
                fail("Firebase Options are missing/empty! Is google-services.json inside the app/ folder?")
            }
        } catch (e: Exception) {
            fail("Failed to read FirebaseOptions: ${e.message}")
        }
    }
}
