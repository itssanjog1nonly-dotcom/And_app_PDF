// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/bluetooth/BluetoothPedalManager.kt
package com.sanjog.pdfscrollreader.data.bluetooth

import android.content.Context
import android.view.InputDevice
import com.sanjog.pdfscrollreader.data.repository.DisplayModeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BluetoothPedalManager(
    context: Context,
    private val onNextPage: () -> Unit,
    private val onPrevPage: () -> Unit
) {
    private val repository = DisplayModeRepository(context.applicationContext)

    private val _connectionStatus = MutableStateFlow("No pedal detected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    fun handleKeyEvent(keyCode: Int, deviceId: Int = -1): Boolean {
        val leftKey = repository.getLeftPedalKeyCode()
        val rightKey = repository.getRightPedalKeyCode()

        if (deviceId != -1) {
            val name = InputDevice.getDevice(deviceId)?.name ?: "Unknown device"
            _connectionStatus.value = "Pedal connected: $name"
        }

        return when (keyCode) {
            android.view.KeyEvent.KEYCODE_PAGE_DOWN,
            android.view.KeyEvent.KEYCODE_SPACE,
            android.view.KeyEvent.KEYCODE_N,
            rightKey -> {
                onNextPage()
                true
            }

            android.view.KeyEvent.KEYCODE_PAGE_UP,
            android.view.KeyEvent.KEYCODE_B,
            leftKey -> {
                onPrevPage()
                true
            }

            else -> false
        }
    }
}
