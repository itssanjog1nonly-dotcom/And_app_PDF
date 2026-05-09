package com.sanjog.pdfscrollreader.ui.fragment.pdfviewer

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.sanjog.pdfscrollreader.data.repository.AnnotationRepository
import com.sanjog.pdfscrollreader.ui.view.ShapeType
import com.sanjog.pdfscrollreader.ui.view.ToolType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Batch export manager that flattens annotations onto multiple PDFs
 * and saves them to a user-chosen directory.
 */
class BatchExportManager(private val context: Context) {

    data class ExportItem(
        val pdfUri: Uri,
        val displayName: String
    )

    data class ExportResult(
        val total: Int,
        val success: Int,
        val failed: Int
    )

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun exportAll(items: List<ExportItem>, destFolderUri: Uri): ExportResult = withContext(Dispatchers.IO) {
        val folder = DocumentFile.fromTreeUri(context, destFolderUri)
            ?: return@withContext ExportResult(items.size, 0, items.size)

        var success = 0
        var failed = 0

        for (item in items) {
            try {
                exportSingle(item, folder)
                success++
                Log.d("BatchExport", "Exported: ${item.displayName}")
            } catch (e: Exception) {
                failed++
                Log.e("BatchExport", "Failed: ${item.displayName}", e)
            }
        }

        ExportResult(items.size, success, failed)
    }

