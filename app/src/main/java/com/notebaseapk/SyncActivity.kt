package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.databinding.ActivitySyncBinding

class SyncActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        updateUI()

        binding.btnCheckUpdate.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Langganan belum aktif", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLoginAction.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        setupBottomNav()
    }

    private fun updateUI() {
        val user = auth.currentUser
        if (user != null) {
            // User Logged In
            binding.btnLoginAction.visibility = View.GONE
            binding.layoutAccountInfo.visibility = View.VISIBLE
            
            db.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: user.displayName ?: "User"
                    val email = doc.getString("email") ?: user.email ?: "-"
                    val phone = doc.getString("phone") ?: "-"
                    val subStatus = doc.getString("subscriptionStatus") ?: "inactive"

                    binding.tvStatusAkun.text = "Status Akun: $name"
                    binding.tvUserEmail.text = "Gmail: $email"
                    binding.tvUserPhone.text = "No Telepon: $phone"
                    binding.tvStatusSub.text = "Status Langganan: ${if (subStatus == "active") "Aktif" else "Belum aktif"}"
                }
        } else {
            // User Not Logged In
            binding.btnLoginAction.visibility = View.VISIBLE
            binding.layoutAccountInfo.visibility = View.GONE
            binding.tvStatusAkun.text = "Status Akun: Belum login"
            binding.tvStatusSub.text = "Status Langganan: Belum aktif"
        }
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        binding.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
