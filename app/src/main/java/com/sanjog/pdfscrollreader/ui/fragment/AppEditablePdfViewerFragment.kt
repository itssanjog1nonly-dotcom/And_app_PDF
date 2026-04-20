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

    companion object {
        private const val ARG_DOCUMENT_URI = "document_uri"

        fun newInstance(uri: android.net.Uri): AppEditablePdfViewerFragment {
            return AppEditablePdfViewerFragment().apply {
                arguments = android.os.Bundle().apply {
                    putParcelable(ARG_DOCUMENT_URI, uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val uri = androidx.core.os.BundleCompat.getParcelable(it, ARG_DOCUMENT_URI, android.net.Uri::class.java)
            if (uri != null) {
                documentUri = uri
            }
        }
    }

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

    /**
     * Expose the protected computeVerticalScrollRange from PdfView
     */
    fun getPdfScrollRange(): Int {
        return currentPdfView?.let { view ->
            // Use reflection if we can't cast or call directly due to visibility
            try {
                val method = view.javaClass.getMethod("computeVerticalScrollRange")
                method.isAccessible = true
                method.invoke(view) as Int
            } catch (e: Exception) {
                // Fallback to protected access if the method is found but inaccessible normally
                try {
                    val method = android.view.View::class.java.getDeclaredMethod("computeVerticalScrollRange")
                    method.isAccessible = true
                    method.invoke(view) as Int
                } catch (e2: Exception) {
                    view.height
                }
            }
        } ?: 0
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