    private fun exportSingle(item: ExportItem, folder: DocumentFile) {
        val safeHash = item.pdfUri.toString().hashCode().toString()
        val repo = AnnotationRepository(context)
        val annotationData = repo.load(safeHash)

        // Create output file in destination folder
        val safeName = "Flattened_${sanitizeFileName(item.displayName)}"
        val destFile = folder.createFile("application/pdf", safeName)
            ?: throw Exception("Failed to create output file for ${item.displayName}")

        context.contentResolver.openInputStream(item.pdfUri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)

            if (annotationData != null) {
                val strokesByPage = annotationData.annotations
                val shapesByPage = annotationData.shapes

                for (pageIndex in 0 until document.numberOfPages) {
                    val pageStrokes = strokesByPage[pageIndex] ?: emptyList()
                    val pageShapes = shapesByPage[pageIndex] ?: emptyList()

                    if (pageStrokes.isEmpty() && pageShapes.isEmpty()) continue

                    val page = document.getPage(pageIndex)
                    val cropBox = page.cropBox
                    val pdfWidth = cropBox.width
                    val pdfHeight = cropBox.height
                    val originX = cropBox.lowerLeftX
                    val originY = cropBox.lowerLeftY

                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        // Draw strokes
                        for (stroke in pageStrokes) {
                            if (stroke.points.isEmpty()) continue
                            if (stroke.toolType == com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.ERASER) continue

                            val r = Color.red(stroke.color) / 255f
                            val g = Color.green(stroke.color) / 255f
                            val b = Color.blue(stroke.color) / 255f
                            var alpha = Color.alpha(stroke.color) / 255f

                            val extGState = PDExtendedGraphicsState()
                            if (stroke.toolType == com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.HIGHLIGHTER) {
                                if (alpha > 0.5f) alpha = 0.4f
                                extGState.setBlendMode(com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode.MULTIPLY)
                            } else {
                                extGState.setBlendMode(com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode.NORMAL)
                            }
                            extGState.setStrokingAlphaConstant(alpha)
                            extGState.setNonStrokingAlphaConstant(alpha)
                            cs.setGraphicsStateParameters(extGState)

                            cs.setStrokingColor(r, g, b)
                            cs.setLineWidth(stroke.strokeWidth)
                            cs.setLineCapStyle(1)
                            cs.setLineJoinStyle(1)

                            val startP = stroke.points[0]
                            cs.moveTo(originX + (startP.x * pdfWidth), originY + pdfHeight - (startP.y * pdfHeight))
                            for (i in 1 until stroke.points.size) {
                                val p = stroke.points[i]
                                cs.lineTo(originX + (p.x * pdfWidth), originY + pdfHeight - (p.y * pdfHeight))
                            }
                            cs.stroke()
                        }

                        // Draw shapes
                        for (shape in pageShapes) {
                            val strokeR = Color.red(shape.strokeColor) / 255f
                            val strokeG = Color.green(shape.strokeColor) / 255f
                            val strokeB = Color.blue(shape.strokeColor) / 255f
                            val strokeA = Color.alpha(shape.strokeColor) / 255f

                            val fillR = Color.red(shape.fillColor ?: 0) / 255f
                            val fillG = Color.green(shape.fillColor ?: 0) / 255f
                            val fillB = Color.blue(shape.fillColor ?: 0) / 255f
                            val fillA = shape.fillAlpha / 255f

                            val extGState = PDExtendedGraphicsState()
                            extGState.setStrokingAlphaConstant(strokeA)
                            extGState.setNonStrokingAlphaConstant(fillA)
                            cs.setGraphicsStateParameters(extGState)

                            val hasFill = fillA > 0f
                            var hasStroke = shape.strokeWidth > 0f && strokeA > 0f
                            val isSameColor = (strokeR == fillR && strokeG == fillG && strokeB == fillB)
                            if (hasFill && isSameColor && fillA < 1.0f) hasStroke = false

                            if (hasStroke) {
                                cs.setLineWidth(shape.strokeWidth)
                                cs.setStrokingColor(strokeR, strokeG, strokeB)
                            }
                            if (hasFill) {
                                cs.setNonStrokingColor(fillR, fillG, fillB)
                            }

                            val x = originX + (shape.left * pdfWidth)
                            val w = (shape.right - shape.left) * pdfWidth
                            val h = (shape.bottom - shape.top) * pdfHeight
                            val y = originY + pdfHeight - (shape.top * pdfHeight) - h

                            when (shape.type) {
                                com.sanjog.pdfscrollreader.data.model.ShapeType.RECTANGLE -> {
                                    cs.addRect(x, y, w, h)
                                }
                                com.sanjog.pdfscrollreader.data.model.ShapeType.CIRCLE -> {
                                    val kappa = 0.55228475f
                                    val cx = x + w / 2f; val cy = y + h / 2f
                                    val rx = w / 2f; val ry = h / 2f
                                    val ox = rx * kappa; val oy = ry * kappa
                                    cs.moveTo(cx + rx, cy)
                                    cs.curveTo(cx + rx, cy + oy, cx + ox, cy + ry, cx, cy + ry)
                                    cs.curveTo(cx - ox, cy + ry, cx - rx, cy + oy, cx - rx, cy)
                                    cs.curveTo(cx - rx, cy - oy, cx - ox, cy - ry, cx, cy - ry)
                                    cs.curveTo(cx + ox, cy - ry, cx + rx, cy - oy, cx + rx, cy)
                                }
                                com.sanjog.pdfscrollreader.data.model.ShapeType.FREEFORM -> {
                                    if (!shape.points.isNullOrEmpty()) {
                                        val sp = shape.points!![0]
                                        cs.moveTo(originX + (sp.x * pdfWidth), originY + pdfHeight - (sp.y * pdfHeight))
                                        for (i in 1 until shape.points!!.size) {
                                            val p = shape.points!![i]
                                            cs.lineTo(originX + (p.x * pdfWidth), originY + pdfHeight - (p.y * pdfHeight))
                                        }
                                        cs.closePath()
                                    }
                                }
                            }

                            if (hasFill && hasStroke) cs.fillAndStroke()
                            else if (hasFill) cs.fill()
                            else if (hasStroke) cs.stroke()
                        }
                    }
                }
            }

            context.contentResolver.openOutputStream(destFile.uri, "w")?.use { os ->
                document.save(os)
            }
            document.close()
        } ?: throw Exception("Cannot open PDF: ${item.pdfUri}")
    }

    private fun sanitizeFileName(name: String): String {
        // Remove .pdf extension if present, sanitize, then re-add
        val base = name.removeSuffix(".pdf").removeSuffix(".PDF")
        val safe = base.replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "_").take(100)
        return if (safe.isBlank()) "export" else safe
    }
}
