// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/Setlist.kt
package com.sanjog.pdfscrollreader.data.model

import java.util.UUID

data class Setlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val entries: List<SetlistEntry> = emptyList(),
    val isPerformanceMode: Boolean = false
)
