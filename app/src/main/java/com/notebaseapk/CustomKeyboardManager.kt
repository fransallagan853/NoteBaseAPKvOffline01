package com.notebaseapk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class CustomKeyboardManager(
    private val activity: Activity,
    private val keyboardContainer: ViewGroup
) {
    private var activeEditText: EditText? = null
    private val registeredEditTexts = mutableListOf<EditText>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lifecycleObserverAdded = false

    private val alphaScaleKey = "keyboard_alpha_height_scale"
    private val numericScaleKey = "keyboard_numeric_height_scale"

    private val defaultAlphaScale = 1.16f
    private val defaultNumericScale = 1.33f

    private val minRowScale = 0.90f
    private val maxRowScale = 1.70f
    private val stepRowScale = 0.05f

    fun setup(editTexts: List<EditText>) {
        registeredEditTexts.clear()
        registeredEditTexts.addAll(editTexts)

        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        setupLifecycleObserver()
        applyKeyboardBottomInset()
        setupKeyboardLayout()
        disableAndroidKeyboardForAllFields()

        editTexts.forEach { editText ->
            editText.showSoftInputOnFocus = false

            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    activeEditText = editText
                    forceHideAndroidKeyboard(editText)
                    keyboardContainer.visibility = View.VISIBLE

                    mainHandler.postDelayed({
                        forceHideAndroidKeyboard(editText)
                    }, 120)

                    mainHandler.postDelayed({
                        forceHideAndroidKeyboard(editText)
                    }, 350)
                }
            }

            editText.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    activeEditText = editText
                    v.requestFocus()
                    forceHideAndroidKeyboard(editText)
                    keyboardContainer.visibility = View.VISIBLE

                    mainHandler.postDelayed({
                        forceHideAndroidKeyboard(editText)
                    }, 120)

                    mainHandler.postDelayed({
                        forceHideAndroidKeyboard(editText)
                    }, 350)
                }
                false
            }
        }
    }

    fun refreshLayout() {
        setupKeyboardLayout()
        disableAndroidKeyboardForAllFields()
        activeEditText?.let {
            forceHideAndroidKeyboard(it)
        }
    }

    fun hideKeyboard() {
        keyboardContainer.visibility = View.GONE
        activeEditText?.let {
            forceHideAndroidKeyboard(it)
            it.clearFocus()
        }
    }

    private fun setupLifecycleObserver() {
        if (lifecycleObserverAdded) return

        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    disableAndroidKeyboardForAllFields()

                    mainHandler.postDelayed({
                        val editText = activeEditText

                        if (editText != null && editText.hasFocus()) {
                            forceHideAndroidKeyboard(editText)
                            keyboardContainer.visibility = View.VISIBLE
                        } else {
                            forceHideAndroidKeyboard()
                        }
                    }, 80)

                    mainHandler.postDelayed({
                        val editText = activeEditText

                        if (editText != null && editText.hasFocus()) {
                            forceHideAndroidKeyboard(editText)
                            keyboardContainer.visibility = View.VISIBLE
                        } else {
                            forceHideAndroidKeyboard()
                        }
                    }, 300)

                    mainHandler.postDelayed({
                        val editText = activeEditText

                        if (editText != null && editText.hasFocus()) {
                            forceHideAndroidKeyboard(editText)
                            keyboardContainer.visibility = View.VISIBLE
                        } else {
                            forceHideAndroidKeyboard()
                        }
                    }, 600)
                }

                override fun onPause(owner: LifecycleOwner) {
                    forceHideAndroidKeyboard()
                }
            })

            lifecycleObserverAdded = true
        }
    }

    private fun disableAndroidKeyboardForAllFields() {
        registeredEditTexts.forEach { editText ->
            editText.showSoftInputOnFocus = false
        }
    }

    private fun forceHideAndroidKeyboard(view: View? = activity.currentFocus) {
        try {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val targetView = view ?: activity.currentFocus ?: keyboardContainer
            imm.hideSoftInputFromWindow(targetView.windowToken, 0)
        } catch (_: Exception) {
        }
    }

    private fun applyKeyboardBottomInset() {
        ViewCompat.setOnApplyWindowInsetsListener(keyboardContainer) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val extraMargin = (8 * view.resources.displayMetrics.density).toInt()

            val params = view.layoutParams
            if (params is ViewGroup.MarginLayoutParams) {
                params.bottomMargin = navBarHeight + extraMargin
                view.layoutParams = params
            }

            insets
        }

        ViewCompat.requestApplyInsets(keyboardContainer)
    }

    private fun setupKeyboardLayout() {
        val sharedPref = activity.getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val layoutKey = sharedPref.getString("keyboard_layout", "default") ?: "default"

        val safeLayoutKey = when (layoutKey) {
            "default", "qwerty_numpad" -> layoutKey
            else -> "default"
        }

        if (safeLayoutKey != layoutKey) {
            sharedPref.edit()
                .putString("keyboard_layout", "default")
                .apply()
        }

        val layoutRes = when (safeLayoutKey) {
            "qwerty_numpad" -> R.layout.layout_keyboard_default2
            else -> R.layout.layout_keyboard_default
        }

        keyboardContainer.removeAllViews()

        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val closeBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        closeBar.addView(createShortcutButton("ABC−") {
            changeRowScale(alphaScaleKey, defaultAlphaScale, -stepRowScale)
        })

        closeBar.addView(createShortcutButton("ABC+") {
            changeRowScale(alphaScaleKey, defaultAlphaScale, stepRowScale)
        })

        closeBar.addView(createShortcutButton("123−") {
            changeRowScale(numericScaleKey, defaultNumericScale, -stepRowScale)
        })

        closeBar.addView(createShortcutButton("123+") {
            changeRowScale(numericScaleKey, defaultNumericScale, stepRowScale)
        })

        closeBar.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    1,
                    1f
                )
            }
        )

        val btnCloseKeyboard = TextView(activity).apply {
            text = "TUTUP  ✕"
            setTextColor(ContextCompat.getColor(activity, R.color.cyan_accent))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundResource(R.drawable.bg_keyboard_button)
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(4)
            }

            setOnClickListener {
                hideKeyboard()
            }
        }

        closeBar.addView(btnCloseKeyboard)
        wrapper.addView(closeBar)

        val keyboardView = LayoutInflater.from(activity).inflate(layoutRes, wrapper, false)
        wrapper.addView(keyboardView)

        keyboardContainer.addView(wrapper)

        applyKeyboardHeightScale(keyboardView)
        applyNumericRowScale(keyboardView)
        applyAlphaRowScale(keyboardView)
        applyKeyboardTextSize(keyboardView)

        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,

            R.id.btnQ, R.id.btnW, R.id.btnE, R.id.btnR, R.id.btnT,
            R.id.btnY, R.id.btnU, R.id.btnI, R.id.btnO, R.id.btnP,

            R.id.btnA, R.id.btnS, R.id.btnD, R.id.btnF, R.id.btnG,
            R.id.btnH, R.id.btnJ, R.id.btnK, R.id.btnL,

            R.id.btnZ, R.id.btnX, R.id.btnC, R.id.btnV,
            R.id.btnB, R.id.btnN, R.id.btnM,

            R.id.btnSlash, R.id.btnAsterisk,
            R.id.btnComma, R.id.btnDot
        )

        buttonIds.forEach { id ->
            keyboardView.findViewById<View>(id)?.setOnClickListener {
                if (it is Button) {
                    appendText(it.text.toString())
                }
            }
        }

        keyboardView.findViewById<View>(R.id.btnClear)?.setOnClickListener {
            clearText()
        }

        keyboardView.findViewById<View>(R.id.btnTutup)?.setOnClickListener {
            clearText()
        }

        keyboardView.findViewById<View>(R.id.btnBackspace)?.setOnClickListener {
            deleteChar()
        }

        keyboardView.findViewById<View>(R.id.btnBackspaceAlt)?.setOnClickListener {
            deleteChar()
        }

        keyboardView.findViewById<View>(R.id.btnSpace)?.setOnClickListener {
            appendText(" ")
        }

        keyboardView.findViewById<View>(R.id.btnSwitchLayout)?.setOnClickListener {
            val currentLayout = sharedPref.getString("keyboard_layout", "default") ?: "default"

            val nextLayout = if (currentLayout == "default") {
                "qwerty_numpad"
            } else {
                "default"
            }

            sharedPref.edit()
                .putString("keyboard_layout", nextLayout)
                .apply()

            setupKeyboardLayout()
            keyboardContainer.visibility = View.VISIBLE
            activeEditText?.requestFocus()
            activeEditText?.let {
                forceHideAndroidKeyboard(it)
            }
        }

        keyboardView.findViewById<View>(R.id.btnKeyboardSetting)?.setOnClickListener {
            forceHideAndroidKeyboard()
            val intent = Intent(activity, KeyboardSettingsActivity::class.java)
            activity.startActivity(intent)
        }

        keyboardView.findViewById<View>(R.id.btnCursorLeft)?.setOnClickListener {
            moveCursorLeft()
        }

        keyboardView.findViewById<View>(R.id.btnCursorRight)?.setOnClickListener {
            moveCursorRight()
        }
    }

    private fun createShortcutButton(textValue: String, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            text = textValue
            setTextColor(ContextCompat.getColor(activity, R.color.white_text))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(8), dp(7), dp(8), dp(7))
            setBackgroundResource(R.drawable.bg_keyboard_button)
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(4)
            }

            setOnClickListener {
                onClick()
            }
        }
    }

    private fun changeRowScale(key: String, defaultValue: Float, delta: Float) {
        val prefs = activity.getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val current = prefs.getFloat(key, defaultValue)

        val next = (current + delta)
            .coerceIn(minRowScale, maxRowScale)

        prefs.edit()
            .putFloat(key, next)
            .apply()

        setupKeyboardLayout()
        keyboardContainer.visibility = View.VISIBLE

        activeEditText?.let {
            it.requestFocus()
            forceHideAndroidKeyboard(it)
        }
    }

    private fun applyKeyboardHeightScale(root: View) {
        val prefs = activity.getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val scale = prefs.getFloat("keyboard_height_scale", 1.0f)

        fun scaleView(view: View) {
            val params = view.layoutParams

            if (params != null && params.height > 0) {
                params.height = (params.height * scale).toInt()
                view.layoutParams = params
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    scaleView(view.getChildAt(i))
                }
            }
        }

        scaleView(root)
    }

    private fun applyNumericRowScale(root: View) {
        val prefs = activity.getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val scale = prefs.getFloat(numericScaleKey, defaultNumericScale)

        val numericRowIds = setOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        fun hasDirectNumericButton(group: ViewGroup): Boolean {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.id in numericRowIds) {
                    return true
                }
            }
            return false
        }

        fun apply(view: View) {
            if (view is ViewGroup) {
                val params = view.layoutParams

                if (params != null && params.height > 0 && hasDirectNumericButton(view)) {
                    params.height = (params.height * scale).toInt()
                    view.layoutParams = params
                }

                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i))
                }
            }
        }

        apply(root)
    }

    private fun applyAlphaRowScale(root: View) {
        val prefs = activity.getSharedPreferences("notebase_prefs", Context.MODE_PRIVATE)
        val scale = prefs.getFloat(alphaScaleKey, defaultAlphaScale)

        val alphaRowIds = setOf(
            R.id.btnQ, R.id.btnW, R.id.btnE, R.id.btnR, R.id.btnT,
            R.id.btnY, R.id.btnU, R.id.btnI, R.id.btnO, R.id.btnP,

            R.id.btnA, R.id.btnS, R.id.btnD, R.id.btnF, R.id.btnG,
            R.id.btnH, R.id.btnJ, R.id.btnK, R.id.btnL,

            R.id.btnZ, R.id.btnX, R.id.btnC, R.id.btnV,
            R.id.btnB, R.id.btnN, R.id.btnM
        )

        fun hasDirectAlphaButton(group: ViewGroup): Boolean {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.id in alphaRowIds) {
                    return true
                }
            }
            return false
        }

        fun apply(view: View) {
            if (view is ViewGroup) {
                val params = view.layoutParams

                if (params != null && params.height > 0 && hasDirectAlphaButton(view)) {
                    params.height = (params.height * scale).toInt()
                    view.layoutParams = params
                }

                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i))
                }
            }
        }

        apply(root)
    }

    private fun applyKeyboardTextSize(root: View) {
        fun apply(view: View) {
            if (view is Button) {
                val text = view.text?.toString() ?: ""

                val sizeSp = when {
                    text.matches(Regex("[0-9]")) -> 21f
                    text.matches(Regex("[A-Z]")) -> 20f
                    text == "/" || text == "*" || text == "," || text == "." -> 18f
                    text == "←" || text == "→" -> 20f
                    text == "⎵" -> 22f
                    text == "🔁" || text == "⚙️" -> 17f
                    else -> 18f
                }

                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                view.includeFontPadding = false
                view.isAllCaps = false
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    apply(view.getChildAt(i))
                }
            }
        }

        apply(root)
    }

    private fun appendText(value: String) {
        val editText = activeEditText ?: return

        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(0)

        editText.text.replace(
            minOf(start, end),
            maxOf(start, end),
            value
        )
    }

    private fun deleteChar() {
        val editText = activeEditText ?: return

        val start = editText.selectionStart
        val end = editText.selectionEnd

        if (start < 0 || end < 0) return

        if (start != end) {
            editText.text.delete(minOf(start, end), maxOf(start, end))
        } else if (start > 0) {
            editText.text.delete(start - 1, start)
        }
    }

    private fun clearText() {
        activeEditText?.setText("")
    }

    private fun moveCursorLeft() {
        val editText = activeEditText ?: return
        val current = editText.selectionStart

        if (current > 0) {
            editText.setSelection(current - 1)
        }
    }

    private fun moveCursorRight() {
        val editText = activeEditText ?: return
        val current = editText.selectionStart
        val max = editText.text.length

        if (current < max) {
            editText.setSelection(current + 1)
        }
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}