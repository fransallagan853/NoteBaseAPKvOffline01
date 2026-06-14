package com.notebaseapk

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityNoteEditBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private lateinit var db: AppDatabase
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var vehicleId: Int = -1
    private var kendaraan: Kendaraan? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSafeHeader()
        setupSafeBottomButton()

        db = AppDatabase.getDatabase(this)
        vehicleId = intent.getIntExtra("VEHICLE_ID", -1)

        if (vehicleId == -1) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveNote()
        }

        loadVehicleData()
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBarHeight + extraPadding, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupSafeBottomButton() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnSave) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraMargin = (16 * resources.displayMetrics.density).toInt()

            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight + extraMargin
            view.layoutParams = params

            insets
        }
    }

    private fun loadVehicleData() {
        lifecycleScope.launch {
            kendaraan = db.kendaraanDao().getKendaraanById(vehicleId)

            kendaraan?.let {
                binding.tvSummaryNopol.text = it.nopol
                binding.tvSummaryKendaraan.text = it.namaKendaraan
                binding.tvSummaryLeasing.text = it.leasing
                binding.etCatatan.setText(it.catatan)
            } ?: run {
                finish()
            }
        }
    }

    private fun saveNote() {
        val noteText = binding.etCatatan.text.toString().trim()
        val isPublish = binding.rbPublish.isChecked

        if (isPublish) {
            publishNote(noteText)
        } else {
            saveLocally(noteText, null, null)
            Toast.makeText(this, "Catatan pribadi berhasil disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun publishNote(noteText: String) {
        val user = auth.currentUser

        if (user == null) {
            Toast.makeText(this, "Silakan login terlebih dahulu untuk publish catatan", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val userDoc = firestore.collection("users")
                    .document(user.uid)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    Toast.makeText(this@NoteEditActivity, "Data user tidak ditemukan", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val name = userDoc.getString("name")
                val phone = userDoc.getString("phone")
                val email = userDoc.getString("email")

                if (name.isNullOrEmpty() || phone.isNullOrEmpty()) {
                    Toast.makeText(
                        this@NoteEditActivity,
                        "Lengkapi profil terlebih dahulu sebelum publish catatan",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val publicNote = hashMapOf(
                    "vehicleId" to vehicleId,
                    "nopol" to (kendaraan?.nopol ?: ""),
                    "leasing" to (kendaraan?.leasing ?: ""),
                    "noteText" to noteText,
                    "editorUid" to user.uid,
                    "editorName" to name,
                    "editorPhone" to phone,
                    "editorEmail" to email,
                    "visibility" to "public",
                    "status" to "approved",
                    "editedAt" to FieldValue.serverTimestamp()
                )

                firestore.collection("public_vehicle_notes")
                    .add(publicNote)
                    .await()

                saveLocally(noteText, name, phone)

                Toast.makeText(this@NoteEditActivity, "Catatan berhasil dipublish", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@NoteEditActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveLocally(noteText: String, editorName: String?, editorPhone: String?) {
        lifecycleScope.launch {
            kendaraan?.let {
                it.catatan = noteText
                it.editorName = editorName
                it.editorPhone = editorPhone
                db.kendaraanDao().update(it)
            }
        }
    }
}