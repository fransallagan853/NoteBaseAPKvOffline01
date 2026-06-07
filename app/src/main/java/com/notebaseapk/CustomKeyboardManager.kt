package com.notebaseapk

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.view.ViewGroup

class CustomKeyboardManager(
    private val activity: Activity,
    private val keyboardContainer: ViewGroup
) {
    private var activeEditText: EditText? = null

    fun setup(editTexts: List<EditText>) {
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

        view.findViewById<View>(R.id.btnSearch)?.setOnClickListener {
            hideKeyboard()
        }
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