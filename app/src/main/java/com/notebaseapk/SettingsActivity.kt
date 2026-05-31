package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var db: AppDatabase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener { finish() }

        observeData()
        setupActions()
        setupBottomNav()
        updateUserUI()
    }

    private fun updateUserUI() {
        val user = auth.currentUser
        if (user != null) {
            binding.btnLogin.visibility = View.GONE
            binding.layoutUserInfo.visibility = View.VISIBLE
            
            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    val name = doc.getString("name") ?: user.displayName ?: "User"
                    val email = doc.getString("email") ?: user.email ?: "-"
                    val phone = doc.getString("phone") ?: "-"
                    
                    binding.tvUserName.text = name
                    binding.tvUserEmail.text = email
                    binding.tvUserPhone.text = phone
                }
        } else {
            binding.btnLogin.visibility = View.VISIBLE
            binding.layoutUserInfo.visibility = View.GONE
        }
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        binding.menuSync.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.kendaraanDao().getCount().collectLatest { count ->
                binding.tvTotalData.text = "$count Data Tersimpan"
            }
        }
    }

    private fun setupActions() {
        binding.btnKeyboardSettings.setOnClickListener {
            startActivity(Intent(this, KeyboardSettingsActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            auth.signOut()
            updateUserUI()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        binding.btnClearData.setOnClickListener {
            showClearDataDialog()
        }
    }

    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua Data")
            .setMessage("Apakah Anda yakin ingin menghapus SEMUA data dari database? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    db.kendaraanDao().deleteAll()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
