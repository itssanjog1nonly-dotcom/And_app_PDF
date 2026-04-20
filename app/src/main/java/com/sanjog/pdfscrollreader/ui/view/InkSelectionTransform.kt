package com.sanjog.pdfscrollreader.ui.view

import android.graphics.PointF
import android.graphics.RectF

/**
 * Applies screen-space transforms to the current selection while keeping annotation data normalized.
 */
class InkSelectionTransform(private val view: InkCanvasView) {

    fun hitsSelectionBounds(x: Float, y: Float, padding: Float): Boolean {
        val bounds = view.getSelectionBounds() ?: return false
        val rect = RectF(bounds).apply { inset(-padding, -padding) }
        val contains = rect.contains(x, y)
        android.util.Log.d("INK_DEBUG", "hitsSelectionBounds: x=$x y=$y bounds=$bounds padding=$padding hit=$contains")
        return contains
    }

    fun translateSelection(previousX: Float, previousY: Float, currentX: Float, currentY: Float) {
        val dx = currentX - previousX
        val dy = currentY - previousY
        if (dx == 0f && dy == 0f) return

        val delegate = view.pageDelegate
        android.util.Log.d("INK_DEBUG", "translateSelection: dx=$dx dy=$dy")

        for (strokeId in view.selectedStrokeIds) {
            view.strokes.find { it.id == strokeId }?.let { stroke ->
                val offset = normalizeOffsetForPage(stroke.pageIndex, dx, dy, delegate)
                stroke.moveBy(offset.x, offset.y)
            }
        }

        for (shapeId in view.selectedShapeIds) {
            view.shapes.find { it.id == shapeId }?.let { shape ->
                val offset = normalizeOffsetForPage(shape.pageIndex, dx, dy, delegate)
                shape.moveBy(offset.x, offset.y)
            }
        }

        view.onSelectionChangedListener?.invoke(view.hasSelection())
        view.invalidate()
    }

    fun resizeSelection(anchor: PointF, previousX: Float, previousY: Float, currentX: Float, currentY: Float) {
        val bounds = view.getSelectionBounds() ?: return
        if (bounds.width() == 0f || bounds.height() == 0f) return

        val scaleX = (currentX - anchor.x) / (previousX - anchor.x)
        val scaleY = (currentY - anchor.y) / (previousY - anchor.y)

        if (scaleX.isNaN() || scaleY.isNaN() || scaleX.isInfinite() || scaleY.isInfinite()) return

        val delegate = view.pageDelegate

        for (strokeId in view.selectedStrokeIds) {
            view.strokes.find { it.id == strokeId }?.let { stroke ->
                val anchorNorm = delegate?.normalizePoint(stroke.pageIndex, anchor.x, anchor.y) ?: anchor
                stroke.scaleBy(anchorNorm, scaleX, scaleY)
            }
        }

        for (shapeId in view.selectedShapeIds) {
            view.shapes.find { it.id == shapeId }?.let { shape ->
                val anchorNorm = delegate?.normalizePoint(shape.pageIndex, anchor.x, anchor.y) ?: anchor
                shape.scaleBy(anchorNorm, scaleX, scaleY)
            }
        }

        view.onSelectionChangedListener?.invoke(view.hasSelection())
        view.invalidate()
    }

    private fun normalizeOffsetForPage(
        pageIndex: Int,
        dx: Float,
        dy: Float,
        delegate: PageDelegate?
    ): PointF {
        val pageBounds = delegate?.getPageBounds(pageIndex)
        if (pageBounds != null && pageBounds.width() > 1f && pageBounds.height() > 1f) {
            return PointF(dx / pageBounds.width(), dy / pageBounds.height())
        }
        return PointF(0f, 0f)
    }
}
