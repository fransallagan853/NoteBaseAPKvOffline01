package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.notebaseapk.adapter.NopolAdapter
import com.notebaseapk.data.AppDatabase
import com.notebaseapk.databinding.ActivityGroupDetailBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class GroupDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGroupDetailBinding
    private lateinit var db: AppDatabase
    private lateinit var customKeyboardManager: CustomKeyboardManager
    private lateinit var adapter: NopolAdapter
    private var groupNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupCustomKeyboard()

        groupNumber = intent.getStringExtra("GROUP_NUMBER") ?: ""
        db = AppDatabase.getDatabase(this)

        binding.tvGroupTitle.text = "Group $groupNumber"
        binding.btnBack.setOnClickListener { finish() }

        setupRecyclerView()
        setupFilter()
        observeData()
    }
    private fun setupCustomKeyboard() {
        customKeyboardManager = CustomKeyboardManager(
            activity = this,
            keyboardContainer = binding.keyboardContainer
        )

        customKeyboardManager.setup(
            listOf(
                binding.etFilter
            )
        )
    }

    override fun onStart() {
        super.onStart()
        if (::customKeyboardManager.isInitialized) {
            customKeyboardManager.refreshLayout()
        }
    }
    private fun setupRecyclerView() {
        adapter = NopolAdapter(emptyList()) { kendaraan ->
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra("VEHICLE_ID", kendaraan.id)
            startActivity(intent)
        }
        binding.rvNopol.layoutManager = LinearLayoutManager(this)
        binding.rvNopol.adapter = adapter
    }

    private fun setupFilter() {
        binding.etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                loadData(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeData() {
        loadData("")
    }

    private fun loadData(filter: String) {
        lifecycleScope.launch {
            db.kendaraanDao().getKendaraanByGroup(groupNumber, filter).collectLatest { list ->
                adapter.updateData(list)
                binding.tvCountFound.text = "${list.size} data ditemukan"
            }
        }
    }
}
