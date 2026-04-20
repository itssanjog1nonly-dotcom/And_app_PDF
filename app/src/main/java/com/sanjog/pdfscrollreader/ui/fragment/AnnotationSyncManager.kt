package com.sanjog.pdfscrollreader.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.sanjog.pdfscrollreader.ui.view.InkCanvasView
import com.sanjog.pdfscrollreader.ui.view.Stroke
import com.sanjog.pdfscrollreader.ui.view.ShapeAnnotation
import com.sanjog.pdfscrollreader.ui.view.ShapeType
import com.sanjog.pdfscrollreader.ui.view.ToolType

class AnnotationSyncManager(private val context: Context) {

    fun saveAnnotations(pdfUri: Uri?, inkCanvasView: InkCanvasView) {
        val uri = pdfUri ?: return
        val uiStrokes = inkCanvasView.getStrokes()
        val uiShapes = inkCanvasView.getShapes()
        val appContext = context.applicationContext

        val strokesByPage = uiStrokes.groupBy { it.pageIndex.coerceAtLeast(0) }
            .mapValues { (_, strokes) -> strokes.map(::mapToDataStroke) }
        val shapesByPage = uiShapes.groupBy { it.pageIndex.coerceAtLeast(0) }
            .mapValues { (_, shapes) -> shapes.map(::mapToDataShape) }

        val data = com.sanjog.pdfscrollreader.data.model.AnnotationData(
            pdfPath = uri.toString(),
            annotations = strokesByPage,
            shapes = shapesByPage
        )

        val repo = com.sanjog.pdfscrollreader.data.repository.AnnotationRepository(appContext)
        repo.save(data)
        Log.d("AnnotationSync", "Saved ${uiStrokes.size} strokes and ${uiShapes.size} shapes")
    }

    fun loadAnnotations(pdfUri: Uri?, inkCanvasView: InkCanvasView) {
        val uri = pdfUri ?: return
        val repo = com.sanjog.pdfscrollreader.data.repository.AnnotationRepository(context)
        val data = repo.load(uri.toString()) ?: return
        
        val allUiStrokes = mutableListOf<Stroke>()
        data.annotations.forEach { (page, strokes) ->
            allUiStrokes.addAll(strokes.map { mapToUiStroke(it, page) })
        }
        
        val allUiShapes = data.shapes.flatMap { (page, shapes) -> 
            shapes.map { mapToUiShape(it, page) }
        }
        
        inkCanvasView.setStrokes(allUiStrokes)
        inkCanvasView.setShapes(allUiShapes)
        Log.d("AnnotationSync", "Loaded ${allUiStrokes.size} strokes and ${allUiShapes.size} shapes")
    }

    private fun mapToDataStroke(uiStroke: Stroke): com.sanjog.pdfscrollreader.data.model.Stroke {
        return com.sanjog.pdfscrollreader.data.model.Stroke(
            id = uiStroke.id,
            points = uiStroke.points.map { com.sanjog.pdfscrollreader.data.model.Stroke.Point(it.x, it.y, 1f) },
            color = uiStroke.color,
            strokeWidth = uiStroke.strokeWidth,
            toolType = when(uiStroke.tool) {
                ToolType.HIGHLIGHTER -> com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.HIGHLIGHTER
                else -> com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.PEN
            }
        )
    }

    private fun mapToUiStroke(dataStroke: com.sanjog.pdfscrollreader.data.model.Stroke, pageIndex: Int): Stroke {
        return Stroke(
            id = dataStroke.id,
            pageIndex = pageIndex,
            points = dataStroke.points.map { PointF(it.x, it.y) }.toMutableList(),
            color = dataStroke.color,
            strokeWidth = dataStroke.strokeWidth,
            tool = when(dataStroke.toolType) {
                com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.HIGHLIGHTER -> ToolType.HIGHLIGHTER
                else -> ToolType.PEN
            }
        )
    }

    private fun mapToDataShape(uiShape: ShapeAnnotation): com.sanjog.pdfscrollreader.data.model.ShapeAnnotation {
        return com.sanjog.pdfscrollreader.data.model.ShapeAnnotation(
            id = uiShape.id,
            type = when(uiShape.shapeType) {
                ShapeType.RECTANGLE -> com.sanjog.pdfscrollreader.data.model.ShapeType.RECTANGLE
                ShapeType.ELLIPSE -> com.sanjog.pdfscrollreader.data.model.ShapeType.CIRCLE
                ShapeType.FREEFORM -> com.sanjog.pdfscrollreader.data.model.ShapeType.FREEFORM
            },
            left = uiShape.normLeft,
            top = uiShape.normTop,
            right = uiShape.normRight,
            bottom = uiShape.normBottom,
            strokeColor = uiShape.strokeColor,
            strokeWidth = uiShape.strokeWidth,
            fillColor = uiShape.fillColor,
            fillAlpha = uiShape.fillAlpha,
            page = uiShape.pageIndex,
            points = uiShape.points
        )
    }

