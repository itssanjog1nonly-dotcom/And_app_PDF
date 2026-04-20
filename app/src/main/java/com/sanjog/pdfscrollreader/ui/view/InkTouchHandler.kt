package com.sanjog.pdfscrollreader.ui.view

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

class InkTouchHandler(private val view: InkCanvasView) {
    private var shapeStartX: Float = 0f
    private var shapeStartY: Float = 0f
    private var marqueeStartX: Float = 0f
    private var marqueeStartY: Float = 0f
    private var activeDragMode = InkCanvasView.DragMode.NONE
    private val lassoPoints = mutableListOf<PointF>()
    
    fun handleDown(x: Float, y: Float) {
        android.util.Log.d("INK_DEBUG", "handleDown: x=$x y=$y tool=${view.currentTool} delegate=${view.pageDelegate != null}")
        val delegate = view.pageDelegate
        val pageIndex = (delegate?.getPageIndexAtPoint(x, y) ?: 0).coerceAtLeast(0)

        when (view.currentTool) {
            InkCanvasView.ToolType.PEN, InkCanvasView.ToolType.HIGHLIGHTER -> {
                startStroke(x, y, pageIndex, delegate)
            }
            InkCanvasView.ToolType.ERASER -> view.eraseAtPoint(x, y)
            InkCanvasView.ToolType.RECT, InkCanvasView.ToolType.ELLIPSE -> {
                shapeStartX = x
                shapeStartY = y
                view.shapePreviewRect = null
            }
            InkCanvasView.ToolType.SELECT_BOX -> {
                marqueeStartX = x
                marqueeStartY = y
                activeDragMode = InkCanvasView.DragMode.MARQUEE
                view.marqueeRect = RectF(x, y, x, y)
            }
            InkCanvasView.ToolType.LASSO -> {
                activeDragMode = InkCanvasView.DragMode.LASSO
                view.lassoPath = Path().apply { moveTo(x, y) }
                lassoPoints.clear()
                lassoPoints.add(PointF(x, y))
            }
            InkCanvasView.ToolType.SELECT_TAP, InkCanvasView.ToolType.LASSO_FILL -> {
                view.selectAtPoint(x, y, delegate)
            }
        }
    }
    
    fun handleMove(x: Float, y: Float) {
        when (view.currentTool) {
            InkCanvasView.ToolType.PEN, InkCanvasView.ToolType.HIGHLIGHTER -> continueStroke(x, y)
            InkCanvasView.ToolType.ERASER -> view.eraseAtPoint(x, y)
            InkCanvasView.ToolType.RECT -> {
                view.shapePreviewRect = RectF(
                    minOf(shapeStartX, x), minOf(shapeStartY, y),
                    maxOf(shapeStartX, x), maxOf(shapeStartY, y)
                )
            }
            InkCanvasView.ToolType.ELLIPSE -> {
                view.shapePreviewRect = RectF(
                    minOf(shapeStartX, x), minOf(shapeStartY, y),
                    maxOf(shapeStartX, x), maxOf(shapeStartY, y)
                )
            }
            InkCanvasView.ToolType.SELECT_BOX -> {
                view.marqueeRect?.set(
                    minOf(marqueeStartX, x),
                    minOf(marqueeStartY, y),
                    maxOf(marqueeStartX, x),
                    maxOf(marqueeStartY, y)
                )
            }
            InkCanvasView.ToolType.LASSO -> {
                view.lassoPath?.lineTo(x, y)
                lassoPoints.add(PointF(x, y))
            }
            else -> { }
        }
    }
    
