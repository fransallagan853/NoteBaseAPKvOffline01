package com.notebaseapk

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Extension function untuk mengatur padding atas secara otomatis berdasarkan Status Bar (Notch).
 * Digunakan untuk Header agar responsif di semua jenis HP.
 */
fun View.applyStatusBarPadding(extraPaddingDp: Int = 8) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        val extraPaddingPx = (extraPaddingDp * resources.displayMetrics.density).toInt()
        view.setPadding(
            view.paddingLeft,
            statusBarHeight + extraPaddingPx,
            view.paddingRight,
            view.paddingBottom
        )
        insets
    }
}

/**
 * Extension function untuk mengatur margin bawah secara otomatis berdasarkan Navigation Bar.
 * Digunakan untuk Bottom Nav atau Tombol di bagian paling bawah agar tidak tertutup sistem.
 */
fun View.applyNavigationBarMargin(extraMarginDp: Int = 0) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        val extraMarginPx = (extraMarginDp * resources.displayMetrics.density).toInt()
        
        val params = view.layoutParams as? ConstraintLayout.LayoutParams
        params?.let {
            it.bottomMargin = navBarHeight + extraMarginPx
            view.layoutParams = it
        }
        insets
    }
}
