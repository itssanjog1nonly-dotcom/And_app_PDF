// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/ShapeAnnotation.kt
package com.sanjog.pdfscrollreader.data.model

import android.graphics.PointF
import java.util.UUID

data class ShapeAnnotation(
    val id: String = UUID.randomUUID().toString(),
    val type: ShapeType,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val strokeColor: Int,
    val fillColor: Int?,
    val strokeWidth: Float,
    val fillAlpha: Int = 128,
    val page: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val points: List<PointF>? = null
)

enum class ShapeType { RECTANGLE, CIRCLE, FREEFORM }
