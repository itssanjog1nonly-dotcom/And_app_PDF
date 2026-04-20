package com.sanjog.pdfscrollreader.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.sanjog.pdfscrollreader.data.model.ShapeAnnotation
import com.sanjog.pdfscrollreader.data.model.Stroke

class AnnotationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var strokes: List<Stroke> = emptyList()
    private var shapes: List<ShapeAnnotation> = emptyList()

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val drawPath = Path()

    fun setAnnotations(strokes: List<Stroke>, shapes: List<ShapeAnnotation>) {
        this.strokes = strokes
        this.shapes = shapes
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Draw Freehand Strokes
        strokes.forEach { stroke ->
            strokePaint.color = stroke.color
            strokePaint.strokeWidth = stroke.strokeWidth
            
            drawPath.reset()
            stroke.points.forEachIndexed { index, point ->
                if (index == 0) {
                    drawPath.moveTo(point.x * w, point.y * h)
                } else {
                    drawPath.lineTo(point.x * w, point.y * h)
                }
            }
            canvas.drawPath(drawPath, strokePaint)
        }

        // Draw Shapes
        shapes.forEach { shape ->
            val left = shape.left * w
            val top = shape.top * h
            val right = shape.right * w
            val bottom = shape.bottom * h

            shape.fillColor?.let {
                fillPaint.color = it
                fillPaint.alpha = shape.fillAlpha
                canvas.drawRect(left, top, right, bottom, fillPaint)
            }

            strokePaint.color = shape.strokeColor
            strokePaint.strokeWidth = shape.strokeWidth
            canvas.drawRect(left, top, right, bottom, strokePaint)
        }
    }
}
