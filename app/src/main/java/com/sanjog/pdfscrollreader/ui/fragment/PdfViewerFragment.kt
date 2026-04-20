package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import android.os.SystemClock
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.pdf.view.PdfView
import com.sanjog.pdfscrollreader.R
import com.sanjog.pdfscrollreader.databinding.FragmentPdfViewerBinding
import com.sanjog.pdfscrollreader.data.repository.RecentlyOpenedRepository
import android.util.SparseArray
import com.sanjog.pdfscrollreader.ui.view.*
import com.sanjog.pdfscrollreader.ui.fragment.pdfviewer.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfUri: Uri? = null
    private var androidXPdfFragment: AppEditablePdfViewerFragment? = null
    private var pdfScrollableView: View? = null 
    
    private val currentPageLocations = SparseArray<RectF>()
    private var currentZoom = 1.0f
    private var viewportChangedListener: PdfView.OnViewportChangedListener? = null
    private var gestureStateChangedListener: PdfView.OnGestureStateChangedListener? = null

    private lateinit var annotationSync: AnnotationSyncManager
    private lateinit var autoScrollManager: AutoScrollManager
    private lateinit var exportManager: PdfExportManager
    private lateinit var toolbarHelper: PdfViewerToolbarHelper

    private data class ToolSettings(var color: Int, var width: Float)

    private val toolSettings = mutableMapOf(
        ToolType.PEN to ToolSettings(Color.BLACK, 8f),
        ToolType.HIGHLIGHTER to ToolSettings(Color.parseColor("#FFFF8800"), 24f),
        ToolType.RECT to ToolSettings(Color.BLACK, 8f),
        ToolType.ELLIPSE to ToolSettings(Color.BLACK, 8f),
        ToolType.LASSO_FILL to ToolSettings(Color.BLACK, 8f)
    )

    private var lastCustomColor: Int = Color.GRAY
    enum class ViewerMode { READER, ANNOTATION }
    private var viewerMode: ViewerMode = ViewerMode.READER
    private var currentTool: ToolType? = null
    private var selectionOverlayController: AnnotationSelectionOverlayController? = null

    companion object {
        private const val ARG_URI = "pdf_uri"
        fun newInstance(uri: Uri, setlistId: String? = null, entryId: String? = null): PdfViewerFragment {
            return PdfViewerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                    putString("setlist_id", setlistId)
                    putString("entry_id", entryId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pdfUri = BundleCompat.getParcelable(it, ARG_URI, Uri::class.java)
        }
        annotationSync = AnnotationSyncManager(requireContext())
        exportManager = PdfExportManager(this, annotationSync, { binding.inkCanvasView }, { androidXPdfFragment })
    }

    private val exportPdfLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        exportManager.handleExportResult(uri, pdfUri)
    }

    fun triggerExport() {
        exportManager.triggerExport(exportPdfLauncher, pdfUri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        autoScrollManager = AutoScrollManager(requireContext(), binding, pdfUri?.toString(), null) { isPlaying ->
            updateFabIcon(isPlaying)
        }
        toolbarHelper = PdfViewerToolbarHelper(binding, 
            onToolSelected = { setActiveTool(it) },
            onUndo = { binding.inkCanvasView.undo() },
            onRedo = { binding.inkCanvasView.redo() },
            onClearAll = { binding.inkCanvasView.clearAll() },
            onDeleteSelection = { binding.inkCanvasView.deleteSelected() }
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val uri = pdfUri ?: run {
            Toast.makeText(requireContext(), "Error: No PDF file selected", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        toolbarHelper.setupToolbar()
        setupAnnotationButton()
        setupSelectionActionOverlay()
        setupAutoScrollControls()
        setupSettingsListeners()

        val fragment = AppEditablePdfViewerFragment.newInstance(uri).apply {
            listener = object : AppEditablePdfViewerFragment.Listener {
                override fun onApplyEditsSuccess(handle: androidx.pdf.PdfWriteHandle) = exportManager.handleApplyEditsSuccess(handle)
                override fun onApplyEditsFailed(throwable: Throwable) = exportManager.handleApplyEditsFailed(throwable)
                override fun onDocumentLoaded() { restoreLastPosition() }
                override fun onPdfViewCreated(pdfView: PdfView) { setupPdfView(pdfView, this@apply) }
            }
        }

        childFragmentManager.beginTransaction().replace(R.id.pdf_viewer_container, fragment).commit()

        binding.inkCanvasView.onChangeListener = object : OnChangeListener {
            override fun onChange() { annotationSync.saveAnnotations(pdfUri, binding.inkCanvasView) }
        }
        binding.inkCanvasView.onHistoryChangedListener = { canUndo, canRedo ->
            binding.btnUndo.isEnabled = canUndo
            binding.btnUndo.alpha = if (canUndo) 1.0f else 0.3f
            binding.btnRedo.isEnabled = canRedo
            binding.btnRedo.alpha = if (canRedo) 1.0f else 0.3f
        }
        binding.inkCanvasView.onHistoryChangedListener?.invoke(false, false)
        binding.inkCanvasView.onDrawingStateChangedListener = { isDrawing -> autoScrollManager.isDrawing = isDrawing }
    }

    private fun setupPdfView(pdfView: PdfView, fragment: AppEditablePdfViewerFragment) {
        androidXPdfFragment = fragment
        pdfScrollableView = pdfView 
        autoScrollManager.setFragment(fragment)
        
        viewportChangedListener = PdfView.OnViewportChangedListener { _: Int, _: Int, pageLocations: SparseArray<RectF>, zoomLevel: Float ->
            currentZoom = zoomLevel
            currentPageLocations.clear()
            for (i in 0 until pageLocations.size()) {
                currentPageLocations.put(pageLocations.keyAt(i), pageLocations.valueAt(i))
            }
            binding.inkCanvasView.setZoom(currentZoom)
            updateNoDrawRegions()
        }
        pdfView.addOnViewportChangedListener(viewportChangedListener!!)

        gestureStateChangedListener = PdfView.OnGestureStateChangedListener { newState: Int ->
            autoScrollManager.lastUserInteractionTime = SystemClock.uptimeMillis()
            autoScrollManager.isUserTouching = newState != PdfView.GESTURE_STATE_IDLE
        }
        pdfView.addOnGestureStateChangedListener(gestureStateChangedListener!!)

        setupPageDelegate()
        annotationSync.loadAnnotations(pdfUri, binding.inkCanvasView)
        binding.btnAnnotate.visibility = View.VISIBLE
    }

    private fun setupAutoScrollControls() {
        binding.fabAutoScroll.setOnClickListener { autoScrollManager.toggleAutoScroll(pdfScrollableView) }
        binding.btnTimeMinus.setOnClickListener { autoScrollManager.handleTimeMinus() }
        binding.btnTimePlus.setOnClickListener { autoScrollManager.handleTimePlus() }
        binding.tvDurationDisplay.setOnClickListener { autoScrollManager.showDurationPickerDialog {} }
    }

    private fun updateFabIcon(isPlaying: Boolean) {
        binding.fabAutoScroll.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
    }

    private fun setupPageDelegate() {
        binding.inkCanvasView.setPageDelegate(object : PageDelegate {
            override fun getVisiblePageIndices(): List<Int> = (0 until currentPageLocations.size()).map { currentPageLocations.keyAt(it) }
            override fun getPageBounds(pageIndex: Int): RectF? = currentPageLocations.get(pageIndex)
            override fun getPageIndexAtPoint(x: Float, y: Float): Int {
                for (i in 0 until currentPageLocations.size()) {
                    val pageIndex = currentPageLocations.keyAt(i)
                    if (currentPageLocations.valueAt(i).contains(x, y)) return pageIndex
                }
                return -1
            }
            override fun normalizePoint(pageIndex: Int, x: Float, y: Float): PointF {
                val bounds = currentPageLocations.get(pageIndex) ?: return PointF(x, y)
                return PointF((x - bounds.left) / bounds.width(), (y - bounds.top) / bounds.height())
            }
            override fun projectPoint(pageIndex: Int, normX: Float, normY: Float): PointF {
                val bounds = currentPageLocations.get(pageIndex) ?: return PointF(-1000f, -1000f)
                return PointF(bounds.left + normX * bounds.width(), bounds.top + normY * bounds.height())
            }
        })
    }

    private fun savePosition() {
        val uri = pdfUri ?: return
        val pdfView = pdfScrollableView as? PdfView ?: return
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val scrollY = try {
                val method = View::class.java.getDeclaredMethod("computeVerticalScrollOffset").apply { isAccessible = true }
                method.invoke(pdfView) as Int
            } catch (e: Exception) { pdfView.scrollY }
            RecentlyOpenedRepository(requireContext().applicationContext).recordVisit(uri, scrollY)
        }
    }

    private fun restoreLastPosition() {
        val uri = pdfUri ?: return
        lifecycleScope.launch {
            val position = RecentlyOpenedRepository(requireContext()).getLastPosition(uri)
            if (position > 0) (pdfScrollableView as? PdfView)?.scrollTo(0, position)
        }
    }

    private fun setupAnnotationButton() {
        binding.btnAnnotate.setOnClickListener { if (viewerMode == ViewerMode.READER) enterAnnotationMode() else exitAnnotationMode() }
    }

    private fun enterAnnotationMode() {
        viewerMode = ViewerMode.ANNOTATION
        binding.inkCanvasView.setEditingEnabled(true)
        binding.annotationToolbarContainer.visibility = View.VISIBLE
        binding.btnAnnotate.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        autoScrollManager.stopAutoScroll()
        binding.fabAutoScroll.isEnabled = false
        binding.fabAutoScroll.alpha = 0.5f
        updateNoDrawRegions()
    }

    private fun exitAnnotationMode() {
        viewerMode = ViewerMode.READER
        binding.inkCanvasView.apply { cancelCurrentGesture(); setEditingEnabled(false) }
        binding.annotationToolbarContainer.visibility = View.GONE
        binding.btnAnnotate.setImageResource(android.R.drawable.ic_menu_edit)
        binding.fabAutoScroll.apply { isEnabled = true; alpha = 1f }
        currentTool = null
        toolbarHelper.resetToolVisuals()
        updateNoDrawRegions()
        refreshSelectionActions(false)
    }

    private fun updateNoDrawRegions() {
        val regions = mutableListOf<android.graphics.Rect>()
        listOf(binding.controlsContainer, binding.annotationToolbarContainer).forEach {
            if (it.visibility == View.VISIBLE) {
                val rect = android.graphics.Rect()
                it.getGlobalVisibleRect(rect)
                regions.add(rect)
            }
        }
        binding.inkCanvasView.setNoDrawRegions(regions)
    }

    private fun setupSelectionActionOverlay() {
        selectionOverlayController = AnnotationSelectionOverlayController(binding.root as ViewGroup, binding.selectionActionOverlay, binding.annotationToolbarContainer, binding.controlsContainer)
        binding.btnSelectionDuplicate.setOnClickListener { binding.inkCanvasView.duplicateSelected() }
        binding.btnSelectionDeleteFloating.setOnClickListener { binding.inkCanvasView.deleteSelected() }
        binding.inkCanvasView.onSelectionChangedListener = { hasSelection -> refreshSelectionActions(hasSelection) }
    }

    private fun refreshSelectionActions(hasSelection: Boolean) {
        if (hasSelection) selectionOverlayController?.show(binding.inkCanvasView.getSelectionBounds()) else selectionOverlayController?.hide()
    }

    private fun setActiveTool(tool: ToolType) {
        currentTool = tool
        binding.inkCanvasView.setTool(tool)
        toolbarHelper.updateToolVisuals(tool)
        toolSettings[tool]?.let { settings ->
            if (tool == ToolType.HIGHLIGHTER) {
                binding.inkCanvasView.apply { setHighlighterColor(settings.color); setHighlighterWidth(settings.width) }
                binding.seekbarHighlighterWidth.progress = settings.width.toInt()
                binding.tvHighlighterWidthValue.setText(settings.width.toInt().toString())
            } else {
                binding.inkCanvasView.apply { setPenColor(settings.color); setPenWidth(settings.width) }
                binding.seekbarPenWidth.progress = settings.width.toInt()
                binding.tvPenWidthValue.setText(settings.width.toInt().toString())
            }
            updateColorSwatchSelection(settings.color)
        }
        
        val isShape = tool in listOf(ToolType.RECT, ToolType.ELLIPSE, ToolType.LASSO_FILL)
        binding.layoutFillAlpha.visibility = if (isShape) View.VISIBLE else View.GONE
        if (isShape) {
            binding.seekbarFillAlpha.progress = binding.inkCanvasView.shapeFillAlpha
            binding.tvFillAlphaValue.text = "${(binding.inkCanvasView.shapeFillAlpha * 100 / 255)}%"
        }
    }

    private fun setupSettingsListeners() {
        val colorButtons: Map<View, Int> = mapOf(
            binding.btnColorBlack to ContextCompat.getColor(requireContext(), R.color.ann_black),
            binding.btnColorWhite to ContextCompat.getColor(requireContext(), R.color.ann_white),
            binding.btnColorDarkCyan to ContextCompat.getColor(requireContext(), R.color.ann_dark_cyan),
            binding.btnColorOliveDark to ContextCompat.getColor(requireContext(), R.color.ann_olive_dark),
            binding.btnColorAmber to ContextCompat.getColor(requireContext(), R.color.ann_amber)
        )
        colorButtons.forEach { (btn, color) -> btn.setOnClickListener { updateCurrentToolColor(color) } }
        binding.btnColorCustom.setOnClickListener { showColorPickerDialog() }

        binding.seekbarPenWidth.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val w = p.toFloat()
                    binding.tvPenWidthValue.setText(p.toString())
                    toolSettings[ToolType.PEN]?.width = w
                    if (currentTool != ToolType.HIGHLIGHTER) { binding.inkCanvasView.setPenWidth(w); binding.inkCanvasView.updateSelectedProperties(null, w) }
                }
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        binding.seekbarHighlighterWidth.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    val w = p.toFloat()
                    binding.tvHighlighterWidthValue.setText(p.toString())
                    toolSettings[ToolType.HIGHLIGHTER]?.width = w
                    if (currentTool == ToolType.HIGHLIGHTER) { binding.inkCanvasView.setHighlighterWidth(w); binding.inkCanvasView.updateSelectedProperties(null, w) }
                }
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        binding.seekbarFillAlpha.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) {
                if (f) {
                    binding.inkCanvasView.shapeFillAlpha = p
                    binding.tvFillAlphaValue.text = "${(p * 100 / 255)}%"
                    binding.inkCanvasView.liveUpdateSelectedProperties(null, null, p)
                }
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })

        binding.tvPenWidthValue.setOnEditorActionListener { v, _, _ ->
            v.text.toString().toIntOrNull()?.let { binding.seekbarPenWidth.progress = it.coerceIn(binding.seekbarPenWidth.min, binding.seekbarPenWidth.max) }
            false
        }
        binding.tvHighlighterWidthValue.setOnEditorActionListener { v, _, _ ->
            v.text.toString().toIntOrNull()?.let { binding.seekbarHighlighterWidth.progress = it.coerceIn(binding.seekbarHighlighterWidth.min, binding.seekbarHighlighterWidth.max) }
            false
        }
    }

    private fun updateCurrentToolColor(color: Int) {
        val tool = currentTool ?: ToolType.PEN
        val settings = toolSettings[tool] ?: ToolSettings(color, 8f).also { toolSettings[tool] = it }
        settings.color = color
        if (tool == ToolType.HIGHLIGHTER) binding.inkCanvasView.setHighlighterColor(color) else binding.inkCanvasView.setPenColor(color)
        binding.inkCanvasView.updateSelectedProperties(color, null)
        updateColorSwatchSelection(color)
    }

    private fun updateColorSwatchSelection(selectedColor: Int) {
        val colorButtons: Map<View, Int> = mapOf(
            binding.btnColorBlack to ContextCompat.getColor(requireContext(), R.color.ann_black),
            binding.btnColorWhite to ContextCompat.getColor(requireContext(), R.color.ann_white),
            binding.btnColorDarkCyan to ContextCompat.getColor(requireContext(), R.color.ann_dark_cyan),
            binding.btnColorOliveDark to ContextCompat.getColor(requireContext(), R.color.ann_olive_dark),
            binding.btnColorAmber to ContextCompat.getColor(requireContext(), R.color.ann_amber)
        )
        colorButtons.forEach { (btn, color) ->
            btn.setBackgroundResource(if (color == selectedColor) R.drawable.bg_color_swatch_selected else R.drawable.bg_color_swatch_unselected)
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        }
        val isCustomSelected = !colorButtons.values.contains(selectedColor)
        binding.btnColorCustom.setBackgroundResource(if (isCustomSelected) R.drawable.bg_color_swatch_selected else R.drawable.bg_color_swatch_unselected)
        if (isCustomSelected) {
            binding.btnColorCustom.backgroundTintList = android.content.res.ColorStateList.valueOf(selectedColor)
            binding.btnColorCustom.setImageResource(0)
        } else {
            binding.btnColorCustom.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#424242"))
            binding.btnColorCustom.setImageResource(android.R.drawable.ic_menu_gallery)
        }
    }

    private fun showColorPickerDialog() {
        val colors = intArrayOf(Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE, Color.RED, Color.parseColor("#FF5722"), Color.parseColor("#FF9800"), Color.parseColor("#FFC107"), Color.YELLOW, Color.parseColor("#CDDC39"), Color.GREEN, Color.parseColor("#4CAF50"), Color.parseColor("#009688"), Color.CYAN, Color.parseColor("#00BCD4"), Color.parseColor("#03A9F4"), Color.BLUE, Color.parseColor("#3F51B5"), Color.parseColor("#673AB7"), Color.parseColor("#9C27B0"), Color.parseColor("#E91E63"), Color.MAGENTA)
        val gridLayout = android.widget.GridLayout(context).apply { columnCount = 5; setPadding(16, 16, 16, 16) }
        val dialog = AlertDialog.Builder(requireContext()).setTitle("Choose Color").setView(gridLayout).setNegativeButton("Cancel", null).create()
        colors.forEach { color ->
            gridLayout.addView(View(context).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply { width = 120; height = 120; setMargins(12, 12, 12, 12) }
                setBackgroundResource(R.drawable.bg_color_swatch_unselected)
                backgroundTintList = android.content.res.ColorStateList.valueOf(color)
                setOnClickListener { lastCustomColor = color; updateCurrentToolColor(color); dialog.dismiss() }
            })
        }
        dialog.show()
    }

    override fun onPause() { super.onPause(); savePosition(); lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) { annotationSync.saveAnnotations(pdfUri, binding.inkCanvasView) } }
    override fun onDestroyView() {
        super.onDestroyView()
        autoScrollManager.onDestroy()
        (pdfScrollableView as? PdfView)?.let { view ->
            viewportChangedListener?.let { view.removeOnViewportChangedListener(it) }
            gestureStateChangedListener?.let { view.removeOnGestureStateChangedListener(it) }
        }
        viewportChangedListener = null; gestureStateChangedListener = null; _binding = null
    }
}
