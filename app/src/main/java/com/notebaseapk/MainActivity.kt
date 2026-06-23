package com.notebaseapk

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
import com.notebaseapk.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import com.notebaseapk.util.NopolFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var groupAdapter: GroupAdapter
    private lateinit var nopolAdapter: NopolAdapter
    private var searchJob: Job? = null
    private lateinit var customKeyboardManager: CustomKeyboardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clean & Responsive UI using Extensions
        binding.headerLayout.applyStatusBarPadding()
        binding.bottomNav.applyNavigationBarMargin()
        binding.keyboardContainer.applyNavigationBarMargin(4)

        db = AppDatabase.getDatabase(this)
        setupRecyclerViews()
        setupSearch()
        setupFab()
        setupMenu()
        setupBottomNav()
        
        performSearch("")
    }

    override fun onStart() {
        super.onStart()
        if (::customKeyboardManager.isInitialized) {
            customKeyboardManager.refreshLayout()
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
            val cleanQuery = query.trim()

            hideAddFromSearchButton()

            if (cleanQuery.isBlank()) {
                binding.tvSearchLabel.visibility = View.GONE
                binding.rvGroup.adapter = groupAdapter

                db.kendaraanDao()
                    .getGroupNopol("")
                    .collectLatest { list ->
                        groupAdapter.updateData(list)
                        hideAddFromSearchButton()
                    }

                return@launch
            }

            val isNumeric = cleanQuery.all {
                it.isDigit()
            }

            when {
                // 1–3 angka tetap menampilkan daftar group.
                isNumeric && cleanQuery.length < 4 -> {
                    binding.tvSearchLabel.visibility = View.VISIBLE
                    binding.tvSearchLabel.text = "Hasil Group"
                    binding.rvGroup.adapter = groupAdapter

                    db.kendaraanDao()
                        .getGroupNopol(cleanQuery)
                        .collectLatest { list ->
                            groupAdapter.updateData(list)

                            updateAddFromSearchButton(
                                query = cleanQuery,
                                resultKosong = list.isEmpty()
                            )
                        }
                }

                // Tepat 4 angka menampilkan kendaraan pada group tersebut.
                isNumeric && cleanQuery.length == 4 -> {
                    binding.tvSearchLabel.visibility = View.VISIBLE
                    binding.tvSearchLabel.text = "Hasil Data"
                    binding.rvGroup.adapter = nopolAdapter

                    db.kendaraanDao()
                        .getKendaraanByGroup(
                            cleanQuery,
                            ""
                        )
                        .collectLatest { list ->
                            nopolAdapter.updateData(
                                NopolFormatter.sortList(list)
                            )

                            updateAddFromSearchButton(
                                query = cleanQuery,
                                resultKosong = list.isEmpty()
                            )
                        }
                }

                else -> {
                    binding.tvSearchLabel.visibility = View.VISIBLE
                    binding.tvSearchLabel.text = "Hasil Data"
                    binding.rvGroup.adapter = nopolAdapter

                    val normalized =
                        normalizeSearchText(cleanQuery)

                    /*
                     * Contoh:
                     * 484D, 0009DD, 8224ABC
                     *
                     * Pencarian dibatasi ke group angkanya agar hasil
                     * no rangka/no mesin tidak ikut masuk.
                     */
                    val nopolPattern = Regex(
                        "^(\\d{1,4})([A-Z]{1,4})$"
                    )

                    val nopolMatch =
                        nopolPattern.matchEntire(normalized)

                    if (nopolMatch != null) {
                        val angkaPart =
                            nopolMatch.groupValues[1]

                        val hurufPart =
                            nopolMatch.groupValues[2]

                        val angkaRapi =
                            angkaPart.padStart(4, '0')

                        val targetNopolKey =
                            angkaRapi + hurufPart

                        db.kendaraanDao()
                            .getKendaraanByGroup(
                                angkaPart,
                                ""
                            )
                            .collectLatest { list ->

                                val filtered = list.filter { kendaraan ->
                                    val displayKey =
                                        normalizeSearchText(
                                            NopolFormatter.display(
                                                kendaraan.nopol
                                            )
                                        )

                                    val rawKey =
                                        normalizeSearchText(
                                            kendaraan.nopol
                                        )

                                    displayKey.contains(targetNopolKey) ||
                                            rawKey.contains(targetNopolKey) ||
                                            rawKey.contains(normalized)
                                }

                                nopolAdapter.updateData(
                                    NopolFormatter.sortList(filtered)
                                )

                                updateAddFromSearchButton(
                                    query = cleanQuery,
                                    resultKosong = filtered.isEmpty()
                                )
                            }
                    } else {
                        db.kendaraanDao()
                            .searchKendaraanSpesifik(normalized)
                            .collectLatest { list ->
                                nopolAdapter.updateData(
                                    NopolFormatter.sortList(list)
                                )

                                updateAddFromSearchButton(
                                    query = cleanQuery,
                                    resultKosong = list.isEmpty()
                                )
                            }
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

    private fun updateAddFromSearchButton(
        query: String,
        resultKosong: Boolean
    ) {
        val normalized =
            normalizeSearchText(query)

        /*
         * Bentuk yang dianggap mirip nopol:
         *
         * 8224
         * B8224
         * 8224ABC
         * B8224ABC
         * B 8224 ABC
         */
        val formatNopolValid = normalized.matches(
            Regex("^[A-Z]{0,2}\\d{1,4}[A-Z]{0,4}$")
        )

        val jumlahAngka = normalized.count {
            it.isDigit()
        }

        val adaHuruf = normalized.any {
            it.isLetter()
        }

        /*
         * Tombol muncul jika:
         * - hasil pencarian kosong;
         * - query berupa 4 angka, atau campuran angka dan huruf;
         * - bentuknya masih masuk pola nomor polisi.
         */
        val bolehDitambahkan =
            formatNopolValid &&
                    (
                            jumlahAngka == 4 ||
                                    (jumlahAngka >= 1 && adaHuruf)
                            )

        binding.btnAddFromSearch.visibility =
            if (
                resultKosong &&
                query.isNotBlank() &&
                bolehDitambahkan
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun hideAddFromSearchButton() {
        binding.btnAddFromSearch.visibility =
            View.GONE
    }

    private fun setupFab() {
        // FAB lama: membuka Tambah Data dengan form kosong.
        binding.fabAdd.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    AddEditActivity::class.java
                )
            )
        }

        // Tombol + baru: membawa isi field pencarian ke Nomor Polisi.
        binding.btnAddFromSearch.setOnClickListener {
            val nopolYangDiketik = binding.etSearch.text
                .toString()
                .trim()
                .uppercase(Locale.getDefault())

            if (nopolYangDiketik.isBlank()) {
                return@setOnClickListener
            }

            val intent = Intent(
                this,
                AddEditActivity::class.java
            ).apply {
                putExtra(
                    "PREFILL_NOPOL",
                    nopolYangDiketik
                )
            }

            startActivity(intent)
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
        binding.menuFavorite.setOnClickListener {
            startActivity(Intent(this, FavoriteActivity::class.java))
            overridePendingTransition(0, 0)
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


    override fun onResume() {
        super.onResume()
        performSearch(binding.etSearch.text.toString())
    }
}
