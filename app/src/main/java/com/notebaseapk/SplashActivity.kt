package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000)
    }

    private fun checkLoginStatus() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Not logged in
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            // Logged in, check if profile is complete (has phone)
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists() && document.contains("phone")) {
                        startActivity(Intent(this, MainActivity::class.java))
                    } else {
                        startActivity(Intent(this, CompleteProfileActivity::class.java))
                    }
                    finish()
                }
                .addOnFailureListener {
                    // Fallback to offline mode/Main if Firestore fails
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
        }
    }
}
