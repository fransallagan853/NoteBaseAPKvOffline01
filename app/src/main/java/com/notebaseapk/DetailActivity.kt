package com.notebaseapk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.FavoriteVehicle
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityDetailBinding
import kotlinx.coroutines.launch
import com.notebaseapk.util.NopolFormatter

class DetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailBinding
    private lateinit var db: AppDatabase
    private var vehicleId: Int = -1
    private var kendaraan: Kendaraan? = null
    private var isFavorite: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)

        setupSafeHeader()
        setupSafeActionLayout()

        binding.btnBack.setOnClickListener { finish() }

        loadData()
        setupActions()
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBarHeight + extraPadding, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupSafeActionLayout() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.actionLayout) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraMargin = (16 * resources.displayMetrics.density).toInt()
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight + extraMargin
            view.layoutParams = params
            insets
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            kendaraan = db.kendaraanDao().getKendaraanById(vehicleId)

            kendaraan?.let {
                binding.tvNopol.text = NopolFormatter.display(it.nopol)
                binding.tvNamaKendaraan.text = it.namaKendaraan
                binding.tvTahun.text = it.tahun.ifEmpty { "-" }
                binding.tvWarna.text = it.warna.ifEmpty { "-" }
                binding.tvNoRangka.text = it.noRangka.ifEmpty { "-" }
                binding.tvNoMesin.text = it.noMesin.ifEmpty { "-" }
                binding.tvLeasing.text = it.leasing
                binding.tvCabang.text = it.cabang.ifEmpty { "-" }
                binding.tvSaldo.text = it.saldo.ifEmpty { "-" }
                binding.tvOverdue.text = it.overdue.ifEmpty { "-" }
                binding.tvCatatan.text = it.catatan.ifEmpty { "Tidak ada catatan" }

                if (!it.editorName.isNullOrEmpty()) {
                    binding.editorInfoLayout.visibility = View.VISIBLE
                    binding.tvEditorName.text = "Nama: ${it.editorName}"
                    binding.tvEditorPhone.text = "No Telp: ${it.editorPhone ?: "-"}"
                } else {
                    binding.editorInfoLayout.visibility = View.GONE
                }

                checkFavoriteStatus(it)
            } ?: run {
                finish()
            }
        }
    }

    private fun setupActions() {
        binding.btnNote.setOnClickListener {
            val intent = Intent(this, NoteEditActivity::class.java)
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

        binding.btnFavorite.setOnClickListener {
            kendaraan?.let { toggleFavorite(it) }
        }
    }

    private fun checkFavoriteStatus(k: Kendaraan) {
        lifecycleScope.launch {
            val count = db.favoriteVehicleDao().isFavorite(
                nopol = k.nopol,
                leasing = k.leasing,
                cabang = k.cabang
            )

            isFavorite = count > 0
            updateFavoriteButton()
        }
    }

    private fun toggleFavorite(k: Kendaraan) {
        lifecycleScope.launch {
            if (isFavorite) {
                db.favoriteVehicleDao().deleteFavoriteByKey(
                    nopol = k.nopol,
                    leasing = k.leasing,
                    cabang = k.cabang
                )

                isFavorite = false
                Toast.makeText(this@DetailActivity, "Dihapus dari favorit", Toast.LENGTH_SHORT).show()
            } else {
                val favorite = FavoriteVehicle(
                    nopol = k.nopol,
                    leasing = k.leasing,
                    cabang = k.cabang
                )

                db.favoriteVehicleDao().insertFavorite(favorite)

                isFavorite = true
                Toast.makeText(this@DetailActivity, "Ditambahkan ke favorit", Toast.LENGTH_SHORT).show()
            }

            updateFavoriteButton()
        }
    }

    private fun updateFavoriteButton() {
        if (isFavorite) {
            binding.btnFavorite.text = "★ Difavoritkan"
        } else {
            binding.btnFavorite.text = "☆ Favorit"
        }
    }

    private fun buildShareText(it: Kendaraan): String {
        val formattedNopol = NopolFormatter.display(it.nopol)

        var text = """
            noteBase - Detail Data

            Nomor Polisi: $formattedNopol
            Kendaraan: ${it.namaKendaraan}
            Tahun: ${it.tahun.ifEmpty { "-" }}
            Warna: ${it.warna.ifEmpty { "-" }}
            Leasing: ${it.leasing}
            Cabang: ${it.cabang.ifEmpty { "-" }}
            No Rangka: ${it.noRangka.ifEmpty { "-" }}
            No Mesin: ${it.noMesin.ifEmpty { "-" }}
            Saldo: ${it.saldo.ifEmpty { "-" }}
            Overdue/OVD: ${it.overdue.ifEmpty { "-" }}
            Catatan: ${it.catatan.ifEmpty { "-" }}
        """.trimIndent()

        if (!it.editorName.isNullOrEmpty()) {
            text += "\n\nDiedit oleh:\nNama: ${it.editorName}\nNo Telp: ${it.editorPhone ?: "-"}"
        }

        return text
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

                        db.favoriteVehicleDao().deleteFavoriteByKey(
                            nopol = it.nopol,
                            leasing = it.leasing,
                            cabang = it.cabang
                        )

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
