package com.sanjog.pdfscrollreader.data.model

data class PdfAnnotation(
    val id: String,
    val pageIndex: Int,
    val x: Float, // Relative X (0..1)
    val y: Float, // Relative Y (0..1)
    val text: String
)
