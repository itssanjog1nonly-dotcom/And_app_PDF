// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/SetlistEntry.kt
package com.sanjog.pdfscrollreader.data.model

import java.util.UUID

data class SetlistEntry(
    val id: String = UUID.randomUUID().toString(),
    val pdfUri: String,
    val displayName: String,
    val lastPage: Int = 0,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val orderIndex: Int
)
