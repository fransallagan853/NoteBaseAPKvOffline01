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
    private var isSubscribed = false

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
                // Re-check subscription status before sync
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
                            isSubscribed = true
                            binding.tvStatusSub.text = "Status Langganan: Aktif"
                            binding.tvActiveUntil.text = "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
                        } else if (subStatus == "active" && activeUntil != null && activeUntil.seconds <= now.seconds) {
                            isSubscribed = false
                            binding.tvStatusSub.text = "Status Langganan: Expired"
                            binding.tvActiveUntil.text = "Aktif Sampai: ${dateFormat.format(activeUntil.toDate())}"
                        } else {
                            isSubscribed = false
                            binding.tvStatusSub.text = "Status Langganan: Belum aktif"
                            binding.tvActiveUntil.text = "Aktif Sampai: -"
                        }
                    } else {
                        isSubscribed = false
                        binding.tvStatusAkun.text = "Status Akun: ${user.displayName ?: "User"}"
                        binding.tvStatusSub.text = "Status Langganan: Belum aktif"
                        binding.tvActiveUntil.text = "Aktif Sampai: -"
                    }
                }
        } else {
            isSubscribed = false
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
                    isSubscribed = true
                    startDataSync()
                } else {
                    isSubscribed = false
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
                var updateCount = 0
                val affectedNopolLeasing = mutableSetOf<String>()

                // 1. Sync public_vehicles
                val vehicleSnapshot = firestore.collection("public_vehicles")
                    .whereEqualTo("status", "approved")
                    .get().await()
                
                for (doc in vehicleSnapshot.documents) {
                    val nopol = doc.getString("nopol") ?: continue
                    val leasing = doc.getString("leasing") ?: continue
                    
                    val existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)
                    
                    val nama = doc.getString("namaKendaraan") ?: ""
                    val tahun = doc.getString("tahun") ?: ""
                    val warna = doc.getString("warna") ?: ""
                    val rangka = doc.getString("noRangka") ?: ""
                    val mesin = doc.getString("noMesin") ?: ""
                    val saldo = doc.getString("saldo") ?: ""
                    val ovd = doc.getString("overdue") ?: ""
                    val catatan = doc.getString("catatan") ?: ""
                    val pName = doc.getString("publisherName")
                    val pPhone = doc.getString("publisherPhone")

                    val groupNumber = extractGroup(nopol)
                    val searchKey = generateSearchKey(nopol, nama, tahun, warna, leasing, saldo, ovd, catatan)

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
                        saldo = saldo,
                        overdue = ovd,
                        catatan = catatan,
                        searchKey = searchKey,
                        editorName = pName,
                        editorPhone = pPhone
                    )

                    if (existing == null) {
                        dbRoom.kendaraanDao().insert(k)
                    } else {
                        dbRoom.kendaraanDao().update(k)
                    }
                    affectedNopolLeasing.add("$nopol|$leasing")
                }

                // 2. Sync public_vehicle_notes
                val noteSnapshot = firestore.collection("public_vehicle_notes")
                    .whereEqualTo("status", "approved")
                    .get().await()

                for (doc in noteSnapshot.documents) {
                    val nopol = doc.getString("nopol") ?: continue
                    val leasing = doc.getString("leasing") ?: continue
                    val noteText = doc.getString("noteText") ?: continue
                    val editorName = doc.getString("editorName")
                    val editorPhone = doc.getString("editorPhone")

                    val existing = dbRoom.kendaraanDao().getKendaraanByNopolAndLeasing(nopol, leasing)
                    if (existing != null) {
                        val updated = existing.copy(
                            catatan = noteText,
                            editorName = editorName,
                            editorPhone = editorPhone
                        )
                        dbRoom.kendaraanDao().update(updated)
                        affectedNopolLeasing.add("$nopol|$leasing")
                    }
                }

                updateCount = affectedNopolLeasing.size

                if (updateCount > 0) {
                    Toast.makeText(this@SyncActivity, "Sinkron selesai: $updateCount data diperbarui", Toast.LENGTH_SHORT).show()
                    saveSyncTime()
                } else {
                    Toast.makeText(this@SyncActivity, "Tidak ada update baru", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@SyncActivity, "Gagal sinkron: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
