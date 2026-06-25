package com.notebaseapk

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    private const val PREF_NAME =
        "notebase_prefs"

    private const val KEY_LIGHT_MODE =
        "light_mode"

    fun applySavedTheme(context: Context) {
        applyNightMode(
            isLightMode(context)
        )
    }

    fun setLightMode(
        context: Context,
        enabled: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_LIGHT_MODE,
                enabled
            )
            .apply()

        applyNightMode(enabled)
    }

    fun isLightMode(
        context: Context
    ): Boolean {
        return context.applicationContext
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_LIGHT_MODE,
                false
            )
    }

    fun toggleTheme(context: Context) {
        setLightMode(
            context = context,
            enabled = !isLightMode(context)
        )
    }

    private fun applyNightMode(
        lightModeEnabled: Boolean
    ) {
        val targetMode =
            if (lightModeEnabled) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }

        /*
         * Hindari pemanggilan ulang mode yang sama.
         * Pemanggilan berulang dapat memicu recreate Activity
         * dan terlihat seperti kelap-kelip.
         */
        if (
            AppCompatDelegate.getDefaultNightMode() !=
            targetMode
        ) {
            AppCompatDelegate.setDefaultNightMode(
                targetMode
            )
        }
    }
}
