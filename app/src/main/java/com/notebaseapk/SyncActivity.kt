package com.notebaseapk

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySyncBinding
    private lateinit var dbRoom: AppDatabase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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
                            binding.tvActiveUntil.text = "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
                        } else if (subStatus == "active" && activeUntil != null && activeUntil.seconds <= now.seconds) {
                            binding.tvStatusSub.text = "Status Langganan: Expired"
                            binding.tvActiveUntil.text = "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
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
                    startDataSync()
                } else {
                    Toast.makeText(this@SyncActivity, "Langganan belum aktif. Hubungi admin untuk aktivasi.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "Gagal mengecek status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDataSync() {
        Toast.makeText(this, "Sedang sinkron data...", Toast.LENGTH_SHORT).show()
        
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
                    "Sinkron selesai: $adminCount data admin, $publicCount data public, $noteCount catatan diperbarui", 
                    Toast.LENGTH_LONG).show()
                
                saveSyncTime()

            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "Gagal sinkron: ${e.message}", Toast.LENGTH_SHORT).show()
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
            // editorEmail diabaikan karena tidak ada di Entity Kendaraan

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
