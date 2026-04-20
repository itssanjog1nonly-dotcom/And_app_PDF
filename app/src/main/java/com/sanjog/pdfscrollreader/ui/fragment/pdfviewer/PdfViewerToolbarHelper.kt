package com.sanjog.pdfscrollreader.ui.fragment.pdfviewer

import android.graphics.Color
import android.view.View
import com.sanjog.pdfscrollreader.R
import com.sanjog.pdfscrollreader.databinding.FragmentPdfViewerBinding
import com.sanjog.pdfscrollreader.ui.view.ToolType

class PdfViewerToolbarHelper(
    private val binding: FragmentPdfViewerBinding,
    private val onToolSelected: (ToolType) -> Unit,
    private val onUndo: () -> Unit,
    private val onRedo: () -> Unit,
    private val onClearAll: () -> Unit,
    private val onDeleteSelection: () -> Unit
) {
    fun setupToolbar() {
        binding.btnToolPen.setOnClickListener { onToolSelected(ToolType.PEN) }
        binding.btnToolHighlighter.setOnClickListener { onToolSelected(ToolType.HIGHLIGHTER) }
        binding.btnToolEraser.setOnClickListener { onToolSelected(ToolType.ERASER) }
        binding.btnToolRect.setOnClickListener { onToolSelected(ToolType.RECT) }
        binding.btnToolEllipse.setOnClickListener { onToolSelected(ToolType.ELLIPSE) }
        binding.btnToolSelectTap.setOnClickListener { onToolSelected(ToolType.SELECT_TAP) }
        binding.btnToolSelectMarquee.setOnClickListener { onToolSelected(ToolType.SELECT_BOX) }
        binding.btnToolSelectLasso.setOnClickListener { onToolSelected(ToolType.LASSO) }
        binding.btnToolSelectLassoFill.setOnClickListener { onToolSelected(ToolType.LASSO_FILL) }
        binding.btnUndo.setOnClickListener { onUndo() }
        binding.btnRedo.setOnClickListener { onRedo() }
        binding.btnClearAll.setOnClickListener { onClearAll() }
        binding.btnDeleteSelection.setOnClickListener { onDeleteSelection() }
    }

    fun updateToolVisuals(selectedTool: ToolType?) {
        val selectedBg = Color.parseColor("#44FFFFFF")
        val normalBg = Color.TRANSPARENT
        
        binding.btnToolPen.setBackgroundColor(if (selectedTool == ToolType.PEN) selectedBg else normalBg)
        binding.btnToolHighlighter.setBackgroundColor(if (selectedTool == ToolType.HIGHLIGHTER) selectedBg else normalBg)
        binding.btnToolEraser.setBackgroundColor(if (selectedTool == ToolType.ERASER) selectedBg else normalBg)
        binding.btnToolRect.setBackgroundColor(if (selectedTool == ToolType.RECT) selectedBg else normalBg)
        binding.btnToolEllipse.setBackgroundColor(if (selectedTool == ToolType.ELLIPSE) selectedBg else normalBg)
        binding.btnToolSelectTap.setBackgroundColor(if (selectedTool == ToolType.SELECT_TAP) selectedBg else normalBg)
        binding.btnToolSelectMarquee.setBackgroundColor(if (selectedTool == ToolType.SELECT_BOX) selectedBg else normalBg)
        binding.btnToolSelectLasso.setBackgroundColor(if (selectedTool == ToolType.LASSO) selectedBg else normalBg)
        binding.btnToolSelectLassoFill.setBackgroundColor(if (selectedTool == ToolType.LASSO_FILL) selectedBg else normalBg)
    }

    fun resetToolVisuals() {
        val normalBg = Color.TRANSPARENT
        binding.btnToolPen.setBackgroundColor(normalBg)
        binding.btnToolHighlighter.setBackgroundColor(normalBg)
        binding.btnToolEraser.setBackgroundColor(normalBg)
        binding.btnToolRect.setBackgroundColor(normalBg)
        binding.btnToolEllipse.setBackgroundColor(normalBg)
        binding.btnToolSelectTap.setBackgroundColor(normalBg)
        binding.btnToolSelectMarquee.setBackgroundColor(normalBg)
        binding.btnToolSelectLasso.setBackgroundColor(normalBg)
        binding.btnToolSelectLassoFill.setBackgroundColor(normalBg)
    }
}
