// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/RecentFile.kt
package com.sanjog.pdfscrollreader.data.model

data class RecentFile(
    val pdfPath: String,
    val displayName: String,
    val thumbnail: ByteArray? = null,
    val lastOpened: Long = System.currentTimeMillis()
)
