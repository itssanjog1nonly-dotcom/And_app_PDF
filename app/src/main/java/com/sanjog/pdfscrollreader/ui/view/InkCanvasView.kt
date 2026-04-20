package com.sanjog.pdfscrollreader.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class ToolType {
        PEN, HIGHLIGHTER, ERASER, RECT, ELLIPSE, SELECT_TAP, SELECT_BOX, LASSO, LASSO_FILL
    }

    enum class EraserMode { AREA, STROKE }

    enum class DragMode { NONE, MARQUEE, MOVE, RESIZE, LASSO }

    enum class ShapeType { RECTANGLE, ELLIPSE, FREEFORM }

    data class Stroke(
        val id: String = java.util.UUID.randomUUID().toString(),
        val pageIndex: Int,
        val points: MutableList<PointF>,
        val color: Int,
        val strokeWidth: Float,
        val tool: ToolType,
        val eraserMode: EraserMode = EraserMode.AREA
    ) {
        @Transient private var _path: Path? = null
        val path: Path get() { if (_path == null) _path = Path(); return _path!! }
        @Transient private var lastProjectedBounds: RectF? = null
        @Transient var cachedScreenBounds: RectF = RectF()

        fun updatePath(delegate: PageDelegate?, force: Boolean = false) {
            android.util.Log.d("INK_DEBUG", "updatePath: delegate=${delegate != null} points=${points.size} force=$force")
            if (delegate != null) {
                // Full projection mode: use page coordinates
                val currentBounds = delegate.getPageBounds(pageIndex)
                if (!force && lastProjectedBounds != null && currentBounds != null &&
                    kotlin.math.abs(currentBounds.left - lastProjectedBounds!!.left) < 0.01f &&
                    kotlin.math.abs(currentBounds.top - lastProjectedBounds!!.top) < 0.01f &&
                    kotlin.math.abs(currentBounds.width() - lastProjectedBounds!!.width()) < 0.01f &&
                    kotlin.math.abs(currentBounds.height() - lastProjectedBounds!!.height()) < 0.01f &&
                    !path.isEmpty) return
                if (currentBounds != null) lastProjectedBounds = RectF(currentBounds)
                path.reset()
                if (points.isEmpty()) { cachedScreenBounds.setEmpty(); return }
                val first = delegate.projectPoint(pageIndex, points[0].x, points[0].y)
                // If projection returns invalid coords, fall through to screen mode
                if (first.x > -999f && first.y > -999f) {
                    path.moveTo(first.x, first.y)
                    for (i in 1 until points.size) {
                        val p = delegate.projectPoint(pageIndex, points[i].x, points[i].y)
                        path.lineTo(p.x, p.y)
                    }
                    path.computeBounds(cachedScreenBounds, true)
                    return
                }
            }
            // Screen coordinate fallback: points are already in screen space
            path.reset()
            if (points.isEmpty()) { cachedScreenBounds.setEmpty(); return }
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            path.computeBounds(cachedScreenBounds, true)
            android.util.Log.d("INK_DEBUG", "updatePath fallback: bounds=$cachedScreenBounds")
        }

        fun intersects(x: Float, y: Float, tolerance: Float, delegate: PageDelegate?): Boolean {
            if (delegate == null) return false
            updatePath(delegate)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            if (!bounds.intersects(x - tolerance, y - tolerance, x + tolerance, y + tolerance)) return false
            for (pointNorm in points) {
                val point = delegate.projectPoint(pageIndex, pointNorm.x, pointNorm.y)
                val dx = point.x - x
                val dy = point.y - y
                if (dx * dx + dy * dy <= tolerance * tolerance) return true
            }
            return false
        }
    }

    data class ShapeAnnotation(
        val id: String = java.util.UUID.randomUUID().toString(),
        val pageIndex: Int,
        val shapeType: ShapeType,
        val normLeft: Float,
        val normTop: Float,
        val normRight: Float,
        val normBottom: Float,
        val strokeColor: Int,
        val strokeWidth: Float,
        val fillColor: Int,
        val fillAlpha: Int,
        val points: List<PointF>? = null
    ) {
        fun intersects(x: Float, y: Float, tolerance: Float, delegate: PageDelegate): Boolean {
            if (shapeType == ShapeType.FREEFORM && points != null && points.isNotEmpty()) {
                val path = Path()
                val start = delegate.projectPoint(pageIndex, points[0].x, points[0].y)
                path.moveTo(start.x, start.y)
                for (i in 1 until points.size) {
                    val p = delegate.projectPoint(pageIndex, points[i].x, points[i].y)
                    path.lineTo(p.x, p.y)
                }
                path.close()
                val bounds = RectF()
                path.computeBounds(bounds, true)
                val region = android.graphics.Region()
                region.setPath(path, android.graphics.Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt()))
                return region.contains(x.toInt(), y.toInt())
            }
            val topLeft = delegate.projectPoint(pageIndex, normLeft, normTop)
            val bottomRight = delegate.projectPoint(pageIndex, normRight, normBottom)
            if (topLeft.x < -10000f || topLeft.y < -10000f || bottomRight.x < -10000f || bottomRight.y < -10000f) return false
            val screenRect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
            return if (shapeType == ShapeType.RECTANGLE) {
                val checkRect = RectF(screenRect.left - tolerance, screenRect.top - tolerance, screenRect.right + tolerance, screenRect.bottom + tolerance)
                checkRect.contains(x, y)
            } else {
                val cx = screenRect.centerX()
                val cy = screenRect.centerY()
                val rx = screenRect.width() / 2 + tolerance
                val ry = screenRect.height() / 2 + tolerance
                if (rx < 0.0001f || ry < 0.0001f) return false
                val dx = x - cx
                val dy = y - cy
                (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1.0f
            }
        }
    }

    interface OnChangeListener {
        fun onChange()
    }

    interface PageDelegate {
        fun getVisiblePageIndices(): List<Int>
        fun getPageBounds(pageIndex: Int): RectF?
        fun getPageIndexAtPoint(x: Float, y: Float): Int
        fun normalizePoint(pageIndex: Int, x: Float, y: Float): PointF
        fun projectPoint(pageIndex: Int, normX: Float, normY: Float): PointF
    }

    var pageDelegate: PageDelegate? = null
        private set
    var isDebugEnabled = false
    private var _editingEnabled: Boolean = false
    fun isEditingEnabled(): Boolean = _editingEnabled
    fun setEditingEnabled(enabled: Boolean) { _editingEnabled = enabled }

    var currentTool: ToolType = ToolType.PEN
    var shapeFillAlpha: Int = 128
    var onChangeListener: OnChangeListener? = null
    var onSelectionChangedListener: ((Boolean) -> Unit)? = null
    var currentPenColor: Int = Color.BLACK
    var currentPenWidth: Float = 4f
    var currentHighlighterColor: Int = Color.parseColor("#80FF0000")
    var currentHighlighterWidth: Float = 20f
    var currentZoom: Float = 1f
    private var currentEraserMode: EraserMode = EraserMode.AREA
    private val noDrawRegions = mutableListOf<android.graphics.Rect>()

    internal val strokes = mutableListOf<Stroke>()
    internal val shapes = mutableListOf<ShapeAnnotation>()
    internal val selectedStrokeIds = mutableSetOf<String>()
    internal val selectedShapeIds = mutableSetOf<String>()

    internal var shapePreviewRect: RectF? = null
    internal var marqueeRect: RectF? = null
    internal var lassoPath: Path? = null
    internal var activeStroke: Stroke? = null

    private val undoStack = java.util.LinkedList<Pair<List<Stroke>, List<ShapeAnnotation>>>()
    private val redoStack = java.util.LinkedList<Pair<List<Stroke>, List<ShapeAnnotation>>>()
    private val MAX_HISTORY = 50

    private val touchHandler = InkTouchHandler(this)
    private val renderer = InkRenderer(this)
    private val selectionHandler = InkSelectionHandler(this)

    fun setNoDrawRegions(regions: List<android.graphics.Rect>) {
        noDrawRegions.clear()
        noDrawRegions.addAll(regions.map { android.graphics.Rect(it) })
        invalidate()
    }
    fun setPageDelegate(delegate: PageDelegate) { pageDelegate = delegate }
    fun setZoom(zoom: Float) { currentZoom = zoom }
    fun cancelCurrentGesture() {
        activeStroke = null
        shapePreviewRect = null
        marqueeRect = null
        lassoPath = null
        invalidate()
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        invalidate()
    }

    fun setShapes(newShapes: List<ShapeAnnotation>) {
        shapes.clear()
        shapes.addAll(newShapes)
        invalidate()
    }

    fun getStrokes(): List<Stroke> = strokes.toList()
    fun getShapes(): List<ShapeAnnotation> = shapes.toList()

    fun setPenColor(color: Int) { currentPenColor = color }
    fun setHighlighterColor(color: Int) { currentHighlighterColor = color }
    fun setPenWidth(width: Float) { currentPenWidth = width }
    fun setHighlighterWidth(width: Float) { currentHighlighterWidth = width }
    fun setTool(tool: ToolType) { currentTool = tool }
    fun setEraserMode(mode: EraserMode) { currentEraserMode = mode }
    fun getEraserMode(): EraserMode = currentEraserMode

    fun hasSelection(): Boolean = selectedStrokeIds.isNotEmpty() || selectedShapeIds.isNotEmpty()

    fun deleteSelected() { selectionHandler.deleteSelected() }
    fun undo() {
        if (undoStack.isEmpty()) return
        val current = snapshot()
        redoStack.add(current)
        if (redoStack.size > MAX_HISTORY) redoStack.removeFirst()
        
        val snapshot = undoStack.removeLast()
        restoreSnapshot(snapshot)
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        val current = snapshot()
        undoStack.add(current)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        
        val snapshot = redoStack.removeLast()
        restoreSnapshot(snapshot)
    }
    fun clearAll() {
        if (strokes.isNotEmpty() || shapes.isNotEmpty()) {
            pushUndo()
            strokes.clear()
            shapes.clear()
            selectedStrokeIds.clear()
            selectedShapeIds.clear()
            redoStack.clear()
            onChangeListener?.onChange()
            onSelectionChangedListener?.invoke(false)
            invalidate()
        }
    }
    fun captureInitialSelectionState() { selectionHandler.captureInitialState() }
    fun commitSelectionStateChange() { selectionHandler.commitStateChange() }
    fun liveUpdateSelectedProperties(color: Int? = null, width: Float? = null, alpha: Int? = null) {
        selectionHandler.liveUpdateProperties(color, width, alpha)
    }
    fun updateSelectedProperties(color: Int? = null, width: Float? = null) {
        selectionHandler.captureInitialState()
        selectionHandler.updateProperties(color, width)
    }

    internal fun pushUndo() {
        val snapshot = snapshot()
        undoStack.add(snapshot)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    internal fun pushUndoSnapshot(snapshot: Pair<List<Stroke>, List<ShapeAnnotation>>) {
        undoStack.add(snapshot)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    internal fun addActiveStroke(stroke: Stroke) { activeStroke = stroke }
    fun addStroke(stroke: Stroke) {
        pushUndo()
        strokes.add(stroke)
        selectedStrokeIds.clear()
        selectedShapeIds.clear()
        onChangeListener?.onChange()
        onSelectionChangedListener?.invoke(false)
        invalidate()
    }
    fun addShape(shape: ShapeAnnotation) {
        pushUndo()
        shapes.add(shape)
        selectedStrokeIds.clear()
        selectedShapeIds.clear()
        onChangeListener?.onChange()
        onSelectionChangedListener?.invoke(false)
        invalidate()
    }

    internal fun selectAtPoint(x: Float, y: Float, delegate: PageDelegate?) {
        selectionHandler.selectAtPoint(x, y, delegate)
    }
    internal fun finalizeMarqueeSelection() {
        selectionHandler.finalizeMarqueeSelection()
    }
    internal fun finalizeLassoSelection() {
        selectionHandler.finalizeLassoSelection()
    }

    fun getSelectionBounds(): RectF? = selectionHandler.getSelectionBounds()

    internal fun eraseAtPoint(x: Float, y: Float) {
        val tolerance = (24f / currentZoom).coerceAtLeast(12f)
        val delegate = pageDelegate
        val hitStrokeIds =
            when (currentEraserMode) {
                EraserMode.STROKE ->
                    strokes.asReversed()
                        .firstOrNull { it.intersects(x, y, tolerance, delegate) || RectF(it.cachedScreenBounds).apply { inset(-tolerance, -tolerance) }.contains(x, y) }
                        ?.let { setOf(it.id) }
                        .orEmpty()
                EraserMode.AREA ->
                    strokes
                        .filter {
                            it.intersects(x, y, tolerance, delegate) ||
                                RectF(it.cachedScreenBounds).apply { inset(-tolerance, -tolerance) }.contains(x, y)
                        }
                        .mapTo(mutableSetOf()) { it.id }
            }

        val hitShapeIds =
            when (currentEraserMode) {
                EraserMode.STROKE ->
                    shapes.asReversed()
                        .firstOrNull { delegate != null && it.intersects(x, y, tolerance, delegate) }
                        ?.let { setOf(it.id) }
                        .orEmpty()
                EraserMode.AREA ->
                    shapes
                        .filter { delegate != null && it.intersects(x, y, tolerance, delegate) }
                        .mapTo(mutableSetOf()) { it.id }
            }

        if (hitStrokeIds.isEmpty() && hitShapeIds.isEmpty()) return

        pushUndo()
        strokes.removeAll { it.id in hitStrokeIds }
        shapes.removeAll { it.id in hitShapeIds }
        selectedStrokeIds.removeAll(hitStrokeIds)
        selectedShapeIds.removeAll(hitShapeIds)
        onChangeListener?.onChange()
        onSelectionChangedListener?.invoke(hasSelection())
        invalidate()
    }

    private fun snapshot(): Pair<List<Stroke>, List<ShapeAnnotation>> {
        val strokeSnapshot =
            strokes.map { stroke ->
                stroke.copy(points = stroke.points.map { PointF(it.x, it.y) }.toMutableList())
            }
        val shapeSnapshot = shapes.map { it.copy(points = it.points?.map { point -> PointF(point.x, point.y) }) }
        return Pair(strokeSnapshot, shapeSnapshot)
    }

    private fun restoreSnapshot(snapshot: Pair<List<Stroke>, List<ShapeAnnotation>>) {
        strokes.clear()
        strokes.addAll(snapshot.first.map { stroke -> stroke.copy(points = stroke.points.map { PointF(it.x, it.y) }.toMutableList()) })
        shapes.clear()
        shapes.addAll(snapshot.second.map { shape -> shape.copy(points = shape.points?.map { point -> PointF(point.x, point.y) }) })
        selectedStrokeIds.clear()
        selectedShapeIds.clear()
        onSelectionChangedListener?.invoke(false)
        onChangeListener?.onChange()
        invalidate()
    }

    private fun isPointBlocked(x: Float, y: Float): Boolean {
        return noDrawRegions.any { it.contains(x.toInt(), y.toInt()) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        android.util.Log.d("INK_DEBUG", "onTouchEvent: action=${event.actionMasked} toolType=${event.getToolType(0)} editingEnabled=$_editingEnabled currentTool=$currentTool")
        if (!_editingEnabled) return false
        val x = event.x
        val y = event.y
        if (isPointBlocked(x, y)) return false

        val toolType = event.getToolType(0)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (!isStylus) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchHandler.handleDown(x, y)
            MotionEvent.ACTION_MOVE -> touchHandler.handleMove(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchHandler.handleUp(x, y)
        }
        invalidate()
        return true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val checkpoint = canvas.save()
        noDrawRegions.forEach { region ->
            canvas.clipOutRect(region)
        }
        renderer.draw(canvas)
        canvas.restoreToCount(checkpoint)
    }
}
