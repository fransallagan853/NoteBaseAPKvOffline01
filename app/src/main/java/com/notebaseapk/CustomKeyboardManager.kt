package com.notebaseapk

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.util.TypedValue
import android.widget.TextView

class CustomKeyboardManager(
    private val activity: Activity,
    private val keyboardContainer: ViewGroup
) {
    private var activeEditText: EditText? = null

    fun setup(editTexts: List<EditText>) {
        applyKeyboardBottomInset()
        setupKeyboardLayout()

        editTexts.forEach { editText ->
            editText.showSoftInputOnFocus = false

            editText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    activeEditText = editText
                    keyboardContainer.visibility = View.VISIBLE
                }
            }

            editText.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    activeEditText = editText
                    v.requestFocus()
                    keyboardContainer.visibility = View.VISIBLE
                }
                false
            }
        }
    }

    fun refreshLayout() {
        setupKeyboardLayout()
    }

    fun hideKeyboard() {
        keyboardContainer.visibility = View.GONE
        activeEditText?.clearFocus()
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

        val layoutRes = when (layoutKey) {
            "qwerty_numpad" -> R.layout.layout_keyboard_qwerty_numpad
            "numpad_top" -> R.layout.layout_keyboard_numpad_top
            "numpad_bottom" -> R.layout.layout_keyboard_numpad_bottom
            else -> R.layout.layout_keyboard_default
        }

        keyboardContainer.removeAllViews()
        val view = LayoutInflater.from(activity).inflate(layoutRes, keyboardContainer, true)

        applyKeyboardHeightScale(view)
        applyKeyboardTextSize(view)

        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9,

            R.id.btnQ, R.id.btnW, R.id.btnE, R.id.btnR, R.id.btnT,
            R.id.btnY, R.id.btnU, R.id.btnI, R.id.btnO, R.id.btnP,

            R.id.btnA, R.id.btnS, R.id.btnD, R.id.btnF, R.id.btnG,
            R.id.btnH, R.id.btnJ, R.id.btnK, R.id.btnL,

            R.id.btnZ, R.id.btnX, R.id.btnC, R.id.btnV,
            R.id.btnB, R.id.btnN, R.id.btnM
        )

        buttonIds.forEach { id ->
            view.findViewById<View>(id)?.setOnClickListener {
                if (it is Button) {
                    appendText(it.text.toString())
                }
            }
        }

        view.findViewById<View>(R.id.btnClear)?.setOnClickListener {
            clearText()
        }

        view.findViewById<View>(R.id.btnBackspace)?.setOnClickListener {
            deleteChar()
        }
        view.findViewById<View>(R.id.btnSpace)?.setOnClickListener {
            appendText(" ")
        }
        view.findViewById<View>(R.id.btnTutup)?.setOnClickListener {
            hideKeyboard()
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
    private fun applyKeyboardTextSize(root: View) {
        fun scaleText(view: View) {
            if (view is TextView) {
                val text = view.text?.toString() ?: ""

                val sizeSp = when {
                    text.length == 1 -> 22f   // Huruf & angka
                    else -> 16f              // SPASI, TUTUP, tombol panjang
                }

                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            }

            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    scaleText(view.getChildAt(i))
                }
            }
        }

        scaleText(root)
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
}