// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/Stroke.kt
package com.sanjog.pdfscrollreader.data.model

import java.util.UUID

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<Point>,
    val color: Int,
    val strokeWidth: Float,
    val toolType: ToolType,
    val timestamp: Long = System.currentTimeMillis()
) {
    data class Point(
        val x: Float,
        val y: Float,
        val pressure: Float
    )

    enum class ToolType {
        PEN, HIGHLIGHTER, ERASER,
        RECTANGLE, CIRCLE, FILLED_RECTANGLE, FILLED_CIRCLE,
        TEXT_BOX
    }
}
