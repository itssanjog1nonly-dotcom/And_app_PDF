// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/ScrollState.kt
package com.sanjog.pdfscrollreader.data.model

data class ScrollState(
    val isAutoScrollEnabled: Boolean,
    val scrollPositionPx: Float,
    val scrollDurationMs: Long = 30_000L,
    val isLoopEnabled: Boolean,
    val lastUserInteractionTime: Long = System.currentTimeMillis(),
    val zoomLevel: Float = 1.0f
)