    private fun mapToUiShape(dataShape: com.sanjog.pdfscrollreader.data.model.ShapeAnnotation, pageIndex: Int): ShapeAnnotation {
        return ShapeAnnotation(
            id = dataShape.id,
            pageIndex = pageIndex,
            shapeType = when(dataShape.type) {
                com.sanjog.pdfscrollreader.data.model.ShapeType.RECTANGLE -> ShapeType.RECTANGLE
                else -> {
                    // Logic to distinguish ELLIPSE vs FREEFORM if data model is limited
                    if (dataShape.points != null && dataShape.points!!.size > 4) ShapeType.FREEFORM 
                    else ShapeType.ELLIPSE
                }
            },
            normLeft = dataShape.left,
            normTop = dataShape.top,
            normRight = dataShape.right,
            normBottom = dataShape.bottom,
            strokeColor = dataShape.strokeColor,
            strokeWidth = dataShape.strokeWidth,
            fillColor = dataShape.fillColor ?: 0,
            fillAlpha = dataShape.fillAlpha,
            points = dataShape.points
        )
    }

    @SuppressLint("RestrictedApi")
    fun injectAnnotationsToNative(fragment: AppEditablePdfViewerFragment, inkCanvas: InkCanvasView) {
        val uiStrokes = inkCanvas.getStrokes()
        val uiShapes = inkCanvas.getShapes()

        try {
            val getViewModelMethod = fragment.javaClass.methods.find { it.name == "getDocumentViewModel" }
            val viewModel = getViewModelMethod?.invoke(fragment) ?: return
            
            val editorField = viewModel.javaClass.getDeclaredField("annotationsEditor")
            editorField.isAccessible = true
            val editor = editorField.get(viewModel) ?: return
            
            val clearMethod = editor.javaClass.getMethod("clear")
            clearMethod.invoke(editor)

            uiStrokes.forEach { stroke ->
                val annotation = convertToNativeAnnotation(stroke)
                val addMethod = editor.javaClass.getMethod("addDraftAnnotation", androidx.pdf.annotation.models.PdfAnnotation::class.java)
                addMethod.invoke(editor, annotation)
            }
            
            uiShapes.forEach { shape ->
                val annotation = convertToNativeAnnotation(shape)
                val addMethod = editor.javaClass.getMethod("addDraftAnnotation", androidx.pdf.annotation.models.PdfAnnotation::class.java)
                addMethod.invoke(editor, annotation)
            }
        } catch (e: Exception) {
            Log.e("AnnotationSync", "Failed to inject annotations", e)
        }
    }

    @SuppressLint("RestrictedApi")
    private fun convertToNativeAnnotation(stroke: Stroke): androidx.pdf.annotation.models.PdfAnnotation {
        val pageIndex = stroke.pageIndex
        val color = stroke.color
        val width = stroke.strokeWidth
        
        val inputs = mutableListOf<androidx.pdf.annotation.models.PathPdfObject.PathInput>()
        if (stroke.points.isNotEmpty()) {
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(stroke.points[0].x, stroke.points[0].y, androidx.pdf.annotation.models.PathPdfObject.PathInput.MOVE_TO))
            for (i in 1 until stroke.points.size) {
                inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(stroke.points[i].x, stroke.points[i].y, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            }
        }
        
        val pathObject = androidx.pdf.annotation.models.PathPdfObject(color, width, inputs)
        val bounds = calculateBounds(stroke.points)
        
        return androidx.pdf.annotation.models.StampAnnotation(pageIndex, bounds, listOf(pathObject))
    }

    @SuppressLint("RestrictedApi")
    private fun convertToNativeAnnotation(shape: ShapeAnnotation): androidx.pdf.annotation.models.PdfAnnotation {
        val pageIndex = shape.pageIndex
        val bounds = RectF(shape.normLeft, shape.normTop, shape.normRight, shape.normBottom)
        val inputs = mutableListOf<androidx.pdf.annotation.models.PathPdfObject.PathInput>()
        
        if (shape.shapeType == ShapeType.RECTANGLE) {
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normTop, androidx.pdf.annotation.models.PathPdfObject.PathInput.MOVE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, shape.normTop, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, shape.normBottom, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normBottom, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normTop, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
        } else {
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, (shape.normTop + shape.normBottom)/2, androidx.pdf.annotation.models.PathPdfObject.PathInput.MOVE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput((shape.normLeft + shape.normRight)/2, shape.normTop, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, (shape.normTop + shape.normBottom)/2, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput((shape.normLeft + shape.normRight)/2, shape.normBottom, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, (shape.normTop + shape.normBottom)/2, androidx.pdf.annotation.models.PathPdfObject.PathInput.LINE_TO))
        }
        
        val pathObject = androidx.pdf.annotation.models.PathPdfObject(shape.strokeColor, shape.strokeWidth, inputs)
        return androidx.pdf.annotation.models.StampAnnotation(pageIndex, bounds, listOf(pathObject))
    }

    private fun calculateBounds(points: List<PointF>): RectF {
        if (points.isEmpty()) return RectF()
        var left = points[0].x
        var top = points[0].y
        var right = points[0].x
        var bottom = points[0].y
        for (i in 1 until points.size) {
            val p = points[i]
            left = minOf(left, p.x)
            top = minOf(top, p.y)
            right = maxOf(right, p.x)
            bottom = maxOf(bottom, p.y)
        }
        return RectF(left, top, right, bottom)
    }
}
