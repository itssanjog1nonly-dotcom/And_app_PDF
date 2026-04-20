package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.pdf.view.PdfView
import com.sanjog.pdfscrollreader.databinding.FragmentPdfViewerBinding

class AutoScrollManager(
    private val context: Context,
    private val binding: FragmentPdfViewerBinding,
    private val pdfUriString: String?,
    private var fragment: AppEditablePdfViewerFragment? = null,
    private val onIsPlayingChanged: (Boolean) -> Unit
) {
    var durationSeconds: Int = 180
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var _binding: FragmentPdfViewerBinding? = binding
    private val bindingSafe: FragmentPdfViewerBinding? get() = _binding

    var isPlaying = false
        private set(value) {
            field = value
            onIsPlayingChanged(value)
        }

    var isDrawing = false
    var isUserTouching = false
    var lastUserInteractionTime = 0L
    private val USER_SCROLL_COOLDOWN_MS = 700L

    init {
        loadDurationSetting()
        updateDurationDisplay()
    }

    fun setFragment(fragment: AppEditablePdfViewerFragment) {
        this.fragment = fragment
    }

    fun onDestroy() {
        stopAutoScroll()
        _binding = null
    }

    fun toggleAutoScroll(pdfScrollableView: View?) {
        if (isPlaying) stopAutoScroll()
        else startAutoScroll(pdfScrollableView)
    }

    fun startAutoScroll(pdfViewArg: View?) {
        val pdfView = pdfViewArg as? PdfView ?: return
        if (isPlaying) return
        
        isPlaying = true
        val startTimeMillis = SystemClock.uptimeMillis()
        var currentScrollY = pdfView.scrollY.toFloat()

        scrollRunnable = object : Runnable {
            private var lastFrameTime = 0L
            private var pausedDurationMillis = 0L
            private var isCurrentlyPaused = false
            private var pauseStartTime = 0L
            private var lastKnownZoom = pdfView.zoom

            override fun run() {
                if (!isPlaying) return
                val now = SystemClock.uptimeMillis()
                
                if (lastFrameTime == 0L) {
                    lastFrameTime = now
                }

                val shouldPause = isUserTouching || isDrawing || (now - lastUserInteractionTime < USER_SCROLL_COOLDOWN_MS)
                
                if (shouldPause) {
                    if (!isCurrentlyPaused) {
                        isCurrentlyPaused = true
                        pauseStartTime = now
                    }
                    lastFrameTime = now
                    scrollHandler.postDelayed(this, 16)
                    return
                } else if (isCurrentlyPaused) {
                    isCurrentlyPaused = false
                    pausedDurationMillis += (now - pauseStartTime)
                    currentScrollY = pdfView.scrollY.toFloat()
                }

                // Recalculate if zoom changed
                if (pdfView.zoom != lastKnownZoom) {
                    lastKnownZoom = pdfView.zoom
                    currentScrollY = pdfView.scrollY.toFloat()
                }

                val totalDurationMillis = durationSeconds * 1000L
                val elapsedActiveMillis = now - startTimeMillis - pausedDurationMillis
                val remainingMillis = totalDurationMillis - elapsedActiveMillis

                // Physical Stop Condition
                if (!pdfView.canScrollVertically(1)) {
                    stopAutoScroll()
                    return
                }

                // Time Limit Stop Condition
                if (remainingMillis <= 0) {
                    stopAutoScroll()
                    return
                }

                // Use the fragment's wrapper to get the range (which handles zoom)
                val maxScroll = fragment?.getPdfScrollRange() ?: pdfView.height
                val remainingDistance = (maxScroll - pdfView.scrollY).coerceAtLeast(0)
                
                if (remainingDistance <= 0) {
                    stopAutoScroll()
                    return
                }

                val pixelsPerMs = remainingDistance.toFloat() / remainingMillis.toFloat()
                val deltaTime = now - lastFrameTime
                lastFrameTime = now

                currentScrollY += pixelsPerMs * deltaTime
                val nextY = currentScrollY.toInt()
                
                if (nextY != pdfView.scrollY) {
                    val prevY = pdfView.scrollY
                    pdfView.scrollTo(0, nextY)
                    
                    if (pdfView.scrollY == prevY && nextY > prevY) {
                        stopAutoScroll()
                        return
                    }
                }

                scrollHandler.postDelayed(this, 16)
            }
        }
        scrollHandler.post(scrollRunnable!!)
    }

    fun stopAutoScroll() {
        if (isPlaying) {
            android.util.Log.d("AutoScrollManager", "Stopping auto-scroll")
        }
        isPlaying = false
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
    }

    fun handleTimeMinus() {
        durationSeconds = (durationSeconds - 5).coerceAtLeast(10)
        saveDurationSetting()
        updateDurationDisplay()
    }

    fun handleTimePlus() {
        durationSeconds = (durationSeconds + 5).coerceAtMost(3600)
        saveDurationSetting()
        updateDurationDisplay()
    }

    fun showDurationPickerDialog(onDurationSet: () -> Unit) {
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        val minutesPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds / 60
            wrapSelectorWheel = false
        }

        val colonLabel = TextView(context).apply {
            text = " : "
            textSize = 24f
        }

        val secondsPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds % 60
            wrapSelectorWheel = true
        }

        dialogLayout.addView(minutesPicker)
        dialogLayout.addView(colonLabel)
        dialogLayout.addView(secondsPicker)

        AlertDialog.Builder(context)
            .setTitle("Set Song Duration")
            .setView(dialogLayout)
            .setPositiveButton("Set") { _, _ ->
                val mins = minutesPicker.value
                val secs = secondsPicker.value
                durationSeconds = (mins * 60 + secs).coerceAtLeast(10)
                saveDurationSetting()
                updateDurationDisplay()
                onDurationSet()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDurationSetting() {
        val uriKey = pdfUriString ?: return
        val prefs = context.getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("${uriKey}_duration", durationSeconds).apply()
    }

    private fun loadDurationSetting() {
        val uriKey = pdfUriString ?: return
        val prefs = context.getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        durationSeconds = prefs.getInt("${uriKey}_duration", 180)
    }

    private fun updateDurationDisplay() {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        bindingSafe?.tvDurationDisplay?.text = String.format("%02d:%02d", minutes, seconds)
    }
}
