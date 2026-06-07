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
                val doc = firestore.collection("users").document(uid).get().await()
                val subStatus = doc.getString("subscriptionStatus") ?: "inactive"
                val activeUntil = doc.getTimestamp("activeUntil")
                val now = Timestamp.now()

                if (subStatus == "active" && activeUntil != null && activeUntil.seconds > now.seconds) {
                    // Tahap 1: Cek metadata server
                    checkServerUpdate()
                } else {
                    Toast.makeText(
                        this@SyncActivity,
                        "Langganan belum aktif. Hubungi admin untuk aktivasi.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
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

            if (gzipFile.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncActivity, "Tidak ada file update di server", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Pengecekan Version Code untuk Efisiensi
            val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
            val lastImportedVersion = prefs.getLong("last_server_version_code", 0L)

            if (versionCode != 0L && versionCode == lastImportedVersion) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncActivity, "Data sudah versi terbaru", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // Lanjut ke tahap download dan extract dengan membawa versionCode baru
            downloadAndExtractUpdate(gzipFile, totalRows, versionCode)

        } catch (e: Exception) {
            Log.e("SyncActivity", "Error checkServerUpdate", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@SyncActivity, "Gagal cek update server: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun downloadAndExtractUpdate(gzipFile: String, totalRows: Int, versionCode: Long) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Persiapkan folder internal (filesDir/updates)
                val updatesDir = File(filesDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val gzippedFile = File(updatesDir, gzipFile)
                val extractedFile = File(updatesDir, "notebase_update.sqlite")

                // 2. Download file .gz dari server
                val request = Request.Builder()
                    .url("http://192.168.18.34:5000/api/update/download/$gzipFile")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Gagal download: ${response.code}")

                    val body = response.body ?: throw Exception("Isi file kosong")
                    body.byteStream().use { input ->
                        FileOutputStream(gzippedFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // 3. Extract Gzip menjadi file SQLite mentah
                FileInputStream(gzippedFile).use { fis ->
                    GZIPInputStream(fis).use { gzis ->
                        FileOutputStream(extractedFile).use { fos ->
                            gzis.copyTo(fos)
                        }
                    }
                }

                // Lanjut Tahap 3: Import ke Room dengan membawa versionCode
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
                    Toast.makeText(this@SyncActivity, "Sedang mengimpor data ke database lokal...", Toast.LENGTH_SHORT).show()
                }

                sqliteDb = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                cursor = sqliteDb.rawQuery("SELECT * FROM kendaraan_update", null)

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        val nopol = cursor.getString(cursor.getColumnIndexOrThrow("nopol")) ?: ""
                        if (nopol.isBlank()) continue

                        val leasing = cursor.getString(cursor.getColumnIndexOrThrow("leasing")) ?: ""
                        val cabang = cursor.getString(cursor.getColumnIndexOrThrow("cabang")) ?: ""

                        // Cek data lama di Room menggunakan nopol + leasing + cabang
                        var existing = dbRoom.kendaraanDao().getKendaraanByNopolLeasingCabang(nopol, leasing, cabang)
                        if (existing == null) {
                            // Fallback ke nopol + leasing
                            existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)
                        }

                        val nama = cursor.getString(cursor.getColumnIndexOrThrow("namaKendaraan")) ?: ""
                        val groupNumber = cursor.getString(cursor.getColumnIndexOrThrow("groupNumber")) ?: extractGroup(nopol)
                        val tahun = cursor.getString(cursor.getColumnIndexOrThrow("tahun")) ?: ""
                        val warna = cursor.getString(cursor.getColumnIndexOrThrow("warna")) ?: ""
                        val rangka = cursor.getString(cursor.getColumnIndexOrThrow("noRangka")) ?: ""
                        val mesin = cursor.getString(cursor.getColumnIndexOrThrow("noMesin")) ?: ""
                        val saldo = cursor.getString(cursor.getColumnIndexOrThrow("saldo")) ?: ""
                        val overdue = cursor.getString(cursor.getColumnIndexOrThrow("overdue")) ?: ""
                        val catatan = cursor.getString(cursor.getColumnIndexOrThrow("catatan")) ?: ""
                        var searchKey = cursor.getString(cursor.getColumnIndexOrThrow("searchKey")) ?: ""

                        if (searchKey.isBlank()) {
                            searchKey = generateSearchKey(nopol, nama, leasing, cabang)
                        }

                        // Buat objek Kendaraan baru
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
                            dbRoom.kendaraanDao().insert(k)
                        } else {
                            dbRoom.kendaraanDao().update(k)
                        }
                        importCount++

                    } while (cursor.moveToNext())
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SyncActivity, "Import selesai: $importCount data masuk", Toast.LENGTH_LONG).show()

                    // Simpan versionCode yang baru saja berhasil diimport ke SharedPreferences
                    val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putLong("last_server_version_code", versionCode).apply()

                    saveSyncTime()
                }

            } catch (e: Exception) {
                Log.e("SyncActivity", "Error importSqliteUpdateToRoom", e)
                withContext(Dispatchers.Main) {
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

                // 1. Sync admin_vehicles
                val adminSnapshot = firestore.collection("admin_vehicles")
                    .whereEqualTo("status", "approved")
                    .get().await()

                for (doc in adminSnapshot.documents) {
                    if (upsertVehicle(doc)) adminCount++
                }

                // 2. Sync public_vehicles
                val publicSnapshot = firestore.collection("public_vehicles")
                    .whereEqualTo("status", "approved")
                    .get().await()

                for (doc in publicSnapshot.documents) {
                    if (upsertVehicle(doc)) publicCount++
                }

                // 3. Sync public_vehicle_notes
                val noteSnapshot = firestore.collection("public_vehicle_notes")
                    .whereEqualTo("status", "approved")
                    .get().await()

                for (doc in noteSnapshot.documents) {
                    if (updateNote(doc)) noteCount++
                }

                Toast.makeText(this@SyncActivity,
                    "Sinkron Firebase selesai: $adminCount data admin, $publicCount data public, $noteCount catatan diperbarui",
                    Toast.LENGTH_LONG).show()

                saveSyncTime()

            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "Gagal sinkron Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun upsertVehicle(doc: DocumentSnapshot): Boolean {
        val nopol = doc.getString("nopol") ?: return false
        val leasing = doc.getString("leasing") ?: return false
        val cabang = doc.getString("cabang") ?: ""

        // Cek data lama berdasarkan nopol + leasing (Room Unique Constraint)
        val existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)

        val nama = doc.getString("namaKendaraan") ?: ""
        val groupNumber = doc.getString("groupNumber") ?: extractGroup(nopol)
        val tahun = doc.getString("tahun") ?: ""
        val warna = doc.getString("warna") ?: ""
        val rangka = doc.getString("noRangka") ?: ""
        val mesin = doc.getString("noMesin") ?: ""
        val saldo = doc.getString("saldo") ?: ""
        val overdue = doc.getString("overdue") ?: ""
        val catatan = doc.getString("catatan") ?: ""

        // Metadata publisher (untuk public_vehicles)
        val pName = doc.getString("publisherName")
        val pPhone = doc.getString("publisherPhone")

        val searchKey = doc.getString("searchKey") ?: generateSearchKey(nopol, nama, leasing, cabang)

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
            editorName = pName ?: existing?.editorName,
            editorPhone = pPhone ?: existing?.editorPhone
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

        // Cari kendaraan lokal berdasarkan nopol + leasing + cabang (jika ada)
        var existing = dbRoom.kendaraanDao().getKendaraanByNopolLeasingCabang(nopol, leasing, cabang)
        if (existing == null) {
            // Fallback ke nopol + leasing
            existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)
        }

        if (existing != null) {
            val noteText = doc.getString("noteText") ?: return false
            val editorName = doc.getString("editorName")
            val editorPhone = doc.getString("editorPhone")

            val updated = existing.copy(
                catatan = noteText,
                editorName = editorName ?: existing.editorName,
                editorPhone = editorPhone ?: existing.editorPhone
            )
            dbRoom.kendaraanDao().update(updated)
            return true
        }
        return false
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

    private fun saveSyncTime() {
        val now = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date())
        val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_sync_time", now).apply()
        binding.tvUpdateTerakhir.text = "Update Terakhir: $now"
    }

    private fun loadLastSyncTime() {
        val prefs = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val lastSync = prefs.getString("last_sync_time", "-")
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
