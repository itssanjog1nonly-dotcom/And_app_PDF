package com.sanjog.pdfscrollreader.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class InkRenderer(private val view: InkCanvasView) {

    private val strokePaint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private val fillPaint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

    private val selectionPaint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#FF2196F3")
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

    private val selectionFillPaint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            color = Color.parseColor("#202196F3")
        }

    fun draw(canvas: Canvas) {
        android.util.Log.d(
            "INK_DEBUG",
            "draw: strokes=${view.strokes.size} activeStroke=${view.activeStroke != null} delegate=${view.pageDelegate != null} editingEnabled=${view.isEditingEnabled()}"
        )
        val delegate = view.pageDelegate

        for (stroke in view.strokes) {
            val bounds = delegate?.getPageBounds(stroke.pageIndex)
            if (bounds != null) {
                canvas.save()
                canvas.clipRect(bounds)
                stroke.updatePath(delegate)
                if (!stroke.path.isEmpty) {
                    strokePaint.color = stroke.color
                    strokePaint.strokeWidth = stroke.strokeWidth * view.currentZoom
                    strokePaint.alpha = if (stroke.tool == ToolType.HIGHLIGHTER) 100 else 255
                    strokePaint.style = Paint.Style.STROKE
                    canvas.drawPath(stroke.path, strokePaint)
                }
                canvas.restore()
            }
        }

        for (shape in view.shapes) {
            val bounds = delegate?.getPageBounds(shape.pageIndex)
            if (bounds != null) {
                canvas.save()
                canvas.clipRect(bounds)
                drawShapeScreenSpace(canvas, shape, delegate)
                canvas.restore()
            }
        }

        view.activeStroke?.let { stroke ->
            val bounds = delegate?.getPageBounds(stroke.pageIndex)
            if (bounds != null) {
                canvas.save()
                canvas.clipRect(bounds)
                stroke.updatePath(delegate, force = true)
                if (!stroke.path.isEmpty) {
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.color = stroke.color
                    strokePaint.strokeWidth = stroke.strokeWidth * view.currentZoom
                    strokePaint.alpha = if (stroke.tool == ToolType.HIGHLIGHTER) 100 else 255
                    canvas.drawPath(stroke.path, strokePaint)
                }
                canvas.restore()
            }
        }

        view.shapePreviewRect?.let { rect ->
            val shapeType =
                when (view.currentTool) {
                    ToolType.RECT -> ShapeType.RECTANGLE
                    ToolType.ELLIPSE -> ShapeType.ELLIPSE
                    else -> null
                }
            if (shapeType != null) {
                drawShapeGeometry(
                    canvas = canvas,
                    rect = rect,
                    shapeType = shapeType,
                    fillColor = view.currentPenColor,
                    fillAlpha = view.shapeFillAlpha,
                    strokeColor = view.currentPenColor,
                    strokeWidth = view.currentPenWidth
                )
            }
        }

        view.marqueeRect?.let {
            selectionPaint.style = Paint.Style.STROKE
            canvas.drawRect(it, selectionPaint)
            canvas.drawRect(it, selectionFillPaint)
        }

        view.lassoPath?.let { path ->
            if (view.currentTool == ToolType.LASSO_FILL) {
                canvas.drawPath(path, selectionFillPaint)
            }
            canvas.drawPath(path, selectionPaint)
        }

        if (view.hasSelection()) {
            val bounds = view.getSelectionBounds()
            if (bounds != null) {
                selectionPaint.style = Paint.Style.STROKE
                selectionPaint.pathEffect = null // solid line for selection
                canvas.drawRect(bounds, selectionPaint)
                canvas.drawRect(bounds, selectionFillPaint)

                // Restore dash for next draw if needed
                selectionPaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)

                // Draw handles
                val handleSize = 12f * view.currentZoom
                selectionPaint.style = Paint.Style.FILL
                canvas.drawCircle(bounds.left, bounds.top, handleSize, selectionPaint)
                canvas.drawCircle(bounds.right, bounds.top, handleSize, selectionPaint)
                canvas.drawCircle(bounds.left, bounds.bottom, handleSize, selectionPaint)
                canvas.drawCircle(bounds.right, bounds.bottom, handleSize, selectionPaint)
            }
        }
    }

    private fun drawShape(canvas: Canvas, shape: ShapeAnnotation, delegate: PageDelegate) {
        val topLeft = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
        val bottomRight = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
        if (topLeft.x <= -999f || topLeft.y <= -999f || bottomRight.x <= -999f || bottomRight.y <= -999f) {
            return
        }
        val rect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
        drawShapeGeometry(
            canvas = canvas,
            rect = rect,
            shapeType = shape.shapeType,
            fillColor = shape.fillColor,
            fillAlpha = shape.fillAlpha,
            strokeColor = shape.strokeColor,
            strokeWidth = shape.strokeWidth,
            shape = shape
        )
    }

    private fun drawShapeScreenSpace(
        canvas: Canvas,
        shape: ShapeAnnotation,
        delegate: PageDelegate?
    ) {
        val rect =
            if (delegate != null) {
                val topLeft = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
                val bottomRight = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
                if (topLeft.x <= -999f || topLeft.y <= -999f || bottomRight.x <= -999f || bottomRight.y <= -999f) {
                    RectF(shape.normLeft, shape.normTop, shape.normRight, shape.normBottom)
                } else {
                    RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                }
            } else {
                RectF(shape.normLeft, shape.normTop, shape.normRight, shape.normBottom)
            }

        if (rect.isEmpty || rect.width() < 1f || rect.height() < 1f) return

        drawShapeGeometry(
            canvas = canvas,
            rect = rect,
            shapeType = shape.shapeType,
            fillColor = shape.fillColor,
            fillAlpha = shape.fillAlpha,
            strokeColor = shape.strokeColor,
            strokeWidth = shape.strokeWidth,
            shape = shape
        )
    }

    private fun drawShapeGeometry(
        canvas: Canvas,
        rect: RectF,
        shapeType: ShapeType,
        fillColor: Int,
        fillAlpha: Int,
        strokeColor: Int,
        strokeWidth: Float,
        shape: ShapeAnnotation? = null
    ) {
        if (fillAlpha > 0) {
            fillPaint.color = fillColor
            fillPaint.alpha = fillAlpha.coerceIn(0, 255)
            fillPaint.style = Paint.Style.FILL
            drawShapePrimitive(canvas, rect, shapeType, fillPaint, shape)
        }

        strokePaint.color = strokeColor
        strokePaint.strokeWidth = resolveShapeBorderWidth(strokeWidth)
        strokePaint.alpha = resolveShapeBorderAlpha(fillAlpha)
        strokePaint.style = Paint.Style.STROKE
        
        drawShapePrimitive(canvas, rect, shapeType, strokePaint, shape)
    }

    private fun drawShapePrimitive(
        canvas: Canvas,
        rect: RectF,
        shapeType: ShapeType,
        paint: Paint,
        shape: ShapeAnnotation? = null
    ) {
        when (shapeType) {
            ShapeType.RECTANGLE -> canvas.drawRect(rect, paint)
            ShapeType.ELLIPSE -> canvas.drawOval(rect, paint)
            ShapeType.FREEFORM -> {
                if (shape != null) {
                    shape.updatePath(view.pageDelegate)
                    if (!shape.path.isEmpty) {
                        canvas.drawPath(shape.path, paint)
                    }
                }
            }
        }
    }

    private fun resolveShapeBorderWidth(strokeWidth: Float): Float {
        val logicalWidth = (strokeWidth * view.currentZoom).coerceAtLeast(1f)
        return (logicalWidth * 0.25f).coerceIn(0.5f, 4f)
    }

    private fun resolveShapeBorderAlpha(fillAlpha: Int): Int {
        val clampedFillAlpha = fillAlpha.coerceIn(0, 255)
        return if (clampedFillAlpha == 0) 180 else (clampedFillAlpha * 0.8f).toInt().coerceAtLeast(36)
    }
}
