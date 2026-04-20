package com.sanjog.pdfscrollreader.ui.view

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region

class InkSelectionHandler(private val view: InkCanvasView) {
    private var capturedStrokes: List<Stroke> = emptyList()
    private var capturedShapes: List<ShapeAnnotation> = emptyList()

    fun selectAtPoint(x: Float, y: Float, delegate: PageDelegate?) {
        val tolerance = 24f
        var foundId: String? = null
        var isStroke = false

        for (stroke in view.strokes.reversed()) {
            stroke.updatePath(delegate, force = true)
            val bounds = RectF(stroke.cachedScreenBounds).apply { inset(-tolerance, -tolerance) }
            if (stroke.intersects(x, y, tolerance, delegate) || bounds.contains(x, y)) {
                foundId = stroke.id
                isStroke = true
                break
            }
        }

        if (foundId == null) {
            for (shape in view.shapes.reversed()) {
                val bounds = getShapeScreenBounds(shape, delegate) ?: continue
                val shapeHit = delegate != null && shape.intersects(x, y, tolerance, delegate)
                if (shapeHit || RectF(bounds).apply { inset(-tolerance, -tolerance) }.contains(x, y)) {
                    foundId = shape.id
                    isStroke = false
                    break
                }
            }
        }

        if (foundId != null) {
            if (isStroke) {
                if (view.selectedStrokeIds.contains(foundId)) {
                    view.selectedStrokeIds.remove(foundId)
                } else {
                    view.selectedStrokeIds.add(foundId)
                }
            } else {
                if (view.selectedShapeIds.contains(foundId)) {
                    view.selectedShapeIds.remove(foundId)
                } else {
                    view.selectedShapeIds.add(foundId)
                }
            }
            view.onSelectionChangedListener?.invoke(view.hasSelection())
        } else {
            // Tap on empty space: clear selection
            view.selectedStrokeIds.clear()
            view.selectedShapeIds.clear()
            view.onSelectionChangedListener?.invoke(false)
        }
        view.invalidate()
    }

    fun finalizeMarqueeSelection() {
        val marquee = view.marqueeRect ?: return
        val delegate = view.pageDelegate

        view.selectedStrokeIds.clear()
        view.selectedShapeIds.clear()

        for (stroke in view.strokes) {
            stroke.updatePath(delegate, force = true)
            if (marquee.contains(stroke.cachedScreenBounds)) {
                view.selectedStrokeIds.add(stroke.id)
            }
        }

        for (shape in view.shapes) {
            val bounds = getShapeScreenBounds(shape, delegate) ?: continue
            if (marquee.contains(bounds)) {
                view.selectedShapeIds.add(shape.id)
            }
        }

        view.onSelectionChangedListener?.invoke(view.hasSelection())
        view.invalidate()
    }

    fun finalizeLassoSelection(lassoPoints: List<PointF>, pageIndex: Int) {
        view.lassoPath?.close()
        view.selectedStrokeIds.clear()
        view.selectedShapeIds.clear()

        val lasso = view.lassoPath ?: run {
            view.onSelectionChangedListener?.invoke(false)
            return
        }

        val lassoBounds = RectF()
        lasso.computeBounds(lassoBounds, true)
        if (lassoBounds.isEmpty) {
            view.onSelectionChangedListener?.invoke(false)
            return
        }

        val lassoRegion =
            Region().apply {
                setPath(
                    lasso,
                    Region(
                        lassoBounds.left.toInt(),
                        lassoBounds.top.toInt(),
                        lassoBounds.right.toInt(),
                        lassoBounds.bottom.toInt()
                    )
                )
            }

        val delegate = view.pageDelegate

        for (stroke in view.strokes) {
            stroke.updatePath(delegate, force = true)
            val bounds = stroke.cachedScreenBounds
            if (bounds.isEmpty) continue
            if (regionContainsAllPoints(lassoRegion, bounds)) {
                view.selectedStrokeIds.add(stroke.id)
            }
        }

        for (shape in view.shapes) {
            val bounds = getShapeScreenBounds(shape, delegate) ?: continue
            if (regionContainsAllPoints(lassoRegion, bounds)) {
                view.selectedShapeIds.add(shape.id)
            }
        }

        view.onSelectionChangedListener?.invoke(view.hasSelection())
        view.invalidate()
    }

    fun hasSelection(): Boolean = view.selectedStrokeIds.isNotEmpty() || view.selectedShapeIds.isNotEmpty()

    fun deleteSelected() {
        val toRemoveStrokes = view.strokes.filter { it.id in view.selectedStrokeIds }
        val toRemoveShapes = view.shapes.filter { it.id in view.selectedShapeIds }
        if (toRemoveStrokes.isNotEmpty() || toRemoveShapes.isNotEmpty()) {
            view.pushUndo()
            view.strokes.removeAll(toRemoveStrokes)
            view.shapes.removeAll(toRemoveShapes)
            view.selectedStrokeIds.clear()
            view.selectedShapeIds.clear()
            view.onChangeListener?.onChange()
            view.onSelectionChangedListener?.invoke(false)
            view.invalidate()
        }
    }

