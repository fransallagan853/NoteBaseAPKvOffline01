package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener { finish() }

        observeData()
        setupActions()
        setupBottomNav()
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        binding.menuImport.setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
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
