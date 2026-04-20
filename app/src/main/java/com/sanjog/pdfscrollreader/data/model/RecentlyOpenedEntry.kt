package com.sanjog.pdfscrollreader.data.model

data class RecentlyOpenedEntry(
    val uri: String,
    val displayName: String,
    val lastOpened: Long,
    val lastModified: Long = 0L,
    val scrollPosition: Int = 0
)