    fun getSelectionBounds(): RectF? {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE
        var hasBounds = false

        val delegate = view.pageDelegate

        for (stroke in view.strokes.filter { it.id in view.selectedStrokeIds }) {
            stroke.updatePath(delegate, force = true)
            val bounds = stroke.cachedScreenBounds
            if (!bounds.isEmpty) {
                left = minOf(left, bounds.left)
                top = minOf(top, bounds.top)
                right = maxOf(right, bounds.right)
                bottom = maxOf(bottom, bounds.bottom)
                hasBounds = true
            }
        }

        for (shape in view.shapes.filter { it.id in view.selectedShapeIds }) {
            val bounds = getShapeScreenBounds(shape, delegate) ?: continue
            left = minOf(left, bounds.left)
            top = minOf(top, bounds.top)
            right = maxOf(right, bounds.right)
            bottom = maxOf(bottom, bounds.bottom)
            hasBounds = true
        }

        return if (hasBounds) RectF(left, top, right, bottom) else null
    }

    fun captureInitialState() {
        capturedStrokes = view.strokes.map { it.deepCopy() }
        capturedShapes = view.shapes.map { it.deepCopy() }
    }

    fun commitStateChange() {
        if (capturedStrokes.isEmpty() && capturedShapes.isEmpty()) return
        val snapshot = Pair(capturedStrokes.map { it.deepCopy() }, capturedShapes.map { it.deepCopy() })
        view.pushUndoSnapshot(snapshot)
        capturedStrokes = emptyList()
        capturedShapes = emptyList()
        view.onChangeListener?.onChange()
    }

    fun liveUpdateProperties(color: Int? = null, width: Float? = null, alpha: Int? = null) {
        for (strokeId in view.selectedStrokeIds) {
            view.strokes.find { it.id == strokeId }?.let { stroke ->
                // Using a modified approach since Stroke is a data class and we want to avoid recreating the whole list
                // but since it's a MutableList of Strokes, and Stroke has val properties, we must replace the instance
                val index = view.strokes.indexOf(stroke)
                if (index != -1) {
                    view.strokes[index] = stroke.copy(
                        color = color ?: stroke.color,
                        strokeWidth = width ?: stroke.strokeWidth
                    ).apply { invalidateCache() }
                }
            }
        }
        for (shapeId in view.selectedShapeIds) {
            view.shapes.find { it.id == shapeId }?.let { shape ->
                val index = view.shapes.indexOf(shape)
                if (index != -1) {
                    view.shapes[index] = shape.copy(
                        strokeColor = color ?: shape.strokeColor,
                        strokeWidth = width ?: shape.strokeWidth,
                        fillAlpha = alpha ?: shape.fillAlpha
                    ).apply { invalidateCache() }
                }
            }
        }
        view.invalidate()
    }

    fun updateProperties(color: Int? = null, width: Float? = null) {
        liveUpdateProperties(color = color, width = width)
        commitStateChange()
    }

    fun duplicateSelected(offsetX: Float = 30f, offsetY: Float = 30f) {
        if (!hasSelection()) return

        val delegate = view.pageDelegate
        val newStrokes = mutableListOf<Stroke>()
        val newShapes = mutableListOf<ShapeAnnotation>()

        val finalOffsetX = offsetX * (1f / (view.currentZoom.coerceAtLeast(0.1f)))
        val finalOffsetY = offsetY * (1f / (view.currentZoom.coerceAtLeast(0.1f)))

        for (strokeId in view.selectedStrokeIds) {
            view.strokes.find { it.id == strokeId }?.let { stroke ->
                val offset = normalizeOffsetForPage(stroke.pageIndex, finalOffsetX, finalOffsetY, delegate)
                val movedPoints =
                    stroke.points.map { point ->
                        PointF(point.x + offset.x, point.y + offset.y)
                    }.toMutableList()
                newStrokes.add(
                    stroke.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        points = movedPoints
                    )
                )
            }
        }

        for (shapeId in view.selectedShapeIds) {
            view.shapes.find { it.id == shapeId }?.let { shape ->
                val offset = normalizeOffsetForPage(shape.pageIndex, finalOffsetX, finalOffsetY, delegate)
                
                val movedPoints = shape.points?.map { pt ->
                    PointF(pt.x + offset.x, pt.y + offset.y)
                }

                newShapes.add(
                    shape.copy(
                        id = java.util.UUID.randomUUID().toString(),
                        normLeft = shape.normLeft + offset.x,
                        normTop = shape.normTop + offset.y,
                        normRight = shape.normRight + offset.x,
                        normBottom = shape.normBottom + offset.y,
                        points = movedPoints
                    )
                )
            }
        }

        if (newStrokes.isNotEmpty() || newShapes.isNotEmpty()) {
            view.pushUndo()
            view.strokes.addAll(newStrokes)
            view.shapes.addAll(newShapes)
            view.selectedStrokeIds.clear()
            view.selectedShapeIds.clear()
            newStrokes.forEach { view.selectedStrokeIds.add(it.id) }
            newShapes.forEach { view.selectedShapeIds.add(it.id) }
            view.onChangeListener?.onChange()
            view.onSelectionChangedListener?.invoke(true)
            view.invalidate()
        }
    }

    private fun getShapeScreenBounds(shape: ShapeAnnotation, delegate: PageDelegate?): RectF? = 
        com.sanjog.pdfscrollreader.ui.view.getShapeScreenBounds(shape, delegate)

    private fun regionContainsAllPoints(region: Region, bounds: RectF): Boolean =
        com.sanjog.pdfscrollreader.ui.view.regionContainsAllPoints(region, bounds)

    private fun normalizeOffsetForPage(pageIndex: Int, offsetX: Float, offsetY: Float, delegate: PageDelegate?): PointF =
        com.sanjog.pdfscrollreader.ui.view.normalizeOffsetForPage(pageIndex, offsetX, offsetY, delegate)
}
