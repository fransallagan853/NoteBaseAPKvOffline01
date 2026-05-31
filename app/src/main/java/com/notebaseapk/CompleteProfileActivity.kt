package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.User
import com.notebaseapk.databinding.ActivityCompleteProfileBinding

class CompleteProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCompleteProfileBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompleteProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Pre-fill data from Google Account
        binding.etName.setText(currentUser.displayName)
        binding.etEmail.setText(currentUser.email)

        binding.btnSave.setOnClickListener {
            saveProfile()
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val uid = auth.currentUser?.uid ?: return

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Nama dan Nomor Telepon wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val userMap = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "phone" to phone,
            "role" to "user",
            "subscriptionStatus" to "inactive",
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // Use set with merge to avoid overwriting subscription data if it exists
        db.collection("users").document(uid)
            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Profil berhasil disimpan", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menyimpan profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
