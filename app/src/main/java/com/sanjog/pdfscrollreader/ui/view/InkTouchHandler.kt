package com.sanjog.pdfscrollreader.ui.view

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

class InkTouchHandler(private val view: InkCanvasView) {
    private var shapeStartX: Float = 0f
    private var shapeStartY: Float = 0f
    private var marqueeStartX: Float = 0f
    private var marqueeStartY: Float = 0f
    private var activeDragMode = DragMode.NONE
    private var activeResizeHandle = InkCanvasView.HandleType.NONE
    private var resizeAnchor = PointF()
    private val lassoPoints = mutableListOf<PointF>()
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var selectionMoved = false

    fun handleDown(x: Float, y: Float) {
        view.onDrawingStateChangedListener?.invoke(true)
        android.util.Log.d("INK_DEBUG", "handleDown: x=$x y=$y tool=${view.currentTool} delegate=${view.pageDelegate != null}")
        val delegate = view.pageDelegate
        val pageIndex = (delegate?.getPageIndexAtPoint(x, y) ?: 0).coerceAtLeast(0)

        lastTouchX = x
        lastTouchY = y
        selectionMoved = false

        if (isSelectionTool(view.currentTool) && view.hasSelection()) {
            val handle = view.getSelectionHandle(x, y, 48f)
            if (handle != InkCanvasView.HandleType.NONE) {
                activeDragMode = DragMode.RESIZE
                activeResizeHandle = handle
                val bounds = view.getSelectionBounds()!!
                resizeAnchor = when (handle) {
                    InkCanvasView.HandleType.RESIZE_TL -> PointF(bounds.right, bounds.bottom)
                    InkCanvasView.HandleType.RESIZE_TR -> PointF(bounds.left, bounds.bottom)
                    InkCanvasView.HandleType.RESIZE_BL -> PointF(bounds.right, bounds.top)
                    InkCanvasView.HandleType.RESIZE_BR -> PointF(bounds.left, bounds.top)
                    else -> PointF()
                }
                return
            }
            
            if (view.selectionTransform.hitsSelectionBounds(x, y, 24f)) {
                activeDragMode = DragMode.MOVE
                return
            }
        }

        when (view.currentTool) {
            ToolType.PEN, ToolType.HIGHLIGHTER -> startStroke(x, y, pageIndex, delegate)
            ToolType.ERASER -> view.eraseAtPoint(x, y)
            ToolType.RECT, ToolType.ELLIPSE -> {
                shapeStartX = x
                shapeStartY = y
                view.shapePreviewRect = null
            }
            ToolType.SELECT_BOX -> {
                marqueeStartX = x
                marqueeStartY = y
                activeDragMode = DragMode.MARQUEE
                view.marqueeRect = RectF(x, y, x, y)
            }
            ToolType.LASSO, ToolType.LASSO_FILL -> {
                activeDragMode = DragMode.LASSO
                view.lassoPath = Path().apply { moveTo(x, y) }
                lassoPoints.clear()
                lassoPoints.add(PointF(x, y))
            }
            ToolType.SELECT_TAP -> view.selectAtPoint(x, y, delegate)
        }
    }

    fun handleMove(x: Float, y: Float) {
        if (activeDragMode == DragMode.MOVE) {
            if (!selectionMoved) {
                view.captureInitialSelectionState()
                selectionMoved = true
            }
            view.translateSelection(lastTouchX, lastTouchY, x, y)
            lastTouchX = x
            lastTouchY = y
            return
        }
        
        if (activeDragMode == DragMode.RESIZE) {
            if (!selectionMoved) {
                view.captureInitialSelectionState()
                selectionMoved = true
            }
            view.resizeSelection(resizeAnchor, lastTouchX, lastTouchY, x, y)
            lastTouchX = x
            lastTouchY = y
            return
        }

        when (view.currentTool) {
            ToolType.PEN, ToolType.HIGHLIGHTER -> continueStroke(x, y)
            ToolType.ERASER -> view.eraseAtPoint(x, y)
            ToolType.RECT, ToolType.ELLIPSE -> {
                view.shapePreviewRect =
                    RectF(
                        minOf(shapeStartX, x),
                        minOf(shapeStartY, y),
                        maxOf(shapeStartX, x),
                        maxOf(shapeStartY, y)
                    )
            }
            ToolType.SELECT_BOX -> {
                view.marqueeRect?.set(
                    minOf(marqueeStartX, x),
                    minOf(marqueeStartY, y),
                    maxOf(marqueeStartX, x),
                    maxOf(marqueeStartY, y)
                )
            }
            ToolType.LASSO, ToolType.LASSO_FILL -> {
                view.lassoPath?.lineTo(x, y)
                lassoPoints.add(PointF(x, y))
            }
            else -> Unit
        }
    }

