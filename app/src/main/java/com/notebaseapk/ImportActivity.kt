package com.notebaseapk

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityImportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class ImportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityImportBinding
    private lateinit var db: AppDatabase
    private var selectedFileUri: Uri? = null
    private var fileHeaders = mutableListOf<String>()
    private var fileLines = mutableListOf<String>()
    private var delimiter: Char = ','

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFileUri = it
            binding.tvFileName.text = it.path?.substringAfterLast("/") ?: "File terpilih"
            prepareMapping(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        setupSafeHeader()
        setupSafeBottomNav()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSelectFile.setOnClickListener { filePicker.launch("text/comma-separated-values") }
        binding.btnStartImport.setOnClickListener { startImport() }

        binding.rgLeasingMode.setOnCheckedChangeListener { _, checkedId ->
            binding.etLeasingManual.visibility = if (checkedId == R.id.rbManual) View.VISIBLE else View.GONE
            updatePreview()
        }

        setupBottomNav()
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBarHeight + extraPadding, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupSafeBottomNav() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight
            view.layoutParams = params
            insets
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
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }
    }

    private fun prepareMapping(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val headerLine = reader.readLine() ?: return@launch
                        delimiter = detectDelimiter(headerLine)
                        fileHeaders = splitCsvLine(headerLine, delimiter).toMutableList()
                        
                        fileLines.clear()
                        repeat(10) {
                            reader.readLine()?.let { fileLines.add(it) }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.layoutMapping.visibility = View.VISIBLE
                    setupSpinners()
                    updatePreview()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImportActivity, "Gagal membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupSpinners() {
        val options = mutableListOf("--- Lewati ---")
        options.addAll(fileHeaders)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinners = listOf(
            binding.spNopol, binding.spNama, binding.spLeasing, binding.spTahun,
            binding.spWarna, binding.spSaldo, binding.spOverdue, binding.spRangka,
            binding.spMesin, binding.spCatatan
        )

        spinners.forEach { spinner ->
            spinner.adapter = adapter
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) { 
                    updatePreview() 
                }
                override fun onNothingSelected(p0: AdapterView<*>?) {}
            }
        }

        // Auto mapping logic
        fileHeaders.forEachIndexed { index, header ->
            val norm = normalizeHeader(header)
            when {
                isMatch(norm, listOf("nopol", "plat", "plate", "nopolisi", "nomorpolisi", "noplat", "nomorplat", "nomor")) -> 
                    binding.spNopol.setSelection(index + 1)
                isMatch(norm, listOf("type", "tipe", "model", "kendaraan", "namakendaraan", "unit", "merk", "mobil")) -> 
                    binding.spNama.setSelection(index + 1)
                isMatch(norm, listOf("leasing", "finance", "pembiayaan", "bank", "perusahaan", "multifinance", "kreditur")) -> 
                    binding.spLeasing.setSelection(index + 1)
                isMatch(norm, listOf("tahun", "year", "thn")) -> 
                    binding.spTahun.setSelection(index + 1)
                isMatch(norm, listOf("warna", "color", "colour")) -> 
                    binding.spWarna.setSelection(index + 1)
                isMatch(norm, listOf("saldo", "sisasaldo", "sisahutang", "outstanding", "os", "ospokok", "totaltagihan", "tagihan", "hutang", "bakidebet")) -> 
                    binding.spSaldo.setSelection(index + 1)
                isMatch(norm, listOf("ovd", "overdue", "harioverdue", "telat", "keterlambatan", "dpd", "dayspastdue", "tunggakan")) -> 
                    binding.spOverdue.setSelection(index + 1)
                isMatch(norm, listOf("norangka", "nomorrangka", "rangka", "norang", "rangkakendaraan", "chassis", "chassisnumber", "vin")) -> 
                    binding.spRangka.setSelection(index + 1)
                isMatch(norm, listOf("nomesin", "nomormesin", "mesin", "nomes", "engine", "enginenumber")) -> 
                    binding.spMesin.setSelection(index + 1)
                isMatch(norm, listOf("catatan", "keterangan", "notes", "remark", "remarks", "info")) -> 
                    binding.spCatatan.setSelection(index + 1)
            }
        }
    }

    private fun isMatch(normHeader: String, aliases: List<String>): Boolean {
        return aliases.contains(normHeader)
    }

    private fun updatePreview() {
        if (fileLines.isEmpty()) return
        val map = getMapping()
        val previewText = StringBuilder()
        
        fileLines.take(5).forEach { line ->
            val parts = splitCsvLine(line, delimiter)
            val nopol = getValue(parts, map["nopol"] ?: -1)
            val nama = getValue(parts, map["nama"] ?: -1)
            val leasing = if (binding.rbManual.isChecked) {
                binding.etLeasingManual.text.toString()
            } else {
                getValue(parts, map["leasing"] ?: -1)
            }
            val saldo = getValue(parts, map["saldo"] ?: -1)
            val ovd = getValue(parts, map["overdue"] ?: -1)
            
            previewText.append("${nopol.ifEmpty { "-" }} | ${nama.ifEmpty { "-" }} | ${leasing.ifEmpty { "-" }} | Saldo: ${saldo.ifEmpty { "-" }} | OVD: ${ovd.ifEmpty { "-" }}\n")
        }
        binding.tvPreview.text = previewText.toString()
    }

    private fun getMapping(): Map<String, Int> {
        return mapOf(
            "nopol" to binding.spNopol.selectedItemPosition - 1,
            "nama" to binding.spNama.selectedItemPosition - 1,
            "leasing" to binding.spLeasing.selectedItemPosition - 1,
            "tahun" to binding.spTahun.selectedItemPosition - 1,
            "warna" to binding.spWarna.selectedItemPosition - 1,
            "saldo" to binding.spSaldo.selectedItemPosition - 1,
            "overdue" to binding.spOverdue.selectedItemPosition - 1,
            "rangka" to binding.spRangka.selectedItemPosition - 1,
            "mesin" to binding.spMesin.selectedItemPosition - 1,
            "catatan" to binding.spCatatan.selectedItemPosition - 1
        )
    }

    private fun startImport() {
        val mapping = getMapping()
        if (mapping["nopol"] == -1) {
            Toast.makeText(this, "Pilih kolom nomor polisi terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        val manualLeasing = binding.etLeasingManual.text.toString().trim().uppercase(Locale.getDefault())
        if (binding.rbManual.isChecked && manualLeasing.isEmpty()) {
            Toast.makeText(this, "Masukkan nama leasing manual", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvStatus.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true
        binding.btnStartImport.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            var newCount = 0
            var updatedCount = 0
            var failedCount = 0

            try {
                val existingKeysMap = mutableMapOf<String, Int>()
                db.kendaraanDao().getAllKendaraanList().forEach {
                    existingKeysMap["${it.nopol}|${it.leasing}"] = it.id
                }

                val newDataList = mutableListOf<Kendaraan>()
                val updateDataList = mutableListOf<Kendaraan>()

                contentResolver.openInputStream(selectedFileUri!!)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readLine() // skip header
                        var line = reader.readLine()
                        while (line != null) {
                            try {
                                val parts = splitCsvLine(line, delimiter)
                                val nopol = getValue(parts, mapping["nopol"]!!).uppercase(Locale.getDefault())
                                
                                if (nopol.isNotEmpty()) {
                                    val nama = getValue(parts, mapping["nama"]!!)
                                    val tahun = getValue(parts, mapping["tahun"]!!)
                                    val warna = getValue(parts, mapping["warna"]!!).uppercase(Locale.getDefault())
                                    val rangka = getValue(parts, mapping["rangka"]!!).uppercase(Locale.getDefault())
                                    val mesin = getValue(parts, mapping["mesin"]!!).uppercase(Locale.getDefault())
                                    val saldo = getValue(parts, mapping["saldo"]!!)
                                    val ovd = getValue(parts, mapping["overdue"]!!)
                                    val leasing = if (binding.rbManual.isChecked) manualLeasing else getValue(parts, mapping["leasing"]!!).uppercase(Locale.getDefault())
                                    val catatan = getValue(parts, mapping["catatan"]!!)
                                    
                                    if (binding.rbAuto.isChecked && leasing.isEmpty()) {
                                        failedCount++
                                        line = reader.readLine()
                                        continue
                                    }

                                    val groupNumber = extractGroup(nopol)
                                    val searchKey = generateSearchKey(nopol, nama, tahun, warna, leasing, saldo, ovd, catatan)

                                    val key = "$nopol|$leasing"
                                    val existingId = existingKeysMap[key]

                                    val kendaraan = Kendaraan(
                                        id = existingId ?: 0,
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

                                    if (existingId != null) updateDataList.add(kendaraan)
                                    else newDataList.add(kendaraan)
                                } else {
                                    failedCount++
                                }
                            } catch (e: Exception) {
                                failedCount++
                            }
                            line = reader.readLine()
                        }
                    }
                }

                db.kendaraanDao().importData(newDataList, updateDataList)
                newCount = newDataList.size
                updatedCount = updateDataList.size

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.tvResult.visibility = View.VISIBLE
                    binding.tvResult.text = "Import Selesai\nData baru: $newCount\nData diperbarui: $updatedCount\nData gagal: $failedCount"
                    Toast.makeText(this@ImportActivity, "Import selesai", Toast.LENGTH_LONG).show()
                    binding.btnStartImport.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this@ImportActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnStartImport.isEnabled = true
                }
            }
        }
    }

    private fun normalizeHeader(text: String): String {
        return text.replace("\uFEFF", "")
            .trim()
            .lowercase(Locale.getDefault())
            .replace("[^a-z0-9]".toRegex(), "")
    }

    private fun detectDelimiter(headerLine: String): Char {
        val commaCount = headerLine.count { it == ',' }
        val semicolonCount = headerLine.count { it == ';' }
        val tabCount = headerLine.count { it == '\t' }
        return when {
            semicolonCount >= commaCount && semicolonCount >= tabCount -> ';'
            tabCount >= commaCount && tabCount >= semicolonCount -> '\t'
            else -> ','
        }
    }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        return result
    }

    private fun getValue(parts: List<String>, index: Int): String {
        if (index == -1 || index >= parts.size) return ""
        return parts[index].replace("\"", "").trim()
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