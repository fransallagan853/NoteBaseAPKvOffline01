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
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityDetailBinding
import com.notebaseapk.util.NopolFormatter
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

        binding.headerLayout.applyStatusBarPadding()
        binding.actionLayout.applyNavigationBarMargin()

        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)

        setupSafeHeader()
        setupSafeActionLayout()

        binding.btnBack.setOnClickListener { finish() }

        loadData()
        setupActions()
    }

    private fun getNopolKey(kendaraan: Kendaraan): String {
        return kendaraan.nopolKey.ifBlank {
            NopolFormatter.generateNopolKey(kendaraan.nopol)
        }
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top

            val extraPadding = (8 * resources.displayMetrics.density).toInt()

            view.setPadding(
                view.paddingLeft,
                statusBarHeight + extraPadding,
                view.paddingRight,
                view.paddingBottom
            )

            insets
        }
    }

    private fun setupSafeActionLayout() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.actionLayout) { view, insets ->
            val navBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            val extraMargin = (8 * resources.displayMetrics.density).toInt()
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
                binding.tvPeriodeData.text = it.periodeData.ifEmpty { "-" }
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
    }

    private fun buildShareText(item: Kendaraan): String {
        val formattedNopol = NopolFormatter.display(item.nopol)

        var text = """
            noteBase - Detail Data

            Nomor Polisi: $formattedNopol
            Periode Data: ${item.periodeData.ifEmpty { "-" }}
            Kendaraan: ${item.namaKendaraan}
            Tahun: ${item.tahun.ifEmpty { "-" }}
            Warna: ${item.warna.ifEmpty { "-" }}
            Leasing: ${item.leasing}
            Cabang: ${item.cabang.ifEmpty { "-" }}
            No Rangka: ${item.noRangka.ifEmpty { "-" }}
            No Mesin: ${item.noMesin.ifEmpty { "-" }}
            Saldo: ${item.saldo.ifEmpty { "-" }}
            Overdue/OVD: ${item.overdue.ifEmpty { "-" }}
            Catatan: ${item.catatan.ifEmpty { "-" }}
        """.trimIndent()

        if (!item.editorName.isNullOrEmpty()) {
            text +=
                "\n\nDiedit oleh:" +
                        "\nNama: ${item.editorName}" +
                        "\nNo Telp: ${item.editorPhone ?: "-"}"
        }

        return text
    }

    private fun shareData(item: Kendaraan) {
        val text = buildShareText(item)
        val intent = Intent(Intent.ACTION_SEND)

        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)

        startActivity(
            Intent.createChooser(
                intent,
                "Bagikan data"
            )
        )
    }

    private fun copyToClipboard(item: Kendaraan) {
        val text = buildShareText(item)

        val clipboard =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(
            "Detail Data noteBase",
            text
        )

        clipboard.setPrimaryClip(clip)

        Toast.makeText(
            this,
            "Detail data disalin",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Data")
            .setMessage("Apakah Anda yakin ingin menghapus data ini?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    kendaraan?.let {
                        db.kendaraanDao().delete(it)

                        // Favorit memang disembunyikan, tetapi data favorit lama
                        // tetap dibersihkan saat kendaraan utamanya dihapus.
                        db.favoriteVehicleDao().deleteFavoriteByKey(
                            nopolKey = getNopolKey(it),
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
