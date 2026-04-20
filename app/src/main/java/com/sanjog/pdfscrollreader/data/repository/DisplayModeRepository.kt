// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/DisplayModeRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import android.view.KeyEvent
import com.sanjog.pdfscrollreader.data.model.DisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DisplayModeRepository(
    context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _displayMode = MutableStateFlow(readDisplayMode())
    val displayMode: StateFlow<DisplayMode> = _displayMode.asStateFlow()

    private val _performanceMode = MutableStateFlow(isPerformanceModeEnabled())
    val performanceMode: StateFlow<Boolean> = _performanceMode.asStateFlow()

    fun setDisplayMode(mode: DisplayMode) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode.name).apply()
        _displayMode.value = mode
    }

    fun setBrightnessPercent(value: Int) {
        prefs.edit().putInt(KEY_STAGE_BRIGHTNESS_PERCENT, value.coerceIn(0, 100)).apply()
    }

    fun getBrightnessPercent(): Int = prefs.getInt(KEY_STAGE_BRIGHTNESS_PERCENT, 100).coerceIn(0, 100)

    fun setTapZonesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TAP_ZONES_ENABLED, enabled).apply()
    }

    fun isTapZonesEnabled(): Boolean = prefs.getBoolean(KEY_TAP_ZONES_ENABLED, true)

    fun setKeepScreenAwake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled).apply()
    }

    fun isKeepScreenAwake(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_AWAKE, true)

    fun setPerformanceModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PERFORMANCE_MODE_ENABLED, enabled).apply()
        _performanceMode.value = enabled
    }

    fun isPerformanceModeEnabled(): Boolean = prefs.getBoolean(KEY_PERFORMANCE_MODE_ENABLED, false)

    fun setPedalMapping(leftKeyCode: Int, rightKeyCode: Int) {
        prefs.edit()
            .putInt(KEY_LEFT_PEDAL_KEYCODE, leftKeyCode)
            .putInt(KEY_RIGHT_PEDAL_KEYCODE, rightKeyCode)
            .apply()
    }

    fun getLeftPedalKeyCode(): Int = prefs.getInt(KEY_LEFT_PEDAL_KEYCODE, KeyEvent.KEYCODE_B)

    fun getRightPedalKeyCode(): Int = prefs.getInt(KEY_RIGHT_PEDAL_KEYCODE, KeyEvent.KEYCODE_N)

    private fun readDisplayMode(): DisplayMode {
        val value = prefs.getString(KEY_DISPLAY_MODE, DisplayMode.NORMAL.name)
        return value?.let {
            runCatching { DisplayMode.valueOf(it) }.getOrDefault(DisplayMode.NORMAL)
        } ?: DisplayMode.NORMAL
    }

    private companion object {
        const val PREFS_NAME = "pdfscrollreader_prefs"
        const val KEY_DISPLAY_MODE = "key_display_mode"
        const val KEY_STAGE_BRIGHTNESS_PERCENT = "key_stage_brightness_percent"
        const val KEY_TAP_ZONES_ENABLED = "key_tap_zones_enabled"
        const val KEY_KEEP_SCREEN_AWAKE = "key_keep_screen_awake"
        const val KEY_PERFORMANCE_MODE_ENABLED = "key_performance_mode_enabled"
        const val KEY_LEFT_PEDAL_KEYCODE = "key_left_pedal_keycode"
        const val KEY_RIGHT_PEDAL_KEYCODE = "key_right_pedal_keycode"
    }
}
