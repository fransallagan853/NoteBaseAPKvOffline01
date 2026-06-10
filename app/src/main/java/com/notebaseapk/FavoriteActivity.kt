package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notebaseapk.adapter.NopolAdapter
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.databinding.ActivityFavoriteBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoriteBinding
    private lateinit var db: AppDatabase
    private lateinit var adapter: NopolAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)

        binding.btnBack.setOnClickListener {
            finish()
        }

        setupRecyclerView()
        observeFavorites()
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

    private fun observeFavorites() {
        lifecycleScope.launch {
            db.favoriteVehicleDao().getFavoriteKendaraanList().collectLatest { list ->
                adapter.updateData(list)

                if (list.isEmpty()) {
                    binding.tvEmptyFavorite.visibility = View.VISIBLE
                    binding.rvFavorite.visibility = View.GONE
                } else {
                    binding.tvEmptyFavorite.visibility = View.GONE
                    binding.rvFavorite.visibility = View.VISIBLE
                }

                binding.tvFavoriteCount.text = "${list.size} data favorit"
            }
        }
    }
}