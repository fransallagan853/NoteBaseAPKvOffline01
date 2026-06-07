package com.notebaseapk

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivitySyncBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

class SyncActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncBinding
    private lateinit var dbRoom: AppDatabase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbRoom = AppDatabase.getDatabase(this)
        setupSafeBottomNav()

        binding.btnBack.setOnClickListener { finish() }

        updateUI()
        loadLastSyncTime()

        binding.btnCheckUpdate.setOnClickListener {
            val user = auth.currentUser
            if (user == null) {
                Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            } else {
                showSyncProgress(0, "Mengecek akun...")
                checkSubscriptionAndSync(user.uid)
            }
        }

        binding.btnLoginAction.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        setupBottomNav()
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

    private fun showSyncProgress(progress: Int, status: String) {
        runOnUiThread {
            binding.layoutSyncProgress.visibility = View.VISIBLE
            binding.syncProgressIndicator.setProgressCompat(progress, true)
            binding.tvSyncPercent.text = "$progress%"
            binding.tvSyncStatus.text = status
            binding.btnCheckUpdate.isEnabled = false
        }
    }

    private fun updateSyncProgress(progress: Int, status: String) {
        runOnUiThread {
            binding.layoutSyncProgress.visibility = View.VISIBLE
            binding.syncProgressIndicator.setProgressCompat(progress, true)
            binding.tvSyncPercent.text = "$progress%"
            binding.tvSyncStatus.text = status
        }
    }

    private fun finishSyncProgress(progress: Int, status: String) {
        runOnUiThread {
            binding.layoutSyncProgress.visibility = View.VISIBLE
            binding.syncProgressIndicator.setProgressCompat(progress, true)
            binding.tvSyncPercent.text = "$progress%"
            binding.tvSyncStatus.text = status
            binding.btnCheckUpdate.isEnabled = true
        }
    }

    private fun updateUI() {
        val user = auth.currentUser
        if (user != null) {
            binding.btnLoginAction.visibility = View.GONE
            binding.layoutAccountInfo.visibility = View.VISIBLE

            firestore.collection("users").document(user.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: user.displayName ?: "User"
                        val email = doc.getString("email") ?: user.email ?: "-"
                        val phone = doc.getString("phone") ?: "-"
                        val subStatus = doc.getString("subscriptionStatus") ?: "inactive"
                        val activeUntil = doc.getTimestamp("activeUntil")

                        binding.tvStatusAkun.text = "Status Akun: $name"
                        binding.tvUserEmail.text = "Gmail: $email"
                        binding.tvUserPhone.text = "No Telepon: $phone"

                        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
                        val now = Timestamp.now()

                        if (subStatus == "active" && activeUntil != null && activeUntil.seconds > now.seconds) {
                            binding.tvStatusSub.text = "Status Langganan: Aktif"
                            binding.tvActiveUntil.text =
                                "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
                        } else if (subStatus == "active" && activeUntil != null && activeUntil.seconds <= now.seconds) {
                            binding.tvStatusSub.text = "Status Langganan: Expired"
                            binding.tvActiveUntil.text =
                                "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
                        } else {
                            binding.tvStatusSub.text = "Status Langganan: Belum aktif"
                            binding.tvActiveUntil.text = "Aktif Sampai: -"
                        }
                    } else {
                        binding.tvStatusAkun.text = "Status Akun: ${user.displayName ?: "User"}"
                        binding.tvStatusSub.text = "Status Langganan: Belum aktif"
                        binding.tvActiveUntil.text = "Aktif Sampai: -"
                    }
                }
        } else {
            binding.btnLoginAction.visibility = View.VISIBLE
            binding.layoutAccountInfo.visibility = View.GONE
            binding.tvStatusAkun.text = "Status Akun: Belum login"
            binding.tvStatusSub.text = "Status Langganan: Belum aktif"
            binding.tvActiveUntil.text = "Aktif Sampai: -"
        }
    }

    private fun checkSubscriptionAndSync(uid: String) {
        lifecycleScope.launch {
            try {
                updateSyncProgress(5, "Mengecek status langganan...")
                val doc = firestore.collection("users").document(uid).get().await()
                val subStatus = doc.getString("subscriptionStatus") ?: "inactive"
                val activeUntil = doc.getTimestamp("activeUntil")
                val now = Timestamp.now()

                if (subStatus == "active" && activeUntil != null && activeUntil.seconds > now.seconds) {
                    checkServerUpdate()
                } else {
                    finishSyncProgress(0, "Langganan belum aktif")
                    Toast.makeText(
                        this@SyncActivity,
                        "Langganan belum aktif. Hubungi admin untuk aktivasi.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                finishSyncProgress(0, "Gagal mengecek langganan")
                Toast.makeText(
                    this@SyncActivity,
                    "Gagal mengecek status: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private suspend fun checkServerUpdate() {
        try {
            updateSyncProgress(10, "Mengecek update server...")
            val resultText = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("http://192.168.18.34:5000/api/update/latest")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Server error: ${response.code}")
                    response.body?.string() ?: throw Exception("Response server kosong")
                }
            }

            val json = JSONObject(resultText)
            val data = json.optJSONObject("data") ?: throw Exception("Data update tidak ditemukan")

            val versionCode = data.optLong("versionCode", 0L)
            val totalRows = data.optInt("totalRows", 0)
            val gzipFile = data.optString("gzipFile", "")
            updateSyncProgress(20, "Update ditemukan: $totalRows data")

            if (gzipFile.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncActivity, "Tidak ada file update di server", Toast.LENGTH_SHORT).show()
                }
                return
            }

            val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
            val lastImportedVersion = prefs.getLong("last_server_version_code", 0L)

            if (versionCode != 0L && versionCode == lastImportedVersion) {
                withContext(Dispatchers.Main) {
                    finishSyncProgress(100, "Data sudah versi terbaru")
                    Toast.makeText(this@SyncActivity, "Data sudah versi terbaru", Toast.LENGTH_SHORT).show()
                }
                return
            }

            downloadAndExtractUpdate(gzipFile, totalRows, versionCode)

        } catch (e: Exception) {
            Log.e("SyncActivity", "Error checkServerUpdate", e)
            withContext(Dispatchers.Main) {
                finishSyncProgress(0, "Gagal cek update server")
                Toast.makeText(this@SyncActivity, "Gagal cek update server: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadAndExtractUpdate(gzipFile: String, totalRows: Int, versionCode: Long) {
        withContext(Dispatchers.IO) {
            try {
                val updatesDir = File(filesDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val gzippedFile = File(updatesDir, gzipFile)
                val extractedFile = File(updatesDir, "notebase_update.sqlite")

                val request = Request.Builder()
                    .url("http://192.168.18.34:5000/api/update/download/$gzipFile")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Gagal download: ${response.code}")

                    val body = response.body ?: throw Exception("Isi file kosong")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    val buffer = ByteArray(8 * 1024)

                    body.byteStream().use { input ->
                        FileOutputStream(gzippedFile).use { output ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break

                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                if (totalBytes > 0) {
                                    val downloadProgress =
                                        25 + ((downloadedBytes * 25) / totalBytes).toInt()
                                    updateSyncProgress(
                                        downloadProgress.coerceIn(25, 50),
                                        "Mengunduh file update..."
                                    )
                                }
                            }
                        }
                    }
                }

                FileInputStream(gzippedFile).use { fis ->
                    GZIPInputStream(fis).use { gzis ->
                        FileOutputStream(extractedFile).use { fos ->
                            gzis.copyTo(fos)
                        }
                    }
                }

                importSqliteUpdateToRoom(extractedFile, versionCode)

            } catch (e: Exception) {
                Log.e("SyncActivity", "Error downloadAndExtractUpdate", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncActivity, "Gagal download update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun importSqliteUpdateToRoom(sqliteFile: File, versionCode: Long) {
        withContext(Dispatchers.IO) {
            var importCount = 0
            var sqliteDb: SQLiteDatabase? = null
            var cursor: android.database.Cursor? = null

            try {
                withContext(Dispatchers.Main) {
                    updateSyncProgress(65, "Mengimpor data...")
                }

                // 1. Ambil data lama ke memori untuk pencocokan cepat (nopol|leasing)
                // Ini jauh lebih cepat daripada query satu per satu di dalam loop
                val existingList = dbRoom.kendaraanDao().getAllKendaraanList()
                val existingMap = existingList.associateBy { "${it.nopol}|${it.leasing}" }

                sqliteDb = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                cursor = sqliteDb.rawQuery("SELECT * FROM kendaraan_update", null)
                val totalImportRows = cursor?.count ?: 0
                val toInsert = mutableListOf<Kendaraan>()
                val toUpdate = mutableListOf<Kendaraan>()
                val batchSize = 5000

                if (cursor != null && cursor.moveToFirst()) {
                    // Ambil column index sekali saja
                    val colNopol = cursor.getColumnIndexOrThrow("nopol")
                    val colLeasing = cursor.getColumnIndexOrThrow("leasing")
                    val colCabang = cursor.getColumnIndexOrThrow("cabang")
                    val colNama = cursor.getColumnIndexOrThrow("namaKendaraan")
                    val colGroup = cursor.getColumnIndexOrThrow("groupNumber")
                    val colTahun = cursor.getColumnIndexOrThrow("tahun")
                    val colWarna = cursor.getColumnIndexOrThrow("warna")
                    val colRangka = cursor.getColumnIndexOrThrow("noRangka")
                    val colMesin = cursor.getColumnIndexOrThrow("noMesin")
                    val colSaldo = cursor.getColumnIndexOrThrow("saldo")
                    val colOverdue = cursor.getColumnIndexOrThrow("overdue")
                    val colCatatan = cursor.getColumnIndexOrThrow("catatan")
                    val colSearchKey = cursor.getColumnIndexOrThrow("searchKey")

                    do {
                        val nopol = cursor.getString(colNopol) ?: ""
                        if (nopol.isBlank()) continue

                        val leasing = cursor.getString(colLeasing) ?: ""
                        val cabang = cursor.getString(colCabang) ?: ""

                        // Pencocokan cepat via Map
                        val existing = existingMap["$nopol|$leasing"]

                        val nama = cursor.getString(colNama) ?: ""
                        val groupNumber = cursor.getString(colGroup) ?: extractGroup(nopol)
                        val tahun = cursor.getString(colTahun) ?: ""
                        val warna = cursor.getString(colWarna) ?: ""
                        val rangka = cursor.getString(colRangka) ?: ""
                        val mesin = cursor.getString(colMesin) ?: ""
                        val saldo = cursor.getString(colSaldo) ?: ""
                        val overdue = cursor.getString(colOverdue) ?: ""
                        val catatan = cursor.getString(colCatatan) ?: ""
                        var searchKey = cursor.getString(colSearchKey) ?: ""

                        if (searchKey.isBlank()) {
                            searchKey = generateSearchKey(
                                nopol,
                                nama,
                                tahun,
                                warna,
                                rangka,
                                mesin,
                                leasing,
                                cabang,
                                saldo,
                                overdue,
                                catatan
                            )
                        }

                        val k = Kendaraan(
                            id = existing?.id ?: 0,
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
                            overdue = overdue,
                            catatan = catatan,
                            searchKey = searchKey,
                            editorName = existing?.editorName,
                            editorPhone = existing?.editorPhone
                        )

                        if (existing == null) {
                            toInsert.add(k)
                        } else {
                            toUpdate.add(k)
                        }
                        importCount++

                        if (importCount % 500 == 0 && totalImportRows > 0) {
                            val importProgress = 65 + ((importCount * 30) / totalImportRows)
                            updateSyncProgress(
                                importProgress.coerceIn(65, 95),
                                "Mengimpor data... $importCount/$totalImportRows"
                            )
                        }
                        // Eksekusi batch jika sudah mencapai batchSize
                        if (toInsert.size + toUpdate.size >= batchSize) {
                            dbRoom.kendaraanDao().importData(toInsert, toUpdate)
                            toInsert.clear()
                            toUpdate.clear()
                        }

                    } while (cursor.moveToNext())
                }

                // Masukkan sisa data yang belum terproses
                if (toInsert.isNotEmpty() || toUpdate.isNotEmpty()) {
                    dbRoom.kendaraanDao().importData(toInsert, toUpdate)
                }

                withContext(Dispatchers.Main) {
                    finishSyncProgress(100, "Sinkron selesai")
                    Toast.makeText(this@SyncActivity, "Import selesai: $importCount data masuk", Toast.LENGTH_LONG).show()
                    
                    val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_server_version_code", versionCode).apply()

                    saveSyncTime()
                }

            } catch (e: Exception) {
                Log.e("SyncActivity", "Error importSqliteUpdateToRoom", e)
                withContext(Dispatchers.Main) {
                    finishSyncProgress(0, "Gagal import data")
                    Toast.makeText(this@SyncActivity, "Gagal import data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                cursor?.close()
                sqliteDb?.close()
            }
        }
    }

    private fun startDataSync() {
        Toast.makeText(this, "Sedang sinkron data Firebase...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                var adminCount = 0
                var publicCount = 0
                var noteCount = 0
                val adminSnapshot = firestore.collection("admin_vehicles").whereEqualTo("status", "approved").get().await()
                for (doc in adminSnapshot.documents) { if (upsertVehicle(doc)) adminCount++ }
                val publicSnapshot = firestore.collection("public_vehicles").whereEqualTo("status", "approved").get().await()
                for (doc in publicSnapshot.documents) { if (upsertVehicle(doc)) publicCount++ }
                val noteSnapshot = firestore.collection("public_vehicle_notes").whereEqualTo("status", "approved").get().await()
                for (doc in noteSnapshot.documents) { if (updateNote(doc)) noteCount++ }
                Toast.makeText(this@SyncActivity, "Sinkron Firebase selesai", Toast.LENGTH_LONG).show()
                saveSyncTime()
            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "Gagal sinkron Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun upsertVehicle(doc: DocumentSnapshot): Boolean {
        val nopol = doc.getString("nopol") ?: return false
        val leasing = doc.getString("leasing") ?: return false

        val existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)

        val nama = doc.getString("namaKendaraan") ?: ""
        val groupNumber = doc.getString("groupNumber") ?: extractGroup(nopol)
        val tahun = doc.getString("tahun") ?: ""
        val warna = doc.getString("warna") ?: ""
        val rangka = doc.getString("noRangka") ?: ""
        val mesin = doc.getString("noMesin") ?: ""
        val cabang = doc.getString("cabang") ?: ""
        val saldo = doc.getString("saldo") ?: ""
        val overdue = doc.getString("overdue") ?: ""
        val catatan = doc.getString("catatan") ?: ""

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
            overdue,
            catatan
        )

        val k = Kendaraan(
            id = existing?.id ?: 0,
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
            overdue = overdue,
            catatan = catatan,
            searchKey = searchKey,
            editorName = doc.getString("publisherName") ?: existing?.editorName,
            editorPhone = doc.getString("publisherPhone") ?: existing?.editorPhone
        )

        if (existing == null) {
            dbRoom.kendaraanDao().insert(k)
        } else {
            dbRoom.kendaraanDao().update(k)
        }

        return true
    }

    private suspend fun updateNote(doc: DocumentSnapshot): Boolean {
        val nopol = doc.getString("nopol") ?: return false
        val leasing = doc.getString("leasing") ?: return false
        val cabang = doc.getString("cabang") ?: ""
        var existing = dbRoom.kendaraanDao().getKendaraanByNopolLeasingCabang(nopol, leasing, cabang)
        if (existing == null) existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)
        if (existing != null) {
            val updated = existing.copy(
                catatan = doc.getString("noteText") ?: existing.catatan,
                editorName = doc.getString("editorName") ?: existing.editorName,
                editorPhone = doc.getString("editorPhone") ?: existing.editorPhone
            )
            dbRoom.kendaraanDao().update(updated)
            return true
        }
        return false
    }

    private fun extractGroup(nopol: String): String {
        val regex = "\\d+".toRegex()
        return regex.find(nopol)?.value ?: "0000"
    }

    private fun generateSearchKey(vararg fields: String): String {
        return fields.joinToString("") {
            it.uppercase(Locale.getDefault())
                .replace("[^A-Z0-9]".toRegex(), "")
        }
    }

    private fun saveSyncTime() {
        val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date())
        getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE).edit().putString("last_sync_time", now).apply()
        binding.tvUpdateTerakhir.text = "Update Terakhir: $now"
    }

    private fun loadLastSyncTime() {
        val lastSync = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE).getString("last_sync_time", "-")
        binding.tvUpdateTerakhir.text = "Update Terakhir: $lastSync"
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        binding.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }
}
