package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notebaseapk.adapter.NopolAdapter
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityFavoriteBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: NopolAdapter
    private var observeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.headerLayout.applyStatusBarPadding(16)
        ViewCompat.requestApplyInsets(binding.headerLayout)

        db = AppDatabase.getDatabase(this)

        setupSafeBottomNav()
        setupRecyclerView()
        setupBottomNav()
        showCatatan()

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupSafeBottomNav() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            val navBarHeight = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars()
            ).bottom

            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight
            view.layoutParams = params

            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = NopolAdapter(emptyList()) { kendaraan ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("VEHICLE_ID", kendaraan.id)
            startActivity(intent)
        }

        binding.rvFavorite.layoutManager = LinearLayoutManager(this)
        binding.rvFavorite.adapter = adapter
    }

    private fun showCatatan() {
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            db.favoriteVehicleDao()
                .getCatatanKendaraanList()
                .collectLatest { list ->
                    showList(list)
                }
        }
    }

    private fun showList(list: List<Kendaraan>) {
        adapter.updateData(list)

        if (list.isEmpty()) {
            binding.tvEmptyFavorite.text = "Belum ada data catatan"
            binding.tvEmptyFavorite.visibility = View.VISIBLE
            binding.rvFavorite.visibility = View.GONE
        } else {
            binding.tvEmptyFavorite.visibility = View.GONE
            binding.rvFavorite.visibility = View.VISIBLE
        }

        binding.tvFavoriteCount.text = "${list.size} data catatan"
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
        }

        binding.menuFavorite.setOnClickListener {
            // Sudah berada di halaman Catatan.
        }

        binding.menuSync.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }

        binding.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }
}
