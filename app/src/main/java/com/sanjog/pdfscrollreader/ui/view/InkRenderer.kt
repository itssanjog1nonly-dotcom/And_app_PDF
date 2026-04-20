package com.sanjog.pdfscrollreader.ui.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

class InkRenderer(private val view: InkCanvasView) {
    
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val selectionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#FF2196F3")
    }

    fun draw(canvas: Canvas) {
        android.util.Log.d("INK_DEBUG", "draw: strokes=${view.strokes.size} activeStroke=${view.activeStroke != null} delegate=${view.pageDelegate != null} editingEnabled=${view.isEditingEnabled()}")
        val delegate = view.pageDelegate

        for (stroke in view.strokes) {
            stroke.updatePath(delegate)
            if (!stroke.path.isEmpty) {
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.strokeWidth * view.currentZoom
                strokePaint.alpha = if (stroke.tool == InkCanvasView.ToolType.HIGHLIGHTER) 100 else 255
                strokePaint.style = Paint.Style.STROKE
                canvas.drawPath(stroke.path, strokePaint)
            }
        }

        if (delegate != null) {
            for (shape in view.shapes) {
                drawShape(canvas, shape, delegate)
            }
        }

        view.activeStroke?.let { stroke ->
            delegate?.let { stroke.updatePath(it, force = true) }
            if (!stroke.path.isEmpty) {
                strokePaint.style = Paint.Style.STROKE
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.strokeWidth * view.currentZoom
                strokePaint.alpha = if (stroke.tool == InkCanvasView.ToolType.HIGHLIGHTER) 100 else 255
                canvas.drawPath(stroke.path, strokePaint)
            }
        }

        view.shapePreviewRect?.let { rect ->
            strokePaint.style = Paint.Style.STROKE
            strokePaint.color = view.currentPenColor
            strokePaint.strokeWidth = view.currentPenWidth * view.currentZoom
            strokePaint.alpha = 255
            if (view.currentTool == InkCanvasView.ToolType.RECT) {
                canvas.drawRect(rect, strokePaint)
            } else if (view.currentTool == InkCanvasView.ToolType.ELLIPSE) {
                canvas.drawOval(rect, strokePaint)
            }
        }

        view.marqueeRect?.let {
            selectionPaint.style = Paint.Style.STROKE
            canvas.drawRect(it, selectionPaint)
            val fill = Paint().apply { 
                color = Color.parseColor("#202196F3") 
                style = Paint.Style.FILL 
            }
            canvas.drawRect(it, fill)
        }

        view.lassoPath?.let { canvas.drawPath(it, selectionPaint) }

        if (view.hasSelection()) {
            val bounds = view.getSelectionBounds()
            bounds?.let {
                selectionPaint.color = Color.parseColor("#FF2196F3")
                canvas.drawRect(it, selectionPaint)
            }
        }
    }

    private fun drawShape(canvas: Canvas, shape: InkCanvasView.ShapeAnnotation, delegate: InkCanvasView.PageDelegate) {
        val topLeft = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
        val bottomRight = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
        if (topLeft.x <= -999f || topLeft.y <= -999f || bottomRight.x <= -999f || bottomRight.y <= -999f) {
            return
        }
        val rect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)

        if (shape.fillAlpha > 0) {
            fillPaint.color = shape.fillColor
            fillPaint.alpha = shape.fillAlpha
            fillPaint.style = Paint.Style.FILL
            if (shape.shapeType == InkCanvasView.ShapeType.RECTANGLE) {
                canvas.drawRect(rect, fillPaint)
            } else if (shape.shapeType == InkCanvasView.ShapeType.ELLIPSE) {
                canvas.drawOval(rect, fillPaint)
            }
        }

        strokePaint.color = shape.strokeColor
        strokePaint.strokeWidth = shape.strokeWidth * view.currentZoom
        strokePaint.style = Paint.Style.STROKE
        if (shape.shapeType == InkCanvasView.ShapeType.RECTANGLE) {
            canvas.drawRect(rect, strokePaint)
        } else if (shape.shapeType == InkCanvasView.ShapeType.ELLIPSE) {
            canvas.drawOval(rect, strokePaint)
        }
    }
}
