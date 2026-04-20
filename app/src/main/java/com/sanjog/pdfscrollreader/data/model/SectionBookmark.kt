// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/SectionBookmark.kt
package com.sanjog.pdfscrollreader.data.model

import java.util.UUID

data class SectionBookmark(
    val id: String = UUID.randomUUID().toString(),
    val pdfHash: String,
    val pageNumber: Int,
    val label: String,
    val colorHex: String,
    val scrollPositionPx: Float = 0f,
    val createdAt: Long = System.currentTimeMillis()
)
