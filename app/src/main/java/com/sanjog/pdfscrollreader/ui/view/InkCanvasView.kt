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
    var onDrawingStateChangedListener: ((Boolean) -> Unit)? = null
    var onHistoryChangedListener: ((Boolean, Boolean) -> Unit)? = null
    var currentPenColor: Int = Color.BLACK
    var currentPenWidth: Float = 8f
    var currentHighlighterColor: Int = Color.parseColor("#FFFF8800")
    var currentHighlighterWidth: Float = 24f
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

    private val historyManager = InkHistoryManager(50) { canUndo, canRedo ->
        onHistoryChangedListener?.invoke(canUndo, canRedo)
    }

    private val touchHandler = InkTouchHandler(this)
    private val renderer = InkRenderer(this)
    private val selectionHandler = InkSelectionHandler(this)
    internal val selectionTransform = InkSelectionTransform(this)

    fun addFreeformShapeFromLasso(lassoPoints: List<PointF>, pageIndex: Int) {
        if (lassoPoints.size < 3) return
        pushUndo()
        val delegate = pageDelegate
        
        val normalizedPoints = lassoPoints.map { pt ->
            delegate?.normalizePoint(pageIndex, pt.x, pt.y) ?: pt
        }

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = Float.MIN_VALUE
        var bottom = Float.MIN_VALUE
        for (pt in normalizedPoints) {
            left = minOf(left, pt.x)
            top = minOf(top, pt.y)
            right = maxOf(right, pt.x)
            bottom = maxOf(bottom, pt.y)
        }

        val shape = ShapeAnnotation(
            id = java.util.UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            shapeType = ShapeType.FREEFORM,
            normLeft = left,
            normTop = top,
            normRight = right,
            normBottom = bottom,
            strokeColor = currentPenColor,
            strokeWidth = currentPenWidth / currentZoom,
            fillColor = currentPenColor,
            fillAlpha = shapeFillAlpha,
            points = normalizedPoints
        )
        shapes.add(shape)
        onChangeListener?.onChange()
        invalidate()
    }

    fun setNoDrawRegions(regions: List<android.graphics.Rect>) {
        noDrawRegions.clear()
        noDrawRegions.addAll(regions.map { android.graphics.Rect(it) })
        invalidate()
    }
    fun setPageDelegate(delegate: PageDelegate) { pageDelegate = delegate }
    fun setZoom(zoom: Float) {
        currentZoom = zoom
        strokes.forEach { it.invalidateCache() }
        shapes.forEach { it.invalidateCache() }
        invalidate()
    }
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

    fun setPenColor(color: Int) {
        currentPenColor = color
        invalidate()
    }
    fun setHighlighterColor(color: Int) {
        currentHighlighterColor = color
        if (currentTool == ToolType.LASSO_FILL) {
            currentPenColor = color
        }
        invalidate()
    }
    fun setPenWidth(width: Float) {
        currentPenWidth = width
        invalidate()
    }
    fun setHighlighterWidth(width: Float) {
        currentHighlighterWidth = width
        invalidate()
    }
    fun setTool(tool: ToolType) {
        currentTool = tool
        invalidate()
    }
    fun setEraserMode(mode: EraserMode) { currentEraserMode = mode }
    fun getEraserMode(): EraserMode = currentEraserMode

    fun hasSelection(): Boolean = selectedStrokeIds.isNotEmpty() || selectedShapeIds.isNotEmpty()

    fun deleteSelected() { selectionHandler.deleteSelected() }
    fun duplicateSelected() { selectionHandler.duplicateSelected() }
    fun undo() {
        val current = snapshot()
        historyManager.undo(current)?.let {
            restoreSnapshot(it)
            onChangeListener?.onChange()
        }
    }
    fun redo() {
        val current = snapshot()
        historyManager.redo(current)?.let {
            restoreSnapshot(it)
            onChangeListener?.onChange()
        }
    }
    fun clearAll() {
        if (strokes.isNotEmpty() || shapes.isNotEmpty()) {
            pushUndo()
            strokes.clear()
            shapes.clear()
            selectedStrokeIds.clear()
            selectedShapeIds.clear()
            onChangeListener?.onChange()
            onSelectionChangedListener?.invoke(false)
            invalidate()
        }
    }
    fun captureInitialSelectionState() { selectionHandler.captureInitialState() }
    fun commitSelectionStateChange() { selectionHandler.commitStateChange() }

    fun translateSelection(previousX: Float, previousY: Float, currentX: Float, currentY: Float) {
        selectionTransform.translateSelection(previousX, previousY, currentX, currentY)
        onSelectionChangedListener?.invoke(hasSelection())
    }

    fun resizeSelection(anchor: PointF, previousX: Float, previousY: Float, currentX: Float, currentY: Float) {
        selectionTransform.resizeSelection(anchor, previousX, previousY, currentX, currentY)
        onSelectionChangedListener?.invoke(hasSelection())
    }
    fun liveUpdateSelectedProperties(color: Int? = null, width: Float? = null, alpha: Int? = null) {
        selectionHandler.liveUpdateProperties(color, width, alpha)
    }
    fun updateSelectedProperties(color: Int? = null, width: Float? = null) {
        selectionHandler.captureInitialState()
        selectionHandler.updateProperties(color, width)
    }

    internal fun pushUndo() {
        historyManager.pushUndo(snapshot())
    }

    internal fun pushUndoSnapshot(snapshot: Pair<List<Stroke>, List<ShapeAnnotation>>) {
        historyManager.pushUndo(snapshot)
    }

    private fun notifyHistoryChanged() {
        historyManager.notifyChanged()
    }

    internal fun addActiveStroke(stroke: Stroke) { activeStroke = stroke }
    fun addStroke(stroke: Stroke) {
        pushUndo()
        strokes.add(stroke)
        onChangeListener?.onChange()
        invalidate()
    }
    fun addShape(shape: ShapeAnnotation) {
        pushUndo()
        shapes.add(shape)
        onChangeListener?.onChange()
        invalidate()
    }

    internal fun selectAtPoint(x: Float, y: Float, delegate: PageDelegate?) {
        selectionHandler.selectAtPoint(x, y, delegate)
    }
    internal fun finalizeMarqueeSelection() {
        selectionHandler.finalizeMarqueeSelection()
    }
    internal fun finalizeLassoSelection(points: List<PointF>, pageIndex: Int) {
        selectionHandler.finalizeLassoSelection(points, pageIndex)
    }

    fun getSelectionBounds(): RectF? = selectionHandler.getSelectionBounds()

    fun getSelectionHandle(x: Float, y: Float, padding: Float = 32f): HandleType {
        val bounds = getSelectionBounds() ?: return HandleType.NONE
        val zoomPadding = padding 
        
        if (isNear(x, y, bounds.right, bounds.bottom, zoomPadding)) return HandleType.RESIZE_BR
        if (isNear(x, y, bounds.left, bounds.bottom, zoomPadding)) return HandleType.RESIZE_BL
        if (isNear(x, y, bounds.right, bounds.top, zoomPadding)) return HandleType.RESIZE_TR
        if (isNear(x, y, bounds.left, bounds.top, zoomPadding)) return HandleType.RESIZE_TL
        
        return HandleType.NONE
    }

    private fun isNear(x1: Float, y1: Float, x2: Float, y2: Float, tolerance: Float): Boolean {
        return Math.abs(x1 - x2) <= tolerance && Math.abs(y1 - y2) <= tolerance
    }

    enum class HandleType { NONE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR }

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

    fun snapshot(): Pair<List<Stroke>, List<ShapeAnnotation>> {
        return Pair(
            strokes.map { it.deepCopy() },
            shapes.map { it.deepCopy() }
        )
    }

    fun restoreSnapshot(snapshot: Pair<List<Stroke>, List<ShapeAnnotation>>) {
        strokes.clear()
        strokes.addAll(snapshot.first.map { it.deepCopy() })
        shapes.clear()
        shapes.addAll(snapshot.second.map { it.deepCopy() })
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
        if (!_editingEnabled) return false

        // Check all pointers for stylus input to support palm rejection
        var stylusPointerIndex = -1
        for (i in 0 until event.pointerCount) {
            val toolType = event.getToolType(i)
            if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                stylusPointerIndex = i
                break
            }
        }

        // If no stylus is found, but we are in an editing mode that allows touch (like marquee/lasso selection),
        // we might want to allow finger input. However, the current logic seems to prefer stylus for drawing.
        // For now, let's stick to stylus-only if a stylus is available, or finger if no stylus is detected.
        // Actually, the requirement said: "If a user rests their palm (pointer 0) before touching the stylus (pointer 1), 
        // the stylus input is ignored. It must iterate through all pointers."
        
        if (stylusPointerIndex == -1) {
            return false
        }

        val x = event.getX(stylusPointerIndex)
        val y = event.getY(stylusPointerIndex)
        
        if (isPointBlocked(x, y)) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Only handle if it's the stylus pointer that went down
                if (event.actionIndex == stylusPointerIndex) {
                    touchHandler.handleDown(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                touchHandler.handleMove(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.actionIndex == stylusPointerIndex || event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    touchHandler.handleUp(x, y)
                }
            }
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