    fun handleUp(x: Float, y: Float) {
        val delegate = view.pageDelegate
        val pageIndex = (delegate?.getPageIndexAtPoint(x, y) ?: 0).coerceAtLeast(0)

        when (view.currentTool) {
            InkCanvasView.ToolType.PEN, InkCanvasView.ToolType.HIGHLIGHTER -> finalizeStroke()
            InkCanvasView.ToolType.RECT -> commitRect(x, y, pageIndex, delegate)
            InkCanvasView.ToolType.ELLIPSE -> commitEllipse(x, y, pageIndex, delegate)
            InkCanvasView.ToolType.ERASER -> view.eraseAtPoint(x, y)
            InkCanvasView.ToolType.SELECT_BOX -> view.finalizeMarqueeSelection()
            InkCanvasView.ToolType.LASSO -> view.finalizeLassoSelection()
            else -> { }
        }
        
        activeDragMode = InkCanvasView.DragMode.NONE
        view.shapePreviewRect = null
        view.marqueeRect = null
        view.lassoPath = null
        lassoPoints.clear()
    }
    
    private fun startStroke(x: Float, y: Float, pageIndex: Int, delegate: InkCanvasView.PageDelegate?) {
        val color = if (view.currentTool == InkCanvasView.ToolType.PEN) view.currentPenColor else view.currentHighlighterColor
        val width = if (view.currentTool == InkCanvasView.ToolType.PEN) view.currentPenWidth else view.currentHighlighterWidth
        // Use normalized coords if delegate available, otherwise fall back to screen coords
        val firstPoint = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)
        val stroke = InkCanvasView.Stroke(
            pageIndex = pageIndex,
            points = mutableListOf(firstPoint),
            color = color,
            strokeWidth = width / view.currentZoom,
            tool = view.currentTool
        )
        android.util.Log.d("INK_DEBUG", "startStroke: delegate=${delegate != null} firstPoint=$firstPoint")
        view.addActiveStroke(stroke)
    }

    private fun continueStroke(x: Float, y: Float) {
        view.activeStroke?.let { stroke ->
            val delegate = view.pageDelegate
            // Use normalized coords if delegate available, otherwise fall back to screen coords
            val point = delegate?.normalizePoint(stroke.pageIndex, x, y) ?: PointF(x, y)
            stroke.points.add(point)
        }
    }
    
    private fun finalizeStroke() {
        android.util.Log.d("INK_DEBUG", "finalizeStroke: activeStroke=${view.activeStroke != null} totalStrokes=${view.strokes.size}")
        view.activeStroke?.let { stroke ->
            view.addStroke(stroke)
            view.activeStroke = null
        }
    }
    
    private fun commitRect(x: Float, y: Float, pageIndex: Int, delegate: InkCanvasView.PageDelegate?) {
        val normStart = delegate?.normalizePoint(pageIndex, shapeStartX, shapeStartY) ?: PointF(shapeStartX, shapeStartY)
        val normEnd = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)

        val shape = InkCanvasView.ShapeAnnotation(
            pageIndex = pageIndex,
            shapeType = InkCanvasView.ShapeType.RECTANGLE,
            normLeft = minOf(normStart.x, normEnd.x),
            normTop = minOf(normStart.y, normEnd.y),
            normRight = maxOf(normStart.x, normEnd.x),
            normBottom = maxOf(normStart.y, normEnd.y),
            strokeColor = view.currentPenColor,
            strokeWidth = view.currentPenWidth,
            fillColor = view.currentPenColor,
            fillAlpha = view.shapeFillAlpha
        )

        view.addShape(shape)
    }
    
    private fun commitEllipse(x: Float, y: Float, pageIndex: Int, delegate: InkCanvasView.PageDelegate?) {
        val normStart = delegate?.normalizePoint(pageIndex, shapeStartX, shapeStartY) ?: PointF(shapeStartX, shapeStartY)
        val normEnd = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)

        val shape = InkCanvasView.ShapeAnnotation(
            pageIndex = pageIndex,
            shapeType = InkCanvasView.ShapeType.ELLIPSE,
            normLeft = minOf(normStart.x, normEnd.x),
            normTop = minOf(normStart.y, normEnd.y),
            normRight = maxOf(normStart.x, normEnd.x),
            normBottom = maxOf(normStart.y, normEnd.y),
            strokeColor = view.currentPenColor,
            strokeWidth = view.currentPenWidth,
            fillColor = view.currentPenColor,
            fillAlpha = view.shapeFillAlpha
        )

        view.addShape(shape)
    }
}
