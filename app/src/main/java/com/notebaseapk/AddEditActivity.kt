package com.notebaseapk

import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityAddEditBinding
import kotlinx.coroutines.launch
import java.util.Locale

class AddEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditBinding
    private lateinit var db: AppDatabase
    private lateinit var customKeyboardManager: CustomKeyboardManager

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var vehicleId: Int = -1
    private var navigationBarHeight: Int = 0

    private var saveButtonLayoutListener:
            ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSafeHeader()

        db = AppDatabase.getDatabase(this)

        vehicleId = intent.getIntExtra(
            "VEHICLE_ID",
            -1
        )

        /*
         * Hanya mengisi otomatis saat membuka mode Tambah Data.
         * Saat Edit Data, nopol tetap diambil dari database seperti biasa.
         */
        if (vehicleId == -1) {
            val prefillNopol = intent.getStringExtra(
                "PREFILL_NOPOL"
            )
                .orEmpty()
                .trim()
                .uppercase(Locale.getDefault())

            if (prefillNopol.isNotEmpty()) {
                binding.etNopol.setText(prefillNopol)

                binding.etNopol.setSelection(
                    binding.etNopol.text.length
                )
            }
        }

        setupCustomKeyboard()
        setupSafeSaveButton()

        if (vehicleId != -1) {
            binding.tvTitle.text = "Edit Data"
            loadVehicleData()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveVehicle()
        }
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(
            binding.headerLayout
        ) { view, insets ->

            val statusBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top

            val extraPadding = dpToPx(16)

            view.setPadding(
                view.paddingLeft,
                statusBarHeight + extraPadding,
                view.paddingRight,
                view.paddingBottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.headerLayout)
    }

    private fun setupSafeSaveButton() {
        val normalBottomMargin = dpToPx(16)
        val keyboardBottomMargin = dpToPx(4)

        fun updateSaveButtonPosition() {
            val params = binding.btnSave.layoutParams
                    as? ConstraintLayout.LayoutParams
                ?: return

            val keyboardTampil =
                binding.keyboardContainer.visibility == View.VISIBLE &&
                        binding.keyboardContainer.height > 0

            if (keyboardTampil) {
                /*
                 * Keyboard terbuka:
                 * tombol menempel tepat di atas keyboard.
                 */
                val perluDiubah =
                    params.bottomToTop != binding.keyboardContainer.id ||
                            params.bottomToBottom != ConstraintSet.UNSET ||
                            params.bottomMargin != keyboardBottomMargin

                if (perluDiubah) {
                    params.bottomToBottom = ConstraintSet.UNSET
                    params.bottomToTop = binding.keyboardContainer.id
                    params.bottomMargin = keyboardBottomMargin

                    binding.btnSave.layoutParams = params
                }
            } else {
                /*
                 * Keyboard tertutup:
                 * tombol dikembalikan langsung ke bawah parent.
                 * Tidak lagi bergantung pada keyboardContainer yang GONE.
                 */
                val targetMargin =
                    navigationBarHeight + normalBottomMargin

                val perluDiubah =
                    params.bottomToTop != ConstraintSet.UNSET ||
                            params.bottomToBottom != ConstraintSet.PARENT_ID ||
                            params.bottomMargin != targetMargin

                if (perluDiubah) {
                    params.bottomToTop = ConstraintSet.UNSET
                    params.bottomToBottom = ConstraintSet.PARENT_ID
                    params.bottomMargin = targetMargin

                    binding.btnSave.layoutParams = params
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(
            binding.root
        ) { _, insets ->

            navigationBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            binding.root.post {
                updateSaveButtonPosition()
            }

            insets
        }

        saveButtonLayoutListener =
            ViewTreeObserver.OnGlobalLayoutListener {
                updateSaveButtonPosition()
            }

        binding.root.viewTreeObserver.addOnGlobalLayoutListener(
            saveButtonLayoutListener
        )

        binding.root.post {
            updateSaveButtonPosition()
            ViewCompat.requestApplyInsets(binding.root)
        }
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
            db.kendaraanDao()
                .getKendaraanById(vehicleId)
                ?.let {

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
        val nopol = binding.etNopol.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val nama = binding.etNamaKendaraan.text
            .toString()
            .trim()

        val tahun = binding.etTahun.text
            .toString()
            .trim()

        val warna = binding.etWarna.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val rangka = binding.etNoRangka.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val mesin = binding.etNoMesin.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val leasing = binding.etLeasing.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val cabang = binding.etCabang.text
            .toString()
            .trim()
            .uppercase(Locale.getDefault())

        val saldo = binding.etSaldo.text
            .toString()
            .trim()

        val ovd = binding.etOverdue.text
            .toString()
            .trim()

        val catatan = binding.etCatatan.text
            .toString()
            .trim()

        if (nopol.isEmpty() || nama.isEmpty()) {
            Toast.makeText(
                this,
                "Nopol dan Nama wajib diisi",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        lifecycleScope.launch {
            var publisherName: String? = null
            var publisherPhone: String? = null

            val groupNumber = extractGroup(nopol)

            val searchKey = generateSearchKey(
                nopol,
                nama,
                warna,
                rangka,
                mesin,
                leasing,
                cabang,
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
        return regex.find(nopol)?.value ?: "0000"
    }

    private fun generateSearchKey(
        vararg fields: String
    ): String {
        return fields.joinToString("") {
            it.uppercase(Locale.getDefault())
                .replace("\\s".toRegex(), "")
                .replace("-", "")
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (
                dp * resources.displayMetrics.density
                ).toInt()
    }

    override fun onDestroy() {
        saveButtonLayoutListener?.let { listener ->
            if (binding.root.viewTreeObserver.isAlive) {
                binding.root.viewTreeObserver
                    .removeOnGlobalLayoutListener(listener)
            }
        }

        saveButtonLayoutListener = null

        super.onDestroy()
    }
}