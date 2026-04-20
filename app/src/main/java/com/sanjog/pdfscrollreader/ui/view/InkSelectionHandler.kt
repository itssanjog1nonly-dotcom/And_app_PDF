package com.sanjog.pdfscrollreader.ui.view

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region

class InkSelectionHandler(private val view: InkCanvasView) {
    private var capturedStrokes: List<InkCanvasView.Stroke> = emptyList()
    private var capturedShapes: List<InkCanvasView.ShapeAnnotation> = emptyList()
    
    fun selectAtPoint(x: Float, y: Float, delegate: InkCanvasView.PageDelegate?) {
        for (stroke in view.strokes.reversed()) {
            val bounds = RectF(stroke.cachedScreenBounds).apply { inset(-24f, -24f) }
            if (stroke.intersects(x, y, 24f, delegate) || bounds.contains(x, y)) {
                view.selectedStrokeIds.clear()
                view.selectedShapeIds.clear()
                view.selectedStrokeIds.add(stroke.id)
                view.onSelectionChangedListener?.invoke(true)
                view.invalidate()
                return
            }
        }
        for (shape in view.shapes.reversed()) {
            if (delegate != null && shape.intersects(x, y, 24f, delegate)) {
                view.selectedStrokeIds.clear()
                view.selectedShapeIds.clear()
                view.selectedShapeIds.add(shape.id)
                view.onSelectionChangedListener?.invoke(true)
                view.invalidate()
                return
            }
        }
        // Nothing hit: clear selection
        view.selectedStrokeIds.clear()
        view.selectedShapeIds.clear()
        view.onSelectionChangedListener?.invoke(false)
        view.invalidate()
    }
    
    fun finalizeMarqueeSelection() {
        val marquee = view.marqueeRect ?: return
        view.selectedStrokeIds.clear()
        view.selectedShapeIds.clear()

        for (stroke in view.strokes) {
            if (RectF.intersects(marquee, stroke.cachedScreenBounds)) {
                view.selectedStrokeIds.add(stroke.id)
            }
        }
        val delegate = view.pageDelegate
        if (delegate != null) {
            for (shape in view.shapes) {
                val topLeft = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
                val bottomRight = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
                val bounds = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                if (RectF.intersects(marquee, bounds)) {
                    view.selectedShapeIds.add(shape.id)
                }
            }
        }
        view.onSelectionChangedListener?.invoke(view.hasSelection())
    }
    
    fun finalizeLassoSelection() {
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
        val lassoRegion = Region()
        lassoRegion.setPath(
            lasso,
            Region(
                lassoBounds.left.toInt(),
                lassoBounds.top.toInt(),
                lassoBounds.right.toInt(),
                lassoBounds.bottom.toInt()
            )
        )
        for (stroke in view.strokes) {
            val b = stroke.cachedScreenBounds
            if (b.isEmpty) continue
            // Check all four corners and center — if any point is inside lasso, select it
            val testPoints = listOf(
                android.graphics.Point(b.centerX().toInt(), b.centerY().toInt()),
                android.graphics.Point(b.left.toInt(), b.top.toInt()),
                android.graphics.Point(b.right.toInt(), b.top.toInt()),
                android.graphics.Point(b.left.toInt(), b.bottom.toInt()),
                android.graphics.Point(b.right.toInt(), b.bottom.toInt())
            )
            if (testPoints.any { lassoRegion.contains(it.x, it.y) }) {
                view.selectedStrokeIds.add(stroke.id)
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

        for (stroke in view.strokes.filter { it.id in view.selectedStrokeIds }) {
            val b = stroke.cachedScreenBounds
            if (!b.isEmpty) {
                left = minOf(left, b.left); top = minOf(top, b.top)
                right = maxOf(right, b.right); bottom = maxOf(bottom, b.bottom)
                hasBounds = true
            }
        }

        val delegate = view.pageDelegate
        if (delegate != null) {
            for (shape in view.shapes.filter { it.id in view.selectedShapeIds }) {
                val tl = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
                val br = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
                left = minOf(left, tl.x, br.x); top = minOf(top, tl.y, br.y)
                right = maxOf(right, tl.x, br.x); bottom = maxOf(bottom, tl.y, br.y)
                hasBounds = true
            }
        }

        return if (hasBounds) RectF(left, top, right, bottom) else null
    }
    
    fun captureInitialState() {
        capturedStrokes =
            view.strokes.map { stroke ->
                stroke.copy(points = stroke.points.map { PointF(it.x, it.y) }.toMutableList())
            }
        capturedShapes =
            view.shapes.map { shape ->
                shape.copy(points = shape.points?.map { point -> PointF(point.x, point.y) })
            }
    }
    
    fun commitStateChange() {
        if (capturedStrokes.isEmpty() && capturedShapes.isEmpty()) return
        val snapshot = Pair(capturedStrokes, capturedShapes)
        view.pushUndoSnapshot(snapshot)
        capturedStrokes = emptyList()
        capturedShapes = emptyList()
        view.onChangeListener?.onChange()
    }
    
    fun liveUpdateProperties(color: Int? = null, width: Float? = null, alpha: Int? = null) {
        for (strokeId in view.selectedStrokeIds) {
            view.strokes.find { it.id == strokeId }?.let { stroke ->
                val newStroke = stroke.copy(
                    color = color ?: stroke.color,
                    strokeWidth = width ?: stroke.strokeWidth
                )
                val index = view.strokes.indexOf(stroke)
                if (index != -1) view.strokes[index] = newStroke
            }
        }
        for (shapeId in view.selectedShapeIds) {
            view.shapes.find { it.id == shapeId }?.let { shape ->
                val newShape = shape.copy(
                    strokeColor = color ?: shape.strokeColor,
                    strokeWidth = width ?: shape.strokeWidth,
                    fillAlpha = alpha ?: shape.fillAlpha
                )
                val index = view.shapes.indexOf(shape)
                if (index != -1) view.shapes[index] = newShape
            }
        }
        view.invalidate()
    }
    
    fun updateProperties(color: Int? = null, width: Float? = null) {
        liveUpdateProperties(color = color, width = width)
        commitStateChange()
    }
}
