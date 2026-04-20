// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/BookmarkEntry.kt
package com.sanjog.pdfscrollreader.data.model

data class BookmarkEntry(
    val pdfPath: String,
    val pageNumber: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)
