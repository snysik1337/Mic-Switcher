package com.example.micswitcher

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Persists a manual light/dark/system theme choice and applies it via
 * AppCompatDelegate. Even without this, the app already follows the
 * system's day/night setting automatically (it's built on
 * Theme.MaterialComponents.DayNight) — this just adds an explicit override
 * for people who want the app in one mode regardless of system setting.
 */
object ThemePrefs {
    private const val PREFS = "mic_switcher_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    fun applySavedMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(currentMode(context))
    }

    fun currentMode(context: Context): Int =
        prefs(context).getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** Cycles System -> Light -> Dark -> System, persists it, and applies it. */
    fun cycleMode(context: Context): Int {
        val next = when (currentMode(context)) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        prefs(context).edit().putInt(KEY_NIGHT_MODE, next).apply()
        AppCompatDelegate.setDefaultNightMode(next)
        return next
    }

    fun label(mode: Int): String = when (mode) {
        AppCompatDelegate.MODE_NIGHT_NO -> "Theme: Light"
        AppCompatDelegate.MODE_NIGHT_YES -> "Theme: Dark"
        else -> "Theme: System"
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
