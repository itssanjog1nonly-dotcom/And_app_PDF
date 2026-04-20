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
    private var pdfViewerFragment: PdfViewerFragment? = null,
    private val onIsPlayingChanged: (Boolean) -> Unit
) {
    var durationSeconds: Int = 180
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var _binding: FragmentPdfViewerBinding? = binding
    private val bindingSafe: FragmentPdfViewerBinding? get() = _binding

    private var targetPdfView: PdfView? = null

    var isPlaying = false
        private set(value) {
            field = value
            onIsPlayingChanged(value)
        }

    var isDrawing = false
    var isUserTouching = false
    var isSimulatingTouch = false
    var lastUserInteractionTime = 0L
    private val USER_SCROLL_COOLDOWN_MS = 700L

    init {
        loadDurationSetting()
        updateDurationDisplay()
    }

    fun setPdfViewerFragment(fragment: PdfViewerFragment) {
        this.pdfViewerFragment = fragment
    }

    fun setPdfView(pdfView: PdfView?) {
        this.targetPdfView = pdfView
    }

    fun onDestroy() {
        stopAutoScroll()
        _binding = null
        targetPdfView = null
    }

    private fun simulateDragNudge(view: View) {
        isSimulatingTouch = true
        val time = SystemClock.uptimeMillis()
        val x = view.width / 2f
        val y = view.height / 2f
        val dragDistance = 80f // Drag finger UP to scroll DOWN
        
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 10, android.view.MotionEvent.ACTION_MOVE, x, y - dragDistance, 0))
        view.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 20, android.view.MotionEvent.ACTION_UP, x, y - dragDistance, 0))
        
        // Reset flag safely after gesture completes
        scrollHandler.postDelayed({ isSimulatingTouch = false }, 150)
    }

    fun toggleAutoScroll() {
        if (isPlaying) stopAutoScroll()
        else startAutoScroll()
    }

    fun startAutoScroll() {
        val pdfView = targetPdfView ?: run {
            android.util.Log.e("AUTOSCROLL_DIAG", "Cannot start: targetPdfView is null")
            isPlaying = false
            return
        }

        if (pdfView.height == 0) {
            android.util.Log.d("AUTOSCROLL_DIAG", "PdfView not laid out yet, deferring start")
            pdfView.post { startAutoScroll() }
            return
        }

        if (isPlaying) return
        
        isPlaying = true

        scrollRunnable = object : Runnable {
            private var lastFrameTime = 0L
            private var stallFrames = 0
            private var accumulatedScroll = 0f

            override fun run() {
                try {
                    if (!isPlaying) return
                    val now = SystemClock.uptimeMillis()
                    
                    if (lastFrameTime == 0L) {
                        lastFrameTime = now
                    }

                    val shouldPause = isUserTouching || isDrawing || (now - lastUserInteractionTime < USER_SCROLL_COOLDOWN_MS)
                    
                    if (shouldPause) {
                        lastFrameTime = now
                        scrollHandler.postDelayed(this, 16)
                        return
                    }

                    val viewToScroll = targetPdfView ?: return

                    // 1. Semantic Speed Calculation
                    val totalDistance = pdfViewerFragment?.getEstimatedTotalDistance() ?: computePdfViewVerticalScrollRange(viewToScroll).toFloat()
                    if (totalDistance <= 0f) {
                        android.util.Log.e("AUTOSCROLL_DIAG", "Total distance is 0! Waiting for layout...")
                        lastFrameTime = now
                        scrollHandler.postDelayed(this, 16)
                        return
                    }

                    val totalDurationMillis = durationSeconds * 1000L
                    val pixelsPerMs = totalDistance / totalDurationMillis
                    val deltaTime = now - lastFrameTime
                    lastFrameTime = now

                    accumulatedScroll += pixelsPerMs * deltaTime
                    val scrollStep = accumulatedScroll.toInt()
                    
                    // Log diagnostics periodically (approx every 10th frame)
                    if (SystemClock.uptimeMillis() % 160 < 20) {
                        android.util.Log.d("AUTOSCROLL_DIAG", "Dist: $totalDistance, Step: $scrollStep, Acc: $accumulatedScroll, Stall: $stallFrames")
                    }

                    // 2. Apply Scroll and Track Stalls
                    if (scrollStep > 0) {
                        val startOffset = computePdfViewVerticalScrollOffset(viewToScroll)
                        viewToScroll.scrollBy(0, scrollStep)
                        val endOffset = computePdfViewVerticalScrollOffset(viewToScroll)
                        
                        if (startOffset == endOffset) {
                            stallFrames++
                        } else {
                            stallFrames = 0
                        }
                        accumulatedScroll -= scrollStep
                    }

                    // 3. True Bottom Detection & Lazy-Load Breakthrough
                    if (stallFrames >= 15) {
                        val isLastVisible = pdfViewerFragment?.isLastPageVisible() == true
                        
                        // Safety Net: If we stall for 60 frames (~1 second), stop entirely so we don't nudge forever.
                        if (isLastVisible || stallFrames >= 60) {
                            android.util.Log.d("AUTOSCROLL_DIAG", "State: STOPPED. LastVisible: $isLastVisible, Stalls: $stallFrames")
                            stopAutoScroll()
                            return
                        } else {
                            // We are stalled at a boundary but NOT at the last page (or page count is unknown).
                            // Force a load using a simulated touch event!
                            if (stallFrames % 15 == 0) {
                                simulateDragNudge(viewToScroll)
                            }
                        }
                    }

                    scrollHandler.postDelayed(this, 16)
                } catch (e: Exception) {
                    android.util.Log.e("AUTOSCROLL_DIAG", "Crash in scroll loop", e)
                    stopAutoScroll()
                }
            }
        }
        scrollHandler.post(scrollRunnable!!)
    }

    private fun computePdfViewVerticalScrollRange(pdfView: PdfView): Int {
        return try {
            val method = View::class.java.getDeclaredMethod("computeVerticalScrollRange")
            method.isAccessible = true
            method.invoke(pdfView) as Int
        } catch (e: Exception) {
            pdfView.height // Fallback
        }
    }

    private fun computePdfViewVerticalScrollOffset(pdfView: PdfView): Int {
        return try {
            val method = View::class.java.getDeclaredMethod("computeVerticalScrollOffset")
            method.isAccessible = true
            method.invoke(pdfView) as Int
        } catch (e: Exception) {
            pdfView.scrollY
        }
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
