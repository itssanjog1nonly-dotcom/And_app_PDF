package com.sanjog.pdfscrollreader.ui.view

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

enum class ToolType {
    PEN, HIGHLIGHTER, ERASER, RECT, ELLIPSE, SELECT_TAP, SELECT_BOX, LASSO, LASSO_FILL
}

enum class EraserMode { AREA, STROKE }

enum class DragMode { NONE, MARQUEE, MOVE, RESIZE, LASSO }

enum class ShapeType { RECTANGLE, ELLIPSE, FREEFORM }

data class Stroke(
    val id: String,
    val pageIndex: Int,
    val points: MutableList<PointF>,
    val color: Int,
    val strokeWidth: Float,
    val tool: ToolType,
    val eraserMode: EraserMode = EraserMode.AREA
) {
    @Transient private var _path: android.graphics.Path? = null
    val path: android.graphics.Path get() { if (_path == null) _path = android.graphics.Path(); return _path!! }
    @Transient private var lastProjectedBounds: RectF? = null
    @Transient var cachedScreenBounds: RectF = RectF()

    fun invalidateCache() {
        _path = null
        lastProjectedBounds = null
    }

    fun deepCopy(): Stroke {
        return Stroke(
            id = id,
            pageIndex = pageIndex,
            points = points.map { PointF(it.x, it.y) }.toMutableList(),
            color = color,
            strokeWidth = strokeWidth,
            tool = tool,
            eraserMode = eraserMode
        )
    }

    fun moveBy(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        for (p in points) {
            p.x += dx
            p.y += dy
        }
        invalidateCache()
    }

    fun scaleBy(anchor: PointF, scaleX: Float, scaleY: Float) {
        if (scaleX == 1f && scaleY == 1f) return
        for (p in points) {
            p.x = anchor.x + (p.x - anchor.x) * scaleX
            p.y = anchor.y + (p.y - anchor.y) * scaleY
        }
        invalidateCache()
    }

    fun updatePath(delegate: PageDelegate?, force: Boolean = false) {
        if (delegate != null) {
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
        path.reset()
        if (points.isEmpty()) { cachedScreenBounds.setEmpty(); return }
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        path.computeBounds(cachedScreenBounds, true)
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
    val id: String,
    val pageIndex: Int,
    var shapeType: ShapeType,
    var normLeft: Float,
    var normTop: Float,
    var normRight: Float,
    var normBottom: Float,
    var strokeColor: Int,
    var strokeWidth: Float,
    var fillColor: Int,
    var fillAlpha: Int,
    val points: List<PointF>? = null
) {
    @Transient private var _path: android.graphics.Path? = null
    val path: android.graphics.Path get() { if (_path == null) _path = android.graphics.Path(); return _path!! }
    @Transient private var lastProjectedBounds: RectF? = null

    fun invalidateCache() {
        _path = null
        lastProjectedBounds = null
    }

    fun deepCopy(): ShapeAnnotation {
        return ShapeAnnotation(
            id = id,
            pageIndex = pageIndex,
            shapeType = shapeType,
            normLeft = normLeft,
            normTop = normTop,
            normRight = normRight,
            normBottom = normBottom,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            fillColor = fillColor,
            fillAlpha = fillAlpha,
            points = points?.map { PointF(it.x, it.y) }
        )
    }

    fun moveBy(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        normLeft += dx
        normTop += dy
        normRight += dx
        normBottom += dy
        points?.forEach { pt ->
            pt.x += dx
            pt.y += dy
        }
        invalidateCache()
    }

    fun scaleBy(anchor: PointF, scaleX: Float, scaleY: Float) {
        if (scaleX == 1f && scaleY == 1f) return
        val newL = anchor.x + (normLeft - anchor.x) * scaleX
        val newT = anchor.y + (normTop - anchor.y) * scaleY
        val newR = anchor.x + (normRight - anchor.x) * scaleX
        val newB = anchor.y + (normBottom - anchor.y) * scaleY
        
        normLeft = minOf(newL, newR)
        normTop = minOf(newT, newB)
        normRight = maxOf(newL, newR)
        normBottom = maxOf(newT, newB)
        
        points?.forEach { pt ->
            pt.x = anchor.x + (pt.x - anchor.x) * scaleX
            pt.y = anchor.y + (pt.y - anchor.y) * scaleY
        }
        invalidateCache()
    }

    fun updatePath(delegate: PageDelegate?, force: Boolean = false) {
        if (delegate == null) return
        val currentBounds = delegate.getPageBounds(pageIndex)
        if (!force && lastProjectedBounds != null && currentBounds != null &&
            kotlin.math.abs(currentBounds.left - lastProjectedBounds!!.left) < 0.01f &&
            kotlin.math.abs(currentBounds.top - lastProjectedBounds!!.top) < 0.01f &&
            kotlin.math.abs(currentBounds.width() - lastProjectedBounds!!.width()) < 0.01f &&
            kotlin.math.abs(currentBounds.height() - lastProjectedBounds!!.height()) < 0.01f &&
            !path.isEmpty) return
        
        if (currentBounds != null) lastProjectedBounds = RectF(currentBounds)
        path.reset()

        if (shapeType == ShapeType.FREEFORM && points != null && points.isNotEmpty()) {
            val start = delegate.projectPoint(pageIndex, points[0].x, points[0].y)
            path.moveTo(start.x, start.y)
            for (i in 1 until points.size) {
                val p = delegate.projectPoint(pageIndex, points[i].x, points[i].y)
                path.lineTo(p.x, p.y)
            }
            path.close()
        } else {
            val topLeft = delegate.projectPoint(pageIndex, normLeft, normTop)
            val bottomRight = delegate.projectPoint(pageIndex, normRight, normBottom)
            if (topLeft.x > -999f && bottomRight.x > -999f) {
                val rect = RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
                if (shapeType == ShapeType.RECTANGLE) path.addRect(rect, android.graphics.Path.Direction.CW)
                else path.addOval(rect, android.graphics.Path.Direction.CW)
            }
        }
    }

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

fun getShapeScreenBounds(
    shape: ShapeAnnotation,
    delegate: PageDelegate?
): RectF? {
    if (delegate == null) {
        return RectF(shape.normLeft, shape.normTop, shape.normRight, shape.normBottom)
    }

    val topLeft = delegate.projectPoint(shape.pageIndex, shape.normLeft, shape.normTop)
    val bottomRight = delegate.projectPoint(shape.pageIndex, shape.normRight, shape.normBottom)
    if (topLeft.x <= -999f || topLeft.y <= -999f || bottomRight.x <= -999f || bottomRight.y <= -999f) {
        return null
    }

    return RectF(
        minOf(topLeft.x, bottomRight.x),
        minOf(topLeft.y, bottomRight.y),
        maxOf(topLeft.x, bottomRight.x),
        maxOf(topLeft.y, bottomRight.y)
    )
}

fun regionContainsAllPoints(region: android.graphics.Region, bounds: RectF): Boolean {
    return region.contains(bounds.left.toInt(), bounds.top.toInt()) &&
            region.contains(bounds.right.toInt(), bounds.top.toInt()) &&
            region.contains(bounds.left.toInt(), bounds.bottom.toInt()) &&
            region.contains(bounds.right.toInt(), bounds.bottom.toInt()) &&
            region.contains(bounds.centerX().toInt(), bounds.centerY().toInt())
}

fun normalizeOffsetForPage(
    pageIndex: Int,
    offsetX: Float,
    offsetY: Float,
    delegate: PageDelegate?
): PointF {
    val pageBounds = delegate?.getPageBounds(pageIndex)
    if (pageBounds != null && pageBounds.width() > 1f && pageBounds.height() > 1f) {
        return PointF(offsetX / pageBounds.width(), offsetY / pageBounds.height())
    }
    return PointF(0f, 0f)
}
