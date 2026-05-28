package com.notebaseapk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityAddEditBinding
import kotlinx.coroutines.launch
import java.util.Locale

class AddEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditBinding
    private lateinit var db: AppDatabase
    private var vehicleId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)

        if (vehicleId != -1) {
            binding.tvTitle.text = "Edit Data"
            loadVehicleData()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveVehicle() }
    }

    private fun loadVehicleData() {
        lifecycleScope.launch {
            db.kendaraanDao().getKendaraanById(vehicleId)?.let {
                binding.etNopol.setText(it.nopol)
                binding.etNamaKendaraan.setText(it.namaKendaraan)
                binding.etTahun.setText(it.tahun)
                binding.etWarna.setText(it.warna)
                binding.etNoRangka.setText(it.noRangka)
                binding.etNoMesin.setText(it.noMesin)
                binding.etLeasing.setText(it.leasing)
                binding.etSaldo.setText(it.saldo)
                binding.etOverdue.setText(it.overdue)
                binding.etCatatan.setText(it.catatan)
            }
        }
    }

    private fun saveVehicle() {
        val nopol = binding.etNopol.text.toString().trim().uppercase(Locale.getDefault())
        val nama = binding.etNamaKendaraan.text.toString().trim()
        val tahun = binding.etTahun.text.toString().trim()
        val warna = binding.etWarna.text.toString().trim().uppercase(Locale.getDefault())
        val rangka = binding.etNoRangka.text.toString().trim().uppercase(Locale.getDefault())
        val mesin = binding.etNoMesin.text.toString().trim().uppercase(Locale.getDefault())
        val leasing = binding.etLeasing.text.toString().trim().uppercase(Locale.getDefault())
        val saldo = binding.etSaldo.text.toString().trim()
        val ovd = binding.etOverdue.text.toString().trim()
        val catatan = binding.etCatatan.text.toString().trim()

        if (nopol.isEmpty() || nama.isEmpty()) {
            Toast.makeText(this, "Nopol dan Nama wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        val groupNumber = extractGroup(nopol)
        val searchKey = generateSearchKey(nopol, nama, tahun, warna, leasing, saldo, ovd, catatan)

        lifecycleScope.launch {
            val kendaraan = Kendaraan(
                id = if (vehicleId == -1) 0 else vehicleId,
                nopol = nopol,
                groupNumber = groupNumber,
                namaKendaraan = nama,
                tahun = tahun,
                warna = warna,
                noRangka = rangka,
                noMesin = mesin,
                leasing = leasing,
                saldo = saldo,
                overdue = ovd,
                catatan = catatan,
                searchKey = searchKey
            )

            if (vehicleId == -1) {
                db.kendaraanDao().insert(kendaraan)
            } else {
                db.kendaraanDao().update(kendaraan)
            }
            finish()
        }
    }

    private fun extractGroup(nopol: String): String {
        val regex = "\\d+".toRegex()
        val match = regex.find(nopol)
        return match?.value ?: "0000"
    }

    private fun generateSearchKey(vararg fields: String): String {
        return fields.joinToString("") { 
            it.uppercase(Locale.getDefault())
                .replace("\\s".toRegex(), "")
                .replace("-", "") 
        }
    }
}
