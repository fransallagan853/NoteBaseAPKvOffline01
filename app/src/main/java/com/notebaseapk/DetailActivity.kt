package com.notebaseapk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var db: AppDatabase
    private var vehicleId: Int = -1
    private var kendaraan: Kendaraan? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)

        binding.btnBack.setOnClickListener { finish() }

        loadData()
        setupActions()
    }

    private fun loadData() {
        lifecycleScope.launch {
            kendaraan = db.kendaraanDao().getKendaraanById(vehicleId)
            kendaraan?.let {
                binding.tvNopol.text = it.nopol
                binding.tvNamaKendaraan.text = it.namaKendaraan
                binding.tvTahun.text = it.tahun.ifEmpty { "-" }
                binding.tvWarna.text = it.warna.ifEmpty { "-" }
                binding.tvNoRangka.text = it.noRangka.ifEmpty { "-" }
                binding.tvNoMesin.text = it.noMesin.ifEmpty { "-" }
                binding.tvLeasing.text = it.leasing
                binding.tvSaldo.text = it.saldo.ifEmpty { "-" }
                binding.tvOverdue.text = it.overdue.ifEmpty { "-" }
                binding.tvCatatan.text = it.catatan.ifEmpty { "Tidak ada catatan" }
            } ?: run {
                finish()
            }
        }
    }

    private fun setupActions() {
        binding.btnEdit.setOnClickListener {
            val intent = Intent(this, AddEditActivity::class.java)
            intent.putExtra("VEHICLE_ID", vehicleId)
            startActivity(intent)
        }

        binding.btnDelete.setOnClickListener {
            showDeleteDialog()
        }

        binding.btnShare.setOnClickListener {
            kendaraan?.let { shareData(it) }
        }

        binding.btnCopy.setOnClickListener {
            kendaraan?.let { copyToClipboard(it) }
        }
    }

    private fun buildShareText(it: Kendaraan): String {
        return """
            noteBase - Detail Data

            Nomor Polisi: ${it.nopol}
            Kendaraan: ${it.namaKendaraan}
            Tahun: ${it.tahun.ifEmpty { "-" }}
            Warna: ${it.warna.ifEmpty { "-" }}
            No Rangka: ${it.noRangka.ifEmpty { "-" }}
            No Mesin: ${it.noMesin.ifEmpty { "-" }}
            Leasing: ${it.leasing}
            Saldo: ${it.saldo.ifEmpty { "-" }}
            Overdue/OVD: ${it.overdue.ifEmpty { "-" }}
            Catatan: ${it.catatan.ifEmpty { "-" }}
        """.trimIndent()
    }

    private fun shareData(it: Kendaraan) {
        val text = buildShareText(it)
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(intent, "Bagikan data"))
    }

    private fun copyToClipboard(it: Kendaraan) {
        val text = buildShareText(it)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Detail Data noteBase", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Detail data disalin", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Data")
            .setMessage("Apakah Anda yakin ingin menghapus data ini?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    kendaraan?.let {
                        db.kendaraanDao().delete(it)
                        finish()
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }
}
