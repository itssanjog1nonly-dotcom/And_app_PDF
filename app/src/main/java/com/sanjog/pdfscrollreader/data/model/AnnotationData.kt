// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/model/AnnotationData.kt
package com.sanjog.pdfscrollreader.data.model

data class AnnotationData(
    val pdfPath: String,
    val annotations: Map<Int, List<Stroke>>,
    val shapes: Map<Int, List<ShapeAnnotation>> = emptyMap(),
    val lastModified: Long = System.currentTimeMillis()
)
