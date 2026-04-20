package com.sanjog.pdfscrollreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.sanjog.pdfscrollreader.data.model.ShapeType
import com.sanjog.pdfscrollreader.data.model.Stroke
import com.sanjog.pdfscrollreader.data.repository.AnnotationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

object PdfExportHelper {

    private const val TAG = "PdfExportHelper"
    // Render PDF at 2x scale for higher quality before drawing to the PDF page
    private const val RENDER_SCALE = 2.0f

    suspend fun exportAnnotatedPdf(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        annotationRepository: AnnotationRepository
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Since annotations are now embedded in the source PDF via saveDocument(),
            // we simply copy the source file to the destination.
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                    Log.d("EXPORT_AUDIT", "Exporting file after save: $sourceUri to $destinationUri")
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "PDF exported via native copy from $sourceUri to $destinationUri")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF via copy", e)
            return@withContext false
        }
    }

}