    fun handleUp(x: Float, y: Float) {
        view.onDrawingStateChangedListener?.invoke(false)
        if (activeDragMode == DragMode.MOVE || activeDragMode == DragMode.RESIZE) {
            if (selectionMoved) {
                view.commitSelectionStateChange()
            }
            resetGestureState()
            return
        }

        val delegate = view.pageDelegate
        val pageIndex = (delegate?.getPageIndexAtPoint(x, y) ?: 0).coerceAtLeast(0)

        when (view.currentTool) {
            ToolType.PEN, ToolType.HIGHLIGHTER -> finalizeStroke()
            ToolType.RECT -> commitRect(x, y, pageIndex, delegate)
            ToolType.ELLIPSE -> commitEllipse(x, y, pageIndex, delegate)
            ToolType.ERASER -> view.eraseAtPoint(x, y)
            ToolType.SELECT_BOX -> view.finalizeMarqueeSelection()
            ToolType.LASSO -> view.finalizeLassoSelection(lassoPoints.toList(), pageIndex)
            ToolType.LASSO_FILL -> {
                view.addFreeformShapeFromLasso(lassoPoints.toList(), pageIndex)
            }
            else -> Unit
        }

        resetGestureState()
    }

    private fun resetGestureState() {
        activeDragMode = DragMode.NONE
        activeResizeHandle = InkCanvasView.HandleType.NONE
        view.shapePreviewRect = null
        view.marqueeRect = null
        view.lassoPath = null
        lassoPoints.clear()
        selectionMoved = false
    }

    private fun startStroke(x: Float, y: Float, pageIndex: Int, delegate: PageDelegate?) {
        val color = if (view.currentTool == ToolType.PEN) view.currentPenColor else view.currentHighlighterColor
        val width = if (view.currentTool == ToolType.PEN) view.currentPenWidth else view.currentHighlighterWidth
        val firstPoint = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)
        val stroke =
            Stroke(
                id = java.util.UUID.randomUUID().toString(),
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

    private fun commitRect(x: Float, y: Float, pageIndex: Int, delegate: PageDelegate?) {
        val normStart = delegate?.normalizePoint(pageIndex, shapeStartX, shapeStartY) ?: PointF(shapeStartX, shapeStartY)
        val normEnd = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)

        val color = view.currentPenColor
        val width = view.currentPenWidth
        val shape =
            ShapeAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                shapeType = ShapeType.RECTANGLE,
                normLeft = minOf(normStart.x, normEnd.x),
                normTop = minOf(normStart.y, normEnd.y),
                normRight = maxOf(normStart.x, normEnd.x),
                normBottom = maxOf(normStart.y, normEnd.y),
                strokeColor = color,
                strokeWidth = width / view.currentZoom,
                fillColor = color,
                fillAlpha = view.shapeFillAlpha
            )

        view.addShape(shape)
    }

    private fun commitEllipse(x: Float, y: Float, pageIndex: Int, delegate: PageDelegate?) {
        val normStart = delegate?.normalizePoint(pageIndex, shapeStartX, shapeStartY) ?: PointF(shapeStartX, shapeStartY)
        val normEnd = delegate?.normalizePoint(pageIndex, x, y) ?: PointF(x, y)

        val color = view.currentPenColor
        val width = view.currentPenWidth
        val shape =
            ShapeAnnotation(
                id = java.util.UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                shapeType = ShapeType.ELLIPSE,
                normLeft = minOf(normStart.x, normEnd.x),
                normTop = minOf(normStart.y, normEnd.y),
                normRight = maxOf(normStart.x, normEnd.x),
                normBottom = maxOf(normStart.y, normEnd.y),
                strokeColor = color,
                strokeWidth = width / view.currentZoom,
                fillColor = color,
                fillAlpha = view.shapeFillAlpha
            )

        view.addShape(shape)
    }

    private fun isSelectionTool(tool: ToolType): Boolean {
        return tool == ToolType.SELECT_TAP ||
            tool == ToolType.SELECT_BOX ||
            tool == ToolType.LASSO ||
            tool == ToolType.LASSO_FILL
    }
}
