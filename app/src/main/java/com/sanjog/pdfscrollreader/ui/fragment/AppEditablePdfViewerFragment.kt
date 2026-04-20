package com.sanjog.pdfscrollreader.ui.fragment

import android.util.Log
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfWriteHandle
import androidx.pdf.ink.EditablePdfViewerFragment
import androidx.pdf.view.PdfView

/**
 * A custom subclass of EditablePdfViewerFragment to expose lifecycle callbacks
 * for saving and applying edits.
 */
class AppEditablePdfViewerFragment : EditablePdfViewerFragment() {

    interface Listener {
        fun onApplyEditsSuccess(handle: PdfWriteHandle)
        fun onApplyEditsFailed(throwable: Throwable)
        fun onDocumentLoaded()
        fun onPdfViewCreated(pdfView: PdfView)
    }

    var listener: Listener? = null
    var currentPdfView: PdfView? = null
        private set

    @OptIn(ExperimentalPdfApi::class)
    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        currentPdfView = pdfView
        listener?.onPdfViewCreated(pdfView)
    }

    override fun onApplyEditsSuccess(handle: PdfWriteHandle) {
        Log.d("AppEditablePdfFragment", "onApplyEditsSuccess")
        listener?.onApplyEditsSuccess(handle)
        super.onApplyEditsSuccess(handle)
    }

    override fun onApplyEditsFailed(throwable: Throwable) {
        Log.e("AppEditablePdfFragment", "onApplyEditsFailed", throwable)
        listener?.onApplyEditsFailed(throwable)
        super.onApplyEditsFailed(throwable)
    }

    override fun onLoadDocumentSuccess() {
        super.onLoadDocumentSuccess()
        listener?.onDocumentLoaded()
    }

    override fun onDestroyView() {
        currentPdfView = null
        super.onDestroyView()
    }
}
