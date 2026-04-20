// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/PreferencesRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context

class PreferencesRepository(
    context: Context
) {
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    var scrollDurationMs: Long
        get() = sharedPreferences.getLong(KEY_SCROLL_DURATION_MS, 30_000L)
        set(value) {
            sharedPreferences.edit().putLong(KEY_SCROLL_DURATION_MS, value).apply()
        }

    var isLoopEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_LOOP_ENABLED, false)
        set(value) {
            sharedPreferences.edit().putBoolean(KEY_LOOP_ENABLED, value).apply()
        }

    var lastOpenedUri: String?
        get() = sharedPreferences.getString(KEY_LAST_OPENED_URI, null)
        set(value) {
            sharedPreferences.edit().putString(KEY_LAST_OPENED_URI, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_SCROLL_DURATION_MS = "scroll_duration_ms"
        const val KEY_LOOP_ENABLED = "loop_enabled"
        const val KEY_LAST_OPENED_URI = "last_opened_uri"
    }
}
