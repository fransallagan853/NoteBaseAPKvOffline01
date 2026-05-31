package com.notebaseapk

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.notebaseapk.databinding.ActivityKeyboardSettingsBinding

class KeyboardSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityKeyboardSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sharedPref = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val currentLayout = sharedPref.getString("keyboard_layout", "default") ?: "default"

        // Set initial selection
        when (currentLayout) {
            "default" -> binding.rbDefault.isChecked = true
            "qwerty_numpad" -> binding.rbQwertyNumpad.isChecked = true
            "numpad_top" -> binding.rbNumpadTop.isChecked = true
            "numpad_bottom" -> binding.rbNumpadBottom.isChecked = true
            "custom_manual" -> binding.rbCustomManual.isChecked = true
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnSave.setOnClickListener {
            val selectedId = binding.rgKeyboardLayout.checkedRadioButtonId
            val layoutValue = when (selectedId) {
                binding.rbDefault.id -> "default"
                binding.rbQwertyNumpad.id -> "qwerty_numpad"
                binding.rbNumpadTop.id -> "numpad_top"
                binding.rbNumpadBottom.id -> "numpad_bottom"
                binding.rbCustomManual.id -> "custom_manual"
                else -> "default"
            }

            if (layoutValue == "custom_manual") {
                Toast.makeText(this, "Fitur custom manual akan tersedia pada update berikutnya", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sharedPref.edit().putString("keyboard_layout", layoutValue).apply()
            Toast.makeText(this, "Layout keyboard disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
