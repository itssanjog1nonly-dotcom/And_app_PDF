// FILE: app/src/main/java/com/sanjog/pdfscrollreader/data/repository/AnnotationRepository.kt
package com.sanjog.pdfscrollreader.data.repository

import android.content.Context
import com.sanjog.pdfscrollreader.data.model.AnnotationData
import com.sanjog.pdfscrollreader.data.model.ShapeAnnotation
import com.sanjog.pdfscrollreader.data.model.Stroke
import com.sanjog.pdfscrollreader.util.AnnotationSerializer

class AnnotationRepository(
    private val context: Context
) {
    fun save(data: AnnotationData) {
        AnnotationSerializer.saveToFile(context, data)
    }

    fun load(pdfHash: String): AnnotationData? {
        return AnnotationSerializer.loadFromFile(context, pdfHash)
    }

    fun delete(pdfHash: String) {
        AnnotationSerializer.deleteFile(context, pdfHash)
    }

    fun addStroke(pdfHash: String, page: Int, stroke: Stroke) {
        val existing = load(pdfHash)
        val updatedMap = existing?.annotations?.toMutableMap() ?: mutableMapOf()
        val updatedPageStrokes = updatedMap[page].orEmpty().toMutableList().apply {
            add(stroke)
        }
        updatedMap[page] = updatedPageStrokes

        val updatedData = AnnotationData(
            pdfPath = existing?.pdfPath ?: pdfHash,
            annotations = updatedMap,
            shapes = existing?.shapes ?: emptyMap(),
            lastModified = System.currentTimeMillis()
        )
        save(updatedData)
    }

    fun getStrokes(pdfHash: String, page: Int): List<Stroke> {
        return load(pdfHash)?.annotations?.get(page).orEmpty()
    }

    fun addShape(pdfHash: String, page: Int, shape: ShapeAnnotation) {
        val existing = load(pdfHash)
        val updatedMap = existing?.shapes?.toMutableMap() ?: mutableMapOf()
        val updatedPageShapes = updatedMap[page].orEmpty().toMutableList().apply {
            add(shape)
        }
        updatedMap[page] = updatedPageShapes

        val updatedData = AnnotationData(
            pdfPath = existing?.pdfPath ?: pdfHash,
            annotations = existing?.annotations ?: emptyMap(),
            shapes = updatedMap,
            lastModified = System.currentTimeMillis()
        )
        save(updatedData)
    }

    fun getShapes(pdfHash: String, page: Int): List<ShapeAnnotation> {
        return load(pdfHash)?.shapes?.get(page).orEmpty()
    }

    fun deleteShape(pdfHash: String, page: Int, shapeId: String) {
        val existing = load(pdfHash) ?: return
        val updatedMap = existing.shapes.toMutableMap()
        val pageShapes = updatedMap[page].orEmpty().filter { it.id != shapeId }
        
        if (pageShapes.isEmpty()) {
            updatedMap.remove(page)
        } else {
            updatedMap[page] = pageShapes
        }

        save(existing.copy(shapes = updatedMap, lastModified = System.currentTimeMillis()))
    }
}
