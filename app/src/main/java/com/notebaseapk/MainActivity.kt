package com.notebaseapk

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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
        checkAndInsertDummyData()
    }

    override fun onStart() {
        super.onStart()
        setupCustomKeyboard()
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
        // Force hide system keyboard
        binding.etSearch.showSoftInputOnFocus = false
        
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.keyboardContainer.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }
        
        binding.etSearch.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.requestFocus()
                binding.keyboardContainer.visibility = View.VISIBLE
            }
            false
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCustomKeyboard() {
        val sharedPref = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val layoutKey = sharedPref.getString("keyboard_layout", "default") ?: "default"
        
        val layoutRes = when (layoutKey) {
            "qwerty_numpad" -> R.layout.layout_keyboard_qwerty_numpad
            "numpad_top" -> R.layout.layout_keyboard_numpad_top
            "numpad_bottom" -> R.layout.layout_keyboard_numpad_bottom
            else -> R.layout.layout_keyboard_default
        }

        binding.keyboardContainer.removeAllViews()
        val view = LayoutInflater.from(this).inflate(layoutRes, binding.keyboardContainer, true)

        // Setup all character buttons
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,
            R.id.btnQ, R.id.btnW, R.id.btnE, R.id.btnR, R.id.btnT, R.id.btnY, R.id.btnU, R.id.btnI, R.id.btnO, R.id.btnP,
            R.id.btnA, R.id.btnS, R.id.btnD, R.id.btnF, R.id.btnG, R.id.btnH, R.id.btnJ, R.id.btnK, R.id.btnL,
            R.id.btnZ, R.id.btnX, R.id.btnC, R.id.btnV, R.id.btnB, R.id.btnN, R.id.btnM
        )

        buttonIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener {
                if (it is Button) {
                    appendSearchText(it.text.toString())
                }
            }
        }

        view.findViewById<View>(R.id.btnClear)?.setOnClickListener { clearSearch() }
        view.findViewById<View>(R.id.btnBackspace)?.setOnClickListener { deleteChar() }
        view.findViewById<View>(R.id.btnSearch)?.setOnClickListener {
            binding.keyboardContainer.visibility = View.GONE
            binding.etSearch.clearFocus()
        }
    }

    private fun appendSearchText(value: String) {
        val start = binding.etSearch.selectionStart
        val end = binding.etSearch.selectionEnd
        binding.etSearch.text.replace(start, end, value)
    }

    private fun deleteChar() {
        val start = binding.etSearch.selectionStart
        val end = binding.etSearch.selectionEnd
        if (start > 0 || start != end) {
            binding.etSearch.text.delete(if (start == end) start - 1 else start, end)
        }
    }

    private fun clearSearch() {
        binding.etSearch.setText("")
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
            .replace("\\s".toRegex(), "")
            .replace("-", "")
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
            popup.menu.add("Import Data")
            popup.menu.add("Pengaturan")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Import Data" -> startActivity(Intent(this, ImportActivity::class.java))
                    "Pengaturan" -> startActivity(Intent(this, SettingsActivity::class.java))
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
                val dummyList = mutableListOf<Kendaraan>()
                val mandatory = listOf(
                    Triple("B 6372 EDC", "Toyota Fortuner", "ADIRA"),
                    Triple("B 6372 EFC", "Toyota Rush", "OTTO"),
                    Triple("D 6372 SCS", "Honda Jazz", "BCA")
                )

                for (d in mandatory) {
                    val nopol = d.first
                    val nama = d.second
                    val leasing = d.third
                    val group = extractGroup(nopol)
                    val searchKey = generateSearchKey(nopol, nama, leasing)
                    
                    dummyList.add(Kendaraan(
                        nopol = nopol,
                        groupNumber = group,
                        namaKendaraan = nama,
                        leasing = leasing,
                        searchKey = searchKey,
                        catatan = "Unit dummy"
                    ))
                }
                db.kendaraanDao().insertAll(mandatory.mapIndexed { index, triple ->
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
