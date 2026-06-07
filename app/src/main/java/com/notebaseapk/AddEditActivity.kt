package com.notebaseapk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityAddEditBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class AddEditActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddEditBinding
    private lateinit var db: AppDatabase
    private lateinit var customKeyboardManager: CustomKeyboardManager
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var vehicleId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)
        setupCustomKeyboard()

        if (vehicleId != -1) {
            binding.tvTitle.text = "Edit Data"
            loadVehicleData()
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveVehicle() }
    }
    private fun setupCustomKeyboard() {
        customKeyboardManager = CustomKeyboardManager(
            activity = this,
            keyboardContainer = binding.keyboardContainer
        )

        customKeyboardManager.setup(
            listOf(
                binding.etNopol,
                binding.etNamaKendaraan,
                binding.etTahun,
                binding.etWarna,
                binding.etLeasing,
                binding.etCabang,
                binding.etSaldo,
                binding.etOverdue,
                binding.etNoRangka,
                binding.etNoMesin,
                binding.etCatatan
            )
        )
    }

    override fun onStart() {
        super.onStart()
        if (::customKeyboardManager.isInitialized) {
            customKeyboardManager.refreshLayout()
        }
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
                binding.etCabang.setText(it.cabang)
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
        val cabang = binding.etCabang.text.toString().trim().uppercase(Locale.getDefault())
        val saldo = binding.etSaldo.text.toString().trim()
        val ovd = binding.etOverdue.text.toString().trim()
        val catatan = binding.etCatatan.text.toString().trim()
        val isPublish = binding.rbPublish.isChecked

        if (nopol.isEmpty() || nama.isEmpty()) {
            Toast.makeText(this, "Nopol dan Nama wajib diisi", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            var publisherName: String? = null
            var publisherPhone: String? = null
            var publisherEmail: String? = null

            if (isPublish) {
                val user = auth.currentUser
                if (user == null) {
                    Toast.makeText(this@AddEditActivity, "Silakan login terlebih dahulu untuk publish data", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val userDoc = firestore.collection("users").document(user.uid).get().await()
                    if (userDoc.exists()) {
                        publisherName = userDoc.getString("name")
                        publisherPhone = userDoc.getString("phone")
                        publisherEmail = userDoc.getString("email")

                        if (publisherName.isNullOrEmpty() || publisherPhone.isNullOrEmpty()) {
                            Toast.makeText(this@AddEditActivity, "Lengkapi profil terlebih dahulu sebelum publish data", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    } else {
                        Toast.makeText(this@AddEditActivity, "Data profil tidak ditemukan", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@AddEditActivity, "Gagal mengambil profil: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            val groupNumber = extractGroup(nopol)
            val searchKey = generateSearchKey(
                nopol,
                nama,
                tahun,
                warna,
                rangka,
                mesin,
                leasing,
                cabang,
                saldo,
                ovd,
                catatan
            )
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
                cabang = cabang,
                saldo = saldo,
                overdue = ovd,
                catatan = catatan,
                searchKey = searchKey,
                editorName = publisherName,
                editorPhone = publisherPhone
            )

            // Simpan Lokal
            if (vehicleId == -1) {
                db.kendaraanDao().insert(kendaraan)
            } else {
                db.kendaraanDao().update(kendaraan)
            }

            // Simpan ke Firestore jika Publish
            if (isPublish) {
                val publicData = hashMapOf(
                    "nopol" to nopol,
                    "namaKendaraan" to nama,
                    "tahun" to tahun,
                    "warna" to warna,
                    "noRangka" to rangka,
                    "noMesin" to mesin,
                    "leasing" to leasing,
                    "cabang" to cabang,
                    "saldo" to saldo,
                    "overdue" to ovd,
                    "catatan" to catatan,
                    "publisherUid" to auth.currentUser?.uid,
                    "publisherName" to publisherName,
                    "publisherPhone" to publisherPhone,
                    "publisherEmail" to publisherEmail,
                    "status" to "approved",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                try {
                    firestore.collection("public_vehicles").add(publicData).await()
                    Toast.makeText(this@AddEditActivity, "Data berhasil dipublish", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@AddEditActivity, "Gagal publish ke Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@AddEditActivity, "Data disimpan secara privat", Toast.LENGTH_SHORT).show()
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
