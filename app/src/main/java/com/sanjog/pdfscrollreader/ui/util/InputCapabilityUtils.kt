package com.sanjog.pdfscrollreader.ui.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice
import android.view.MotionEvent

object InputCapabilityUtils {

    /**
     * Checks if the device has a stylus connected or supported.
     */
    fun isStylusSupported(context: Context): Boolean {
        val packageManager = context.packageManager
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_STYLUS)) {
            return true
        }

        val deviceIds = InputDevice.getDeviceIds()
        for (id in deviceIds) {
            val device = InputDevice.getDevice(id) ?: continue
            val sources = device.sources
            
            val isStylus = (sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) ||
                           (sources and InputDevice.SOURCE_BLUETOOTH_STYLUS == InputDevice.SOURCE_BLUETOOTH_STYLUS)
            
            if (isStylus) return true
        }
        return false
    }

    fun shouldEnableAnnotationUi(context: Context): Boolean {
        val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
        return isTablet && isStylusSupported(context)
    }

    /**
     * Checks if a MotionEvent was produced by a stylus.
     */
    fun isStylusEvent(event: MotionEvent): Boolean {
        for (i in 0 until event.pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                return true
            }
        }
        return false
    }
}
