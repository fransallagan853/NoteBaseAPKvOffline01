package com.notebaseapk

import android.content.Context
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.notebaseapk.databinding.ActivityKeyboardSettingsBinding

class KeyboardSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKeyboardSettingsBinding
    private lateinit var customKeyboardManager: CustomKeyboardManager
    private lateinit var sharedPref: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeyboardSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPref = getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)

        setupSafeHeader()
        setupSafeBottomButton()
        setupKeyboardLayoutSetting()
        setupKeyboardHeightSlider()
        setupKeyboardPreview()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            Toast.makeText(this, "Pengaturan keyboard disimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupSafeHeader() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val extraPadding = (16 * resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, statusBarHeight + extraPadding, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun setupKeyboardLayoutSetting() {
        val currentLayout = sharedPref.getString("keyboard_layout", "default") ?: "default"

        when (currentLayout) {
            "default" -> binding.rbDefault.isChecked = true
            "qwerty_numpad" -> binding.rbQwertyNumpad.isChecked = true
            "numpad_top" -> binding.rbNumpadTop.isChecked = true
            "numpad_bottom" -> binding.rbNumpadBottom.isChecked = true
            else -> binding.rbDefault.isChecked = true
        }

        binding.rgKeyboardLayout.setOnCheckedChangeListener { _, checkedId ->
            val layoutValue = when (checkedId) {
                binding.rbDefault.id -> "default"
                binding.rbQwertyNumpad.id -> "qwerty_numpad"
                binding.rbNumpadTop.id -> "numpad_top"
                binding.rbNumpadBottom.id -> "numpad_bottom"
                else -> "default"
            }

            sharedPref.edit()
                .putString("keyboard_layout", layoutValue)
                .apply()

            if (::customKeyboardManager.isInitialized) {
                customKeyboardManager.refreshLayout()
                binding.etKeyboardPreview.requestFocus()
            }
        }
    }

    private fun setupKeyboardHeightSlider() {
        val savedScale = sharedPref.getFloat("keyboard_height_scale", 1.0f)

        val progress = (((savedScale - 0.8f) / 0.4f) * 80)
            .toInt()
            .coerceIn(0, 80)

        binding.seekKeyboardHeight.progress = progress
        binding.tvKeyboardHeightValue.text = "${(savedScale * 100).toInt()}%"

        binding.seekKeyboardHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 0.8f + (progress / 80f) * 0.4f

                binding.tvKeyboardHeightValue.text = "${(scale * 100).toInt()}%"

                sharedPref.edit()
                    .putFloat("keyboard_height_scale", scale)
                    .apply()

                if (::customKeyboardManager.isInitialized) {
                    customKeyboardManager.refreshLayout()
                    binding.etKeyboardPreview.requestFocus()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                Toast.makeText(
                    this@KeyboardSettingsActivity,
                    "Tinggi keyboard disimpan",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun setupKeyboardPreview() {
        customKeyboardManager = CustomKeyboardManager(
            activity = this,
            keyboardContainer = binding.keyboardContainer
        )

        customKeyboardManager.setup(
            listOf(binding.etKeyboardPreview)
        )

        binding.etKeyboardPreview.requestFocus()
    }

    private fun setupSafeBottomButton() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnSave) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraMargin = (20 * resources.displayMetrics.density).toInt()

            val params = view.layoutParams as ConstraintLayout.LayoutParams
            params.bottomMargin = navBarHeight + extraMargin
            view.layoutParams = params

            insets
        }
    }
}