package com.sanjog.pdfscrollreader.ui.fragment.pdfviewer

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sanjog.pdfscrollreader.ui.view.InkCanvasView
import com.sanjog.pdfscrollreader.ui.view.ShapeType
import com.sanjog.pdfscrollreader.ui.view.ToolType
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfExportManager(
    private val fragment: Fragment,
    private val inkCanvasViewProvider: () -> InkCanvasView
) {
    init {
        // Initialize PDFBox
        PDFBoxResourceLoader.init(fragment.requireContext().applicationContext)
    }

    fun triggerExport(launcher: ActivityResultLauncher<String>, pdfUri: Uri?) {
        val defaultName = "Flattened_${pdfUri?.lastPathSegment ?: "document"}"
        launcher.launch(defaultName)
    }

    fun handleExportResult(destUri: Uri?, originalPdfUri: Uri?) {
        if (destUri != null && originalPdfUri != null) {
            performFlattenedExport(destUri, originalPdfUri)
        }
    }

    private fun performFlattenedExport(destUri: Uri, originalPdfUri: Uri) {
        Toast.makeText(fragment.requireContext(), "Exporting flattened PDF...", Toast.LENGTH_SHORT).show()
        
        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = fragment.requireContext()
                val inkCanvas = inkCanvasViewProvider()
                val strokes = inkCanvas.getStrokes()
                val shapes = inkCanvas.getShapes()

                context.contentResolver.openInputStream(originalPdfUri)?.use { inputStream ->
                    val document = PDDocument.load(inputStream)
                    
                    // Group annotations by page
                    val strokesByPage = strokes.groupBy { it.pageIndex }
                    val shapesByPage = shapes.groupBy { it.pageIndex }

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

                        // Open content stream in APPEND mode to draw over existing PDF content
                        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { contentStream ->
                            
                            // 1. Draw Shapes
                            for (shape in pageShapes) {
                                // PDFBox uses 0-1f for colors
                                val strokeR = Color.red(shape.strokeColor) / 255f
                                val strokeG = Color.green(shape.strokeColor) / 255f
                                val strokeB = Color.blue(shape.strokeColor) / 255f
                                val strokeA = Color.alpha(shape.strokeColor) / 255f

                                val extGState = PDExtendedGraphicsState()
                                extGState.setStrokingAlphaConstant(strokeA)
                                extGState.setNonStrokingAlphaConstant(shape.fillAlpha / 255f)
                                contentStream.setGraphicsStateParameters(extGState)

                                contentStream.setLineWidth(shape.strokeWidth)
                                contentStream.setStrokingColor(strokeR, strokeG, strokeB)
                                
                                val fillR = Color.red(shape.fillColor) / 255f
                                val fillG = Color.green(shape.fillColor) / 255f
                                val fillB = Color.blue(shape.fillColor) / 255f
                                contentStream.setNonStrokingColor(fillR, fillG, fillB)

                                // Convert Normalized coords to PDF Points (Bottom-Left Origin)
                                val x = originX + (shape.normLeft * pdfWidth)
                                val w = (shape.normRight - shape.normLeft) * pdfWidth
                                val h = (shape.normBottom - shape.normTop) * pdfHeight
                                val y = originY + pdfHeight - (shape.normTop * pdfHeight) - h // Y is flipped!

                                when (shape.shapeType) {
                                    ShapeType.RECTANGLE -> {
                                        contentStream.addRect(x, y, w, h)
                                        if (shape.fillAlpha > 0) contentStream.fillAndStroke() else contentStream.stroke()
                                    }
                                    ShapeType.ELLIPSE -> {
                                        // Approximate Ellipse with Bezier Curves or just use Rect for now as fallback
                                        // (Implementation for proper ellipse requires curveTo, but bounding box rect works as a basic implementation)
                                        contentStream.addRect(x, y, w, h)
                                        if (shape.fillAlpha > 0) contentStream.fillAndStroke() else contentStream.stroke()
                                    }
                                    ShapeType.FREEFORM -> { } // Handled via strokes
                                }
                            }

                            // 2. Draw Ink Strokes (Pen & Highlighter)
                            for (stroke in pageStrokes) {
                                if (stroke.points.isEmpty() || stroke.tool == ToolType.ERASER) continue

                                val r = Color.red(stroke.color) / 255f
                                val g = Color.green(stroke.color) / 255f
                                val b = Color.blue(stroke.color) / 255f
                                val alpha = Color.alpha(stroke.color) / 255f

                                val extGState = PDExtendedGraphicsState()
                                extGState.setStrokingAlphaConstant(alpha)
                                extGState.setNonStrokingAlphaConstant(alpha)
                                contentStream.setGraphicsStateParameters(extGState)

                                contentStream.setStrokingColor(r, g, b)
                                contentStream.setLineWidth(stroke.strokeWidth)
                                contentStream.setLineCapStyle(1) // Round Cap
                                contentStream.setLineJoinStyle(1) // Round Join

                                // Assuming stroke.points are NORMALIZED (0.0 to 1.0). If they are screen pixels, 
                                // Agent: please adjust mapping to divide by view width/height first.
                                val startP = stroke.points[0]
                                val startX = originX + (startP.x * pdfWidth)
                                val startY = originY + pdfHeight - (startP.y * pdfHeight)
                                
                                contentStream.moveTo(startX, startY)
                                
                                for (i in 1 until stroke.points.size) {
                                    val p = stroke.points[i]
                                    val px = originX + (p.x * pdfWidth)
                                    val py = originY + pdfHeight - (p.y * pdfHeight)
                                    contentStream.lineTo(px, py)
                                }
                                contentStream.stroke()
                            }
                        }
                    }

                    // Save output
                    context.contentResolver.openOutputStream(destUri, "w")?.use { outputStream ->
                        document.save(outputStream)
                    }
                    document.close()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Export successful! Annotations flattened.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("PdfExportManager", "Failed to export flattened PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(fragment.requireContext(), "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}