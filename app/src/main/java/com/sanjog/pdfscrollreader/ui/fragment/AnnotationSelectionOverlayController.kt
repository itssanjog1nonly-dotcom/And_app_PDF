package com.sanjog.pdfscrollreader.ui.fragment

import android.graphics.RectF
import android.view.View
import android.view.ViewGroup

/**
 * Positions the floating annotation actions next to the current selection bounds.
 */
class AnnotationSelectionOverlayController(
    private val root: ViewGroup,
    private val overlay: View,
    private val topObstacle: View,
    private val bottomObstacle: View
) {
    private val marginPx = root.resources.displayMetrics.density * 12f

    fun show(bounds: RectF?) {
        if (bounds == null || root.width == 0 || root.height == 0) {
            hide()
            return
        }

        val overlayWidth = measureWidth()
        val overlayHeight = measureHeight()

        val minX = marginPx
        val maxX = (root.width - overlayWidth - marginPx).coerceAtLeast(minX)
        val minY = if (topObstacle.visibility == View.VISIBLE) topObstacle.bottom + marginPx else marginPx
        val maxY =
            if (bottomObstacle.visibility == View.VISIBLE) {
                (bottomObstacle.top - overlayHeight - marginPx).coerceAtLeast(minY)
            } else {
                (root.height - overlayHeight - marginPx).coerceAtLeast(minY)
            }

        val targetX = (bounds.centerX() - overlayWidth / 2f).coerceIn(minX, maxX)
        val preferredAbove = bounds.top - overlayHeight - marginPx
        val preferredBelow = bounds.bottom + marginPx
        val targetY =
            if (preferredAbove >= minY) {
                preferredAbove
            } else {
                preferredBelow.coerceIn(minY, maxY)
            }

        overlay.x = targetX
        overlay.y = targetY.coerceIn(minY, maxY)
        overlay.visibility = View.VISIBLE
    }

    fun hide() {
        overlay.visibility = View.GONE
    }

    private fun measureWidth(): Float {
        overlay.measure(
            View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.AT_MOST)
        )
        return overlay.measuredWidth.toFloat()
    }

    private fun measureHeight(): Float {
        if (overlay.measuredHeight == 0) {
            overlay.measure(
                View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.AT_MOST)
            )
        }
        return overlay.measuredHeight.toFloat()
    }
}
