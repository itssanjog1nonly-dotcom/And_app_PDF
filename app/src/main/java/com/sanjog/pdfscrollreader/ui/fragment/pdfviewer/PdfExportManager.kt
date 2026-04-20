package com.sanjog.pdfscrollreader.ui.fragment.pdfviewer

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sanjog.pdfscrollreader.ui.fragment.AnnotationSyncManager
import com.sanjog.pdfscrollreader.ui.fragment.AppEditablePdfViewerFragment
import com.sanjog.pdfscrollreader.ui.view.InkCanvasView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfExportManager(
    private val fragment: Fragment,
    private val annotationSync: AnnotationSyncManager,
    private val inkCanvasViewProvider: () -> InkCanvasView,
    private val pdfFragmentProvider: () -> AppEditablePdfViewerFragment?
) {
    private var pendingExportUri: Uri? = null

    fun triggerExport(launcher: ActivityResultLauncher<String>, pdfUri: Uri?) {
        val defaultName = "Annotated_${pdfUri?.lastPathSegment ?: "document"}"
        launcher.launch(defaultName)
    }

    fun handleExportResult(uri: Uri?, pdfUri: Uri?) {
        if (uri != null) performExport(uri, pdfUri)
    }

    @SuppressLint("NewApi")
    private fun performExport(destUri: Uri, pdfUri: Uri?) {
        if (pdfUri == null) return
        Toast.makeText(fragment.requireContext(), "Exporting PDF...", Toast.LENGTH_SHORT).show()
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            pdfFragmentProvider()?.let { pdfFragment ->
                annotationSync.injectAnnotationsToNative(pdfFragment, inkCanvasViewProvider())
                pendingExportUri = destUri
                pdfFragment.applyDraftEdits()
            }
        }
    }

    fun handleApplyEditsSuccess(handle: androidx.pdf.PdfWriteHandle) {
        val exportUri = pendingExportUri
        pendingExportUri = null
        if (exportUri == null) return

        fragment.viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                fragment.requireContext().contentResolver.openFileDescriptor(exportUri, "w")?.use { pfd ->
                    handle.writeTo(pfd)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(fragment.requireContext(), "Export successful!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("PdfExportManager", "Failed to write PDF", e)
            }
        }
    }

    fun handleApplyEditsFailed(throwable: Throwable) {
        pendingExportUri = null
        Toast.makeText(fragment.requireContext(), "Failed to apply edits: ${throwable.message}", Toast.LENGTH_LONG).show()
    }
}
