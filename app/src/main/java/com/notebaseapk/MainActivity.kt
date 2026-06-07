package com.notebaseapk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notebaseapk.adapter.GroupAdapter
import com.notebaseapk.adapter.NopolAdapter
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.data.Kendaraan
import com.notebaseapk.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.constraintlayout.widget.ConstraintLayout

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var nopolAdapter: NopolAdapter
    private var searchJob: Job? = null
    private var inflatedKeyboard: View? = null
    private lateinit var customKeyboardManager: CustomKeyboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSafeBottomNav()

        db = AppDatabase.getDatabase(this)
        setupRecyclerViews()
        setupSearch()
        setupFab()
        setupMenu()
        setupBottomNav()
        observeStats()
        
        performSearch("")
        // checkAndInsertDummyData()
    }

    override fun onStart() {
        super.onStart()
        if (::customKeyboardManager.isInitialized) {
            customKeyboardManager.refreshLayout()
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

    private fun setupRecyclerViews() {
        groupAdapter = GroupAdapter(emptyList()) { group ->
            val intent = Intent(this, GroupDetailActivity::class.java)
            intent.putExtra("GROUP_NUMBER", group.groupNumber)
            startActivity(intent)
        }
        
        nopolAdapter = NopolAdapter(emptyList()) { kendaraan ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("VEHICLE_ID", kendaraan.id)
            startActivity(intent)
        }

        binding.rvGroup.layoutManager = LinearLayoutManager(this)
        binding.rvGroup.adapter = groupAdapter
    }

    private fun setupSearch() {
        customKeyboardManager = CustomKeyboardManager(
            activity = this,
            keyboardContainer = binding.keyboardContainer
        )

        customKeyboardManager.setup(
            listOf(binding.etSearch)
        )

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }



    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            if (query.isBlank()) {
                binding.tvSearchLabel.visibility = View.GONE
                binding.rvGroup.adapter = groupAdapter
                db.kendaraanDao().getGroupNopol("").collectLatest {
                    groupAdapter.updateData(it)
                }
            } else {
                val isNumeric = query.all { it.isDigit() }
                if (isNumeric && query.length < 4) {
                    binding.tvSearchLabel.visibility = View.VISIBLE
                    binding.tvSearchLabel.text = "Hasil Group"
                    binding.rvGroup.adapter = groupAdapter
                    db.kendaraanDao().getGroupNopol(query).collectLatest {
                        groupAdapter.updateData(it)
                    }
                } else {
                    binding.tvSearchLabel.visibility = View.VISIBLE
                    binding.tvSearchLabel.text = "Hasil Data"
                    binding.rvGroup.adapter = nopolAdapter
                    val normalized = normalizeSearchText(query)
                    db.kendaraanDao().searchKendaraanSpesifik(normalized).collectLatest {
                        nopolAdapter.updateData(it)
                    }
                }
            }
        }
    }

    private fun normalizeSearchText(text: String): String {
        return text.uppercase(Locale.getDefault())
            .replace("[^A-Z0-9]".toRegex(), "")
            .trim()
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditActivity::class.java))
        }
    }

    private fun setupMenu() {
        binding.btnMenu.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("Pengaturan Keyboard")
            popup.menu.add("Pengaturan Aplikasi")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Pengaturan Keyboard" -> startActivity(Intent(this, KeyboardSettingsActivity::class.java))
                    "Pengaturan Aplikasi" -> startActivity(Intent(this, SettingsActivity::class.java))
                }
                true
            }
            popup.show()
        }
    }

    private fun setupBottomNav() {
        binding.menuHome.setOnClickListener {
            performSearch("")
        }
        binding.menuSync.setOnClickListener {
            startActivity(Intent(this, SyncActivity::class.java))
            overridePendingTransition(0, 0)
        }
        binding.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(0, 0)
        }
    }

    private fun observeStats() {
        lifecycleScope.launch {
            db.kendaraanDao().getCount().collectLatest { count ->
                binding.tvTotalData.text = "$count Data Tersimpan"
            }
        }
    }

    private fun checkAndInsertDummyData() {
        lifecycleScope.launch {
            val count = db.kendaraanDao().getCountSync()
            if (count == 0) {
                val mandatory = listOf(
                    Triple("B 6372 EDC", "Toyota Fortuner", "ADIRA"),
                    Triple("B 6372 EFC", "Toyota Rush", "OTTO"),
                    Triple("D 6372 SCS", "Honda Jazz", "BCA")
                )
                db.kendaraanDao().insertAll(mandatory.mapIndexed { _, triple ->
                    val nopol = triple.first
                    Kendaraan(
                        nopol = nopol,
                        groupNumber = extractGroup(nopol),
                        namaKendaraan = triple.second,
                        leasing = triple.third,
                        searchKey = generateSearchKey(nopol, triple.second, triple.third),
                        catatan = "Unit dummy"
                    )
                })
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

    override fun onResume() {
        super.onResume()
        performSearch(binding.etSearch.text.toString())
    }
}
