package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.view.ViewTreeObserver
import android.os.ext.SdkExtensions
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.content.res.ColorStateList
import android.widget.ImageButton
import android.widget.Toast
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresExtension
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.sanjog.pdfscrollreader.R
import com.sanjog.pdfscrollreader.databinding.FragmentPdfViewerBinding
import com.sanjog.pdfscrollreader.ui.util.InputCapabilityUtils
import com.sanjog.pdfscrollreader.util.toDisplayName
import com.sanjog.pdfscrollreader.data.repository.RecentlyOpenedRepository
import android.util.SparseArray
import androidx.pdf.PdfPoint
import androidx.pdf.view.PdfView
import com.sanjog.pdfscrollreader.ui.view.InkCanvasView
import androidx.pdf.ink.EditablePdfViewerFragment as AndroidXPdfFragment

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfUri: Uri? = null
    private var durationSeconds: Int = 180
    private var androidXPdfFragment: AppEditablePdfViewerFragment? = null
    private var pdfScrollableView: View? = null 
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var isPlaying = false
    private var isUserTouching = false
    private var lastUserInteractionTime = 0L
    private val USER_SCROLL_COOLDOWN_MS = 700L
    
    private var stylusSupported: Boolean = false
    private val currentPageLocations = SparseArray<RectF>()
    private var currentZoom = 1.0f
    private var annotationUiHidden = false // Flag to avoid repeated reflection calls
    private var viewportChangedListener: PdfView.OnViewportChangedListener? = null
    private var gestureStateChangedListener: PdfView.OnGestureStateChangedListener? = null

    // Performance context
    private var setlistId: String? = null
    private var entryId: String? = null
    private lateinit var setlistRepository: com.sanjog.pdfscrollreader.data.repository.SetlistRepository
    private lateinit var bookmarkRepository: com.sanjog.pdfscrollreader.data.repository.BookmarkRepository
    private lateinit var sectionRepository: com.sanjog.pdfscrollreader.data.repository.SectionBookmarkRepository

    // Annotation tool state
    private var activePenColor: Int = Color.BLACK
    private var activeHighlighterColor: Int = Color.parseColor("#FFFF8800") // Default to translucent orange
    private var activePenWidth: Float = 8f // Default pen width in pixels
    private var activeHighlighterWidth: Float = 24f // Default highlighter width in pixels
    private var lastCustomColor: Int = Color.GRAY // To remember the last custom chosen color


    enum class ViewerMode { READER, ANNOTATION }
    private var viewerMode: ViewerMode = ViewerMode.READER

    companion object {
        private const val ARG_URI = "pdf_uri"
        private const val ARG_SETLIST_ID = "setlist_id"
        private const val ARG_ENTRY_ID = "entry_id"

        fun newInstance(uri: Uri, setlistId: String? = null, entryId: String? = null): PdfViewerFragment {
            return PdfViewerFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_URI, uri)
                    putString(ARG_SETLIST_ID, setlistId)
                    putString(ARG_ENTRY_ID, entryId)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            pdfUri = BundleCompat.getParcelable(it, ARG_URI, Uri::class.java)
            setlistId = it.getString(ARG_SETLIST_ID)
            entryId = it.getString(ARG_ENTRY_ID)
        }
        setlistRepository = com.sanjog.pdfscrollreader.data.repository.SetlistRepository(requireContext())
        bookmarkRepository = com.sanjog.pdfscrollreader.data.repository.BookmarkRepository(requireContext())
        sectionRepository = com.sanjog.pdfscrollreader.data.repository.SectionBookmarkRepository(requireContext())
    }

    private val exportPdfLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            performExport(uri)
        }
    }

    fun triggerExport() {
        val defaultName = "Annotated_${pdfUri?.lastPathSegment ?: "document"}"
        exportPdfLauncher.launch(defaultName)
    }

    private fun performExport(destUri: Uri) {
        if (pdfUri == null) return
        Toast.makeText(requireContext(), "Exporting PDF...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            injectAnnotationsToNative()
            pendingExportUri = destUri
            androidXPdfFragment?.applyDraftEdits()
        }
    }

    private var pendingExportUri: Uri? = null

    private fun handleApplyEditsSuccess(handle: androidx.pdf.PdfWriteHandle) {
        val sourceUri = pdfUri ?: return
        val exportUri = pendingExportUri
        pendingExportUri = null

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (exportUri != null) {
                    requireContext().contentResolver.openFileDescriptor(exportUri, "w")?.use { pfd ->
                        handle.writeTo(pfd)
                    }
                } else {
                    requireContext().contentResolver.openFileDescriptor(sourceUri, "rw")?.use { pfd ->
                        handle.writeTo(pfd)
                    }
                    RecentlyOpenedRepository(requireContext()).recordEdit(sourceUri)
                }

                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (exportUri != null) {
                        Toast.makeText(requireContext(), "Export successful!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(requireContext(), "Saved successfully!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewerFragment", "Failed to write PDF", e)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Failed to save PDF", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleApplyEditsFailed(throwable: Throwable) {
        pendingExportUri = null
        Log.e("PdfViewerFragment", "Failed to apply edits", throwable)
        Toast.makeText(requireContext(), "Failed to apply edits: ${throwable.message}", Toast.LENGTH_LONG).show()
    }

    private fun injectAnnotationsToNative() {
        val fragment = androidXPdfFragment ?: return
        val inkCanvas = binding.inkCanvasView
        val uiStrokes = inkCanvas.getStrokes()
        val uiShapes = inkCanvas.getShapes()

        try {
            // 1. Get ViewModel
            val getViewModelMethod = fragment.javaClass.methods.find { it.name == "getDocumentViewModel" }
            val viewModel = getViewModelMethod?.invoke(fragment) ?: return
            
            // 2. Get AnnotationsEditor
            val editorField = viewModel.javaClass.getDeclaredField("annotationsEditor")
            editorField.isAccessible = true
            val editor = editorField.get(viewModel) ?: return
            
            // 3. Clear existing drafts in editor to avoid duplicates during export sync
            val clearMethod = editor.javaClass.getMethod("clear")
            clearMethod.invoke(editor)

            // 4. Inject Strokes
            uiStrokes.forEach { stroke ->
                val annotation = convertToNativeAnnotation(stroke)
                val addMethod = editor.javaClass.getMethod("addDraftAnnotation", androidx.pdf.annotation.models.PdfAnnotation::class.java)
                addMethod.invoke(editor, annotation)
            }
            
            // 5. Inject Shapes
            uiShapes.forEach { shape ->
                val annotation = convertToNativeAnnotation(shape)
                val addMethod = editor.javaClass.getMethod("addDraftAnnotation", androidx.pdf.annotation.models.PdfAnnotation::class.java)
                addMethod.invoke(editor, annotation)
            }
            
            Log.d("NativeSave", "Injected ${uiStrokes.size} strokes and ${uiShapes.size} shapes into native editor for export")
        } catch (e: Exception) {
            Log.e("NativeSave", "Failed to inject annotations", e)
        }
    }

    private fun convertToNativeAnnotation(stroke: InkCanvasView.Stroke): androidx.pdf.annotation.models.PdfAnnotation {
        val pageIndex = stroke.pageIndex
        val color = stroke.color
        val width = stroke.strokeWidth
        
        val inputs = mutableListOf<androidx.pdf.annotation.models.PathPdfObject.PathInput>()
        if (stroke.points.isNotEmpty()) {
            // MOVE_TO = 0, LINE_TO = 1
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(stroke.points[0].x, stroke.points[0].y, 0))
            for (i in 1 until stroke.points.size) {
                inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(stroke.points[i].x, stroke.points[i].y, 1))
            }
        }
        
        val pathObject = androidx.pdf.annotation.models.PathPdfObject(color, width, inputs)
        val bounds = calculateBounds(stroke.points)
        
        return androidx.pdf.annotation.models.StampAnnotation(pageIndex, bounds, listOf(pathObject))
    }

    private fun convertToNativeAnnotation(shape: InkCanvasView.ShapeAnnotation): androidx.pdf.annotation.models.PdfAnnotation {
        val pageIndex = shape.pageIndex
        val bounds = RectF(shape.normLeft, shape.normTop, shape.normRight, shape.normBottom)
        
        // Map shape to a path or simple stamp
        // For simplicity, we can use a PathPdfObject that draws the shape
        val inputs = mutableListOf<androidx.pdf.annotation.models.PathPdfObject.PathInput>()
        
        if (shape.shapeType == InkCanvasView.ShapeType.RECTANGLE) {
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normTop, 0))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, shape.normTop, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, shape.normBottom, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normBottom, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, shape.normTop, 1))
        } else {
            // Ellipse approximation (4 points)
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, (shape.normTop + shape.normBottom)/2, 0))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput((shape.normLeft + shape.normRight)/2, shape.normTop, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normRight, (shape.normTop + shape.normBottom)/2, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput((shape.normLeft + shape.normRight)/2, shape.normBottom, 1))
            inputs.add(androidx.pdf.annotation.models.PathPdfObject.PathInput(shape.normLeft, (shape.normTop + shape.normBottom)/2, 1))
        }
        
        val pathObject = androidx.pdf.annotation.models.PathPdfObject(shape.strokeColor, shape.strokeWidth, inputs)
        return androidx.pdf.annotation.models.StampAnnotation(pageIndex, bounds, listOf(pathObject))
    }

    private fun calculateBounds(points: List<PointF>): RectF {
        if (points.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        var minX = points[0].x
        var minY = points[0].y
        var maxX = points[0].x
        var maxY = points[0].y
        for (p in points) {
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
        return RectF(minX, minY, maxX, maxY)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stylusSupported = InputCapabilityUtils.shouldEnableAnnotationUi(requireContext())
        Log.d("PdfViewerFragment", "Stylus supported: $stylusSupported")

        setupWindowInsets()
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            setupPdfFragment()
        }
        setupFab()
        setupDurationControls()
        setupAnnotationButton()
        setupAnnotationToolbar()
        setupToolbarNoDrawSync()
        setupSetlistContext()
        setupSectionMarkers()

        binding.inkCanvasView.onChangeListener = object : InkCanvasView.OnChangeListener {
            override fun onChange() {
                saveAnnotations()
            }
        }
        
        loadAnnotations()
    }

    private fun setupSetlistContext() {
        if (setlistId != null && entryId != null) {
            val setlist = setlistRepository.getById(setlistId!!)
            if (setlist != null) {
                val entries = setlist.entries.sortedBy { it.orderIndex }
                val currentIndex = entries.indexOfFirst { it.id == entryId }
                if (currentIndex >= 0) {
                    binding.tvSetlistIndex?.visibility = View.VISIBLE
                    binding.tvSetlistIndex?.text = "${currentIndex + 1} / ${entries.size}"
                }
            }
        }
    }

    private fun setupSectionMarkers() {
        binding.btnMarkers?.setOnClickListener {
            showSectionMarkersSheet()
        }
    }

    private fun showSectionMarkersSheet() {
        val sheetBinding = com.sanjog.pdfscrollreader.databinding.LayoutSectionMarkersSheetBinding.inflate(layoutInflater)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)

        val hash = pdfUri.toString() // Use URI as unique ID for now
        val markers = sectionRepository.getAll(hash)
        
        val adapter = com.sanjog.pdfscrollreader.ui.adapter.SectionMarkerAdapter(
            onItemClick = { marker ->
                scrollToPage(marker.pageNumber)
                dialog.dismiss()
            },
            onDeleteClick = { marker ->
                sectionRepository.delete(marker.id)
                // Refresh list
                val updated = sectionRepository.getAll(hash)
                (sheetBinding.rvMarkers.adapter as? com.sanjog.pdfscrollreader.ui.adapter.SectionMarkerAdapter)?.submitList(updated)
                sheetBinding.tvEmpty.visibility = if (updated.isEmpty()) View.VISIBLE else View.GONE
            }
        )

        sheetBinding.rvMarkers.layoutManager = LinearLayoutManager(requireContext())
        sheetBinding.rvMarkers.adapter = adapter
        adapter.submitList(markers)
        sheetBinding.tvEmpty.visibility = if (markers.isEmpty()) View.VISIBLE else View.GONE

        sheetBinding.btnAddMarker.setOnClickListener {
            showAddMarkerDialog(hash) {
                val updated = sectionRepository.getAll(hash)
                adapter.submitList(updated)
                sheetBinding.tvEmpty.visibility = if (updated.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        dialog.show()
    }

    private fun showAddMarkerDialog(pdfHash: String, onAdded: () -> Unit) {
        val input = EditText(requireContext()).apply {
            hint = "Section Name (e.g. Chorus)"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Add Section Marker")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val label = input.text.toString()
                if (label.isNotBlank()) {
                    val currentPage = getCurrentPageNumber()
                    val marker = com.sanjog.pdfscrollreader.data.model.SectionBookmark(
                        pdfHash = pdfHash,
                        pageNumber = currentPage,
                        label = label,
                        colorHex = "#4DF0E4"
                    )
                    sectionRepository.add(marker)
                    onAdded()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getCurrentPageNumber(): Int {
        return getPdfViewInternal()?.firstVisiblePage ?: 0
    }

    private fun getPdfViewInternal(): PdfView? {
        return androidXPdfFragment?.currentPdfView
    }

    private fun scrollToPage(page: Int) {
        try {
            val pdfView = getPdfViewInternal() ?: return
            pdfView.scrollToPosition(PdfPoint(page.coerceAtLeast(0), 0f, 0f))
        } catch (e: Exception) {
            Log.e("PdfViewerFragment", "Failed to scrollToPage", e)
        }
    }


    private fun saveCurrentPosition() {
        val page = getCurrentPageNumber()
        if (setlistId != null && entryId != null) {
            setlistRepository.updateLastPage(setlistId!!, entryId!!, page)
        } else {
            pdfUri?.let { uri ->
                bookmarkRepository.clear(uri.toString())
                val entry = com.sanjog.pdfscrollreader.data.model.BookmarkEntry(
                    pdfPath = uri.toString(),
                    pageNumber = page,
                    timestamp = System.currentTimeMillis()
                )
                bookmarkRepository.add(entry)
            }
        }
    }

    private fun restoreLastPosition() {
        val savedPage = if (setlistId != null && entryId != null) {
            val setlist = setlistRepository.getById(setlistId!!)
            setlist?.entries?.find { it.id == entryId }?.lastPage ?: 0
        } else {
            pdfUri?.let { uri ->
                bookmarkRepository.getAll(uri.toString()).firstOrNull()?.pageNumber ?: 0
            } ?: 0
        }
        
        if (savedPage > 0) {
            // Give the viewer a moment to load before scrolling
            scrollHandler.postDelayed({
                scrollToPage(savedPage)
            }, 500)
        }
    }

    private var toolbarLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var controlsLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private fun setupToolbarNoDrawSync() {
        toolbarLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            _binding?.let { b ->
                updateNoDrawRegions()
            }
        }
        
        controlsLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            _binding?.let { 
                updateNoDrawRegions() 
            }
        }

        binding.annotationToolbarContainer.viewTreeObserver.addOnGlobalLayoutListener(toolbarLayoutListener)
        binding.controlsContainer.viewTreeObserver.addOnGlobalLayoutListener(controlsLayoutListener)
    }

    private fun updateNoDrawRegions() {
        val b = _binding ?: return
        val regions = mutableListOf<android.graphics.Rect>()
        val canvasLocation = IntArray(2)
        b.inkCanvasView.getLocationOnScreen(canvasLocation)
        
        if (b.annotationToolbarContainer.visibility == View.VISIBLE) {
            val rect = android.graphics.Rect()
            if (b.annotationToolbarContainer.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                rect.offset(-canvasLocation[0], -canvasLocation[1])
                regions.add(rect)
            }
        }
        
        if (b.controlsContainer.visibility == View.VISIBLE) {
            val rect = android.graphics.Rect()
            if (b.controlsContainer.getGlobalVisibleRect(rect) && !rect.isEmpty) {
                rect.offset(-canvasLocation[0], -canvasLocation[1])
                regions.add(rect)
            }
        }
        
        b.inkCanvasView.setNoDrawRegions(regions)
    }

    private fun setupAnnotationToolbar() {
        // Color Swatches
        val colorSwatches = mapOf(
            binding.btnColorBlack to Color.BLACK,
            binding.btnColorRed to ContextCompat.getColor(requireContext(), android.R.color.holo_red_light),
            binding.btnColorBlue to ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light),
            binding.btnColorGreen to ContextCompat.getColor(requireContext(), android.R.color.holo_green_light),
            binding.btnColorWhite to Color.WHITE
        )

        val allColorButtons = listOf(
            binding.btnColorBlack,
            binding.btnColorRed,
            binding.btnColorBlue,
            binding.btnColorGreen,
            binding.btnColorWhite,
            binding.btnColorCustom
        )

        fun updateColorSwatchSelection(selectedColor: Int?) {
            allColorButtons.forEach { button ->
                val isSelected = when (button.id) {
                    R.id.btn_color_black -> selectedColor == Color.BLACK
                    R.id.btn_color_red -> selectedColor == ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                    R.id.btn_color_blue -> selectedColor == ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light)
                    R.id.btn_color_green -> selectedColor == ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                    R.id.btn_color_white -> selectedColor == Color.WHITE
                    R.id.btn_color_custom -> selectedColor == lastCustomColor && selectedColor != null && !colorSwatches.values.contains(selectedColor)
                    else -> false
                }
                button.background = ContextCompat.getDrawable(requireContext(), if (isSelected) R.drawable.bg_color_swatch_selected else R.drawable.bg_color_swatch_unselected)
                
                // Set the solid color directly to preserve the XML stroke
                val drawable = button.background.mutate() as android.graphics.drawable.GradientDrawable
                if (button.id == R.id.btn_color_custom) {
                    drawable.setColor(lastCustomColor)
                } else {
                    drawable.setColor(colorSwatches[button] ?: Color.TRANSPARENT)
                }
                button.backgroundTintList = null // Remove the tint so the border stays white
            }
        }

        // Initialize InkCanvasView with default settings from fragment's state
        binding.inkCanvasView.setPenColor(activePenColor)
        binding.inkCanvasView.setHighlighterColor(activeHighlighterColor)
        binding.inkCanvasView.setPenWidth(activePenWidth)
        binding.inkCanvasView.setHighlighterWidth(activeHighlighterWidth)

        // Tool selection buttons
        binding.btnToolPen.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.PEN)
            updateToolVisuals(InkCanvasView.ToolType.PEN)
            updateColorSwatchSelection(activePenColor)
            binding.seekbarPenWidth.progress = activePenWidth.toInt()
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }
        binding.btnToolHighlighter.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.HIGHLIGHTER)
            updateToolVisuals(InkCanvasView.ToolType.HIGHLIGHTER)
            updateColorSwatchSelection(activeHighlighterColor)
            binding.seekbarHighlighterWidth.progress = activeHighlighterWidth.toInt()
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }
        binding.btnToolEraser.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.ERASER)
            updateToolVisuals(InkCanvasView.ToolType.ERASER)
            updateColorSwatchSelection(null)
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }
        binding.btnToolEraser.setOnLongClickListener {
            val popup = androidx.appcompat.widget.PopupMenu(requireContext(), it)
            popup.menu.add("Stroke Eraser")
            popup.menu.add("Area Eraser")
            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Stroke Eraser" -> {
                        binding.inkCanvasView.setEraserMode(InkCanvasView.EraserMode.STROKE)
                        Toast.makeText(requireContext(), "Stroke Eraser", Toast.LENGTH_SHORT).show()
                    }
                    "Area Eraser" -> {
                        binding.inkCanvasView.setEraserMode(InkCanvasView.EraserMode.AREA)
                        Toast.makeText(requireContext(), "Area Eraser", Toast.LENGTH_SHORT).show()
                    }
                }
                binding.inkCanvasView.setTool(InkCanvasView.ToolType.ERASER)
                true
            }
            popup.show()
            true
        }

        // Shape tools
        binding.btnToolRect.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.RECT)
            updateToolVisuals(InkCanvasView.ToolType.RECT)
            updateColorSwatchSelection(activePenColor)
            binding.layoutFillAlpha.visibility = View.VISIBLE
            binding.seekbarFillAlpha.progress = 255 // Default to 100%
            enterAnnotationMode()
        }
        binding.btnToolEllipse.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.ELLIPSE)
            updateToolVisuals(InkCanvasView.ToolType.ELLIPSE)
            updateColorSwatchSelection(activePenColor)
            binding.layoutFillAlpha.visibility = View.VISIBLE
            binding.seekbarFillAlpha.progress = 255 // Default to 100%
            enterAnnotationMode()
        }

        binding.btnToolSelectTap.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.SELECT_TAP)
            updateToolVisuals(InkCanvasView.ToolType.SELECT_TAP)
            updateColorSwatchSelection(null)
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }

        binding.btnToolSelectMarquee.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.SELECT_BOX)
            updateToolVisuals(InkCanvasView.ToolType.SELECT_BOX)
            updateColorSwatchSelection(null)
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }

        binding.btnToolSelectLasso.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.LASSO)
            updateToolVisuals(InkCanvasView.ToolType.LASSO)
            updateColorSwatchSelection(null)
            binding.layoutFillAlpha.visibility = View.GONE
            enterAnnotationMode()
        }

        binding.btnToolSelectLassoFill.setOnClickListener {
            binding.inkCanvasView.setTool(InkCanvasView.ToolType.LASSO_FILL)
            updateToolVisuals(InkCanvasView.ToolType.LASSO_FILL)
            updateColorSwatchSelection(activePenColor)
            binding.layoutFillAlpha.visibility = View.VISIBLE
            binding.seekbarFillAlpha.progress = 255
            enterAnnotationMode()
        }

        // Fill alpha slider (0 = transparent / outline only, 255 = solid fill)
        binding.seekbarFillAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.inkCanvasView.shapeFillAlpha = progress
                val pct = (progress / 255f * 100).toInt()
                binding.tvFillAlphaValue.text = "$pct%"
                
                if (fromUser && binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.liveUpdateSelectedProperties(alpha = progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.captureInitialSelectionState()
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.commitSelectionStateChange()
                }
                saveAnnotations()
            }
        })

        binding.btnDeleteSelection.setOnClickListener {
            binding.inkCanvasView.deleteSelected()
        }

        binding.btnUndo.setOnClickListener { binding.inkCanvasView.undo() }
        binding.btnRedo.setOnClickListener { binding.inkCanvasView.redo() }
        binding.btnClearAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear All Annotations")
                .setMessage("Are you sure you want to remove all drawings?")
                .setPositiveButton("Clear") { _, _ ->
                    binding.inkCanvasView.clearAll()
                    saveAnnotations() // Save immediately after clearing
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.btnExitAnnotation.setOnClickListener { exitAnnotationMode() }


        colorSwatches.forEach { (button, color) ->
            button.setOnClickListener {
                val tool = binding.inkCanvasView.currentTool
                when {
                    tool == InkCanvasView.ToolType.PEN ||
                    tool == InkCanvasView.ToolType.RECT ||
                    tool == InkCanvasView.ToolType.ELLIPSE ||
                    tool == InkCanvasView.ToolType.LASSO_FILL -> {
                        activePenColor = color
                        binding.inkCanvasView.setPenColor(activePenColor)
                    }
                    tool == InkCanvasView.ToolType.HIGHLIGHTER -> {
                        activeHighlighterColor = color
                        binding.inkCanvasView.setHighlighterColor(activeHighlighterColor)
                    }
                    else -> {}
                }
                
                // NEW: Also update selection if active
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.updateSelectedProperties(color = color)
                }
                
                updateColorSwatchSelection(color)
                enterAnnotationMode()
            }
        }

        binding.btnColorCustom.setImageDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_gallery))
        binding.btnColorCustom.setOnClickListener {
            val initialColor = if (binding.inkCanvasView.currentTool == InkCanvasView.ToolType.PEN) activePenColor else activeHighlighterColor
            showColorPickerDialog(initialColor) { selectedColor ->
                lastCustomColor = selectedColor
                val tool = binding.inkCanvasView.currentTool
                when {
                    tool == InkCanvasView.ToolType.HIGHLIGHTER -> {
                        activeHighlighterColor = selectedColor
                        binding.inkCanvasView.setHighlighterColor(activeHighlighterColor)
                    }
                    else -> {
                        // Default to Pen color logic for Pen, Rect, Ellipse, Lasso Fill
                        activePenColor = selectedColor
                        binding.inkCanvasView.setPenColor(activePenColor)
                    }
                }

                // NEW: Also update selection if active
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.updateSelectedProperties(color = selectedColor)
                }

                updateColorSwatchSelection(selectedColor)
                enterAnnotationMode()
            }
        }

        // Width Controls
        binding.seekbarPenWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvPenWidthValue.setText(progress.toString())
                    val tool = binding.inkCanvasView.currentTool
                    if (tool == InkCanvasView.ToolType.HIGHLIGHTER) {
                        activeHighlighterWidth = progress.toFloat()
                        binding.inkCanvasView.setHighlighterWidth(activeHighlighterWidth)
                    } else {
                        activePenWidth = progress.toFloat().coerceAtLeast(2f)
                        binding.inkCanvasView.setPenWidth(activePenWidth)
                    }

                    // Live update selection
                    if (binding.inkCanvasView.hasSelection()) {
                        binding.inkCanvasView.liveUpdateSelectedProperties(width = progress.toFloat())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.captureInitialSelectionState()
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.commitSelectionStateChange()
                }
                saveAnnotations()
            }
        })

        binding.tvPenWidthValue.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                val input = v.text.toString().toIntOrNull()
                if (input != null) {
                    val coerced = input.coerceIn(2, 50)
                    binding.seekbarPenWidth.progress = coerced
                    v.text = coerced.toString()
                    activePenWidth = coerced.toFloat()
                    binding.inkCanvasView.setPenWidth(activePenWidth)
                    saveAnnotations()
                }
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        binding.seekbarHighlighterWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvHighlighterWidthValue.setText(progress.toString())
                    activeHighlighterWidth = progress.toFloat()
                    binding.inkCanvasView.setHighlighterWidth(activeHighlighterWidth)
                    
                    if (binding.inkCanvasView.hasSelection()) {
                        binding.inkCanvasView.liveUpdateSelectedProperties(width = progress.toFloat())
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.captureInitialSelectionState()
                }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (binding.inkCanvasView.hasSelection()) {
                    binding.inkCanvasView.commitSelectionStateChange()
                }
                saveAnnotations()
            }
        })

        binding.tvHighlighterWidthValue.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.action == android.view.KeyEvent.ACTION_DOWN && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER)) {
                val input = v.text.toString().toIntOrNull()
                if (input != null) {
                    val coerced = input.coerceIn(10, 80)
                    binding.seekbarHighlighterWidth.progress = coerced
                    v.text = coerced.toString()
                    activeHighlighterWidth = coerced.toFloat()
                    binding.inkCanvasView.setHighlighterWidth(activeHighlighterWidth)
                    saveAnnotations()
                }
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
                v.clearFocus()
                true
            } else false
        }

        // Initial tool and color/width state setup (after all listeners are set)
        binding.inkCanvasView.setTool(InkCanvasView.ToolType.PEN) // Set default tool
        updateColorSwatchSelection(activePenColor) // Highlight default pen color
        binding.seekbarPenWidth.progress = activePenWidth.toInt()
        binding.tvPenWidthValue.setText(activePenWidth.toInt().toString())
binding.tvHighlighterWidthValue.setText(activeHighlighterWidth.toInt().toString())
        updateToolVisuals(InkCanvasView.ToolType.PEN)

        binding.inkCanvasView.onSelectionChangedListener = { hasSelection ->
            binding.btnDeleteSelection.visibility = if (hasSelection) View.VISIBLE else View.GONE
        }
    }

    private fun saveDocumentNative(location: String = "unknown") {
        val fragment = androidXPdfFragment ?: return
        try {
            Log.d("PdfViewerFragment", "Applying draft edits from $location")
            injectAnnotationsToNative()
            pendingExportUri = null
            fragment.applyDraftEdits()
        } catch (e: Exception) {
            Log.e("PdfViewerFragment", "Failed to save document from $location", e)
        }
    }

    private fun updateToolVisuals(tool: InkCanvasView.ToolType) {
        val selectedBg = Color.parseColor("#44FFFFFF") // Light translucent highlight
        val normalBg = Color.TRANSPARENT

        binding.btnToolPen.setBackgroundColor(if (tool == InkCanvasView.ToolType.PEN) selectedBg else normalBg)
        binding.btnToolHighlighter.setBackgroundColor(if (tool == InkCanvasView.ToolType.HIGHLIGHTER) selectedBg else normalBg)
        binding.btnToolEraser.setBackgroundColor(if (tool == InkCanvasView.ToolType.ERASER) selectedBg else normalBg)
        binding.btnToolRect.setBackgroundColor(if (tool == InkCanvasView.ToolType.RECT) selectedBg else normalBg)
        binding.btnToolEllipse.setBackgroundColor(if (tool == InkCanvasView.ToolType.ELLIPSE) selectedBg else normalBg)
        binding.btnToolSelectTap.setBackgroundColor(if (tool == InkCanvasView.ToolType.SELECT_TAP) selectedBg else normalBg)
        binding.btnToolSelectMarquee.setBackgroundColor(if (tool == InkCanvasView.ToolType.SELECT_BOX) selectedBg else normalBg)
        binding.btnToolSelectLasso.setBackgroundColor(if (tool == InkCanvasView.ToolType.LASSO) selectedBg else normalBg)
        binding.btnToolSelectLassoFill.setBackgroundColor(if (tool == InkCanvasView.ToolType.LASSO_FILL) selectedBg else normalBg)

        // Yellow tint for the icon itself when active
        binding.btnToolPen.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.PEN) Color.YELLOW else Color.WHITE)
        binding.btnToolHighlighter.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.HIGHLIGHTER) Color.YELLOW else Color.WHITE)
        binding.btnToolEraser.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.ERASER) Color.YELLOW else Color.WHITE)
        binding.btnToolRect.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.RECT) Color.YELLOW else Color.WHITE)
        binding.btnToolEllipse.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.ELLIPSE) Color.YELLOW else Color.WHITE)
        binding.btnToolSelectTap.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.SELECT_TAP) Color.YELLOW else Color.WHITE)
        binding.btnToolSelectMarquee.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.SELECT_BOX) Color.YELLOW else Color.WHITE)
        binding.btnToolSelectLasso.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.LASSO) Color.YELLOW else Color.WHITE)
        binding.btnToolSelectLassoFill.imageTintList = ColorStateList.valueOf(if (tool == InkCanvasView.ToolType.LASSO_FILL) Color.YELLOW else Color.WHITE)
    }

    private fun enterAnnotationMode() {
        if (viewerMode == ViewerMode.ANNOTATION) return
        viewerMode = ViewerMode.ANNOTATION
        stopAutoScroll()
        binding.inkCanvasView.setEditingEnabled(true)
        binding.annotationToolbarContainer.visibility = View.VISIBLE
        binding.btnAnnotate.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        binding.fabAutoScroll.isEnabled = false
        binding.fabAutoScroll.alpha = 0.5f
        
        // Toolbar should always be on top of InkCanvasView due to XML order and elevations
        binding.annotationToolbarContainer.bringToFront()
        
        Log.d("ZOrder_Debug", "InkCanvasView Elevation: ${binding.inkCanvasView.elevation}, Toolbar Elevation: ${binding.annotationToolbarContainer.elevation}")

        updateNoDrawRegions()
    }

    private fun exitAnnotationMode() {
        if (viewerMode == ViewerMode.READER) return
        viewerMode = ViewerMode.READER
        binding.inkCanvasView.cancelCurrentGesture() // Cancel any pending draw/selection
        binding.inkCanvasView.setEditingEnabled(false)
        binding.annotationToolbarContainer.visibility = View.GONE
        binding.btnAnnotate.setImageResource(android.R.drawable.ic_menu_edit)
        binding.fabAutoScroll.isEnabled = true
        binding.fabAutoScroll.alpha = 1f
        updateNoDrawRegions()
        saveDocumentNative("exitAnnotationMode") // BUG 3 Fix: Save natively when exiting annotation mode
    }


    private fun showColorPickerDialog(initialColor: Int, onColorSelected: (Int) -> Unit) {
        val predefinedColors = intArrayOf(
            Color.parseColor("#F44336"), Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"), Color.parseColor("#673AB7"),
            Color.parseColor("#3F51B5"), Color.parseColor("#2196F3"), Color.parseColor("#03A9F4"), Color.parseColor("#00BCD4"),
            Color.parseColor("#009688"), Color.parseColor("#4CAF50"), Color.parseColor("#8BC34A"), Color.parseColor("#CDDC39"),
            Color.parseColor("#FFEB3B"), Color.parseColor("#FFC107"), Color.parseColor("#FF9800"), Color.parseColor("#FF5722"),
            Color.parseColor("#795548"), Color.parseColor("#9E9E9E"), Color.parseColor("#607D8B"), Color.parseColor("#000000"),
            Color.parseColor("#FFFFFF")
        )

        val gridLayout = android.widget.GridLayout(requireContext()).apply {
            columnCount = 5
            rowCount = 4
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        var dialog: androidx.appcompat.app.AlertDialog? = null

        val size = (48 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()

        for (color in predefinedColors) {
            val colorView = View(requireContext()).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = size
                    height = size
                    setMargins(margin, margin, margin, margin)
                }
                
                val shape = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(color)
                    if (color == initialColor) {
                        setStroke(6, Color.WHITE) // highlight selection
                    }
                }
                background = shape
                elevation = 4f
                
                setOnClickListener {
                    onColorSelected(color)
                    dialog?.dismiss()
                }
            }
            gridLayout.addView(colorView)
        }

        dialog = AlertDialog.Builder(requireContext())
            .setTitle("Choose Color")
            .setView(gridLayout)
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        saveAnnotations()
        if (viewerMode == ViewerMode.ANNOTATION) {
            saveDocumentNative("onPause")
        }
        saveCurrentPosition()
    }

    private fun setupAnnotationButton() {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13 && stylusSupported) {
            binding.btnAnnotate.visibility = View.VISIBLE
            binding.btnAnnotate.setOnClickListener {
                if (viewerMode == ViewerMode.READER) {
                    enterAnnotationMode()
                } else {
                    exitAnnotationMode()
                }
            }
        } else {
            binding.btnAnnotate.visibility = View.GONE
        }
        
        // Ensure InkCanvasView is visible but editing is disabled by default
        binding.inkCanvasView.visibility = View.VISIBLE
        binding.inkCanvasView.setEditingEnabled(false)
    }

    // External editor functionality removed as per Phase 3.1 requirements.
    // Use InkCanvasView for in-app annotations instead.
    private fun launchExternalEditor() {
        // DO NOT use external editors.
    }

    private fun handleTouchInteraction(event: MotionEvent) {
        lastUserInteractionTime = SystemClock.uptimeMillis()

        // Drawing is now handled internally by InkCanvasView.onTouchEvent
        // if isStylusEvent(event) is true.
        if (viewerMode == ViewerMode.ANNOTATION && InputCapabilityUtils.isStylusEvent(event)) {
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                isUserTouching = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (event.pointerCount <= 1) {
                    isUserTouching = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                isUserTouching = true
            }
        }
    }

    private fun setAnnotationUiState(visible: Boolean) {
        val fragment = androidXPdfFragment ?: return
        
        try {
            fragment.isToolboxVisible = visible
            fragment.view?.let { hideInternalEditButtons(it) }
        } catch (e: Exception) {
            Log.e("PDF_UI", "Failed to update annotation UI state", e)
        }
    }

    private fun hideInternalEditButtons(view: View) {
        val className = view.javaClass.name
        val desc = view.contentDescription?.toString() ?: ""
        
        // Identify internal FABs or pen-style buttons by class name or content description
        val isRogueButton = className.contains("FloatingActionButton") || 
            desc.contains("Edit", ignoreCase = true) || 
            desc.contains("Annotate", ignoreCase = true) ||
            desc.contains("Markup", ignoreCase = true) ||
            desc.contains("Pen", ignoreCase = true)
        
        if (isRogueButton) {
            // Safety check: Don't hide our own app-level buttons
            val isOurButton = view.id == R.id.btn_annotate || view.id == R.id.fab_auto_scroll
            
            if (!isOurButton) {
                if (view.visibility != View.GONE) {
                    view.visibility = View.GONE
                    Log.d("PDF_UI", "Forced GONE on rogue internal button: $className, Desc: '$desc'")
                }
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                hideInternalEditButtons(view.getChildAt(i))
            }
        }
    }

    private fun saveAnnotations() {
        val uri = pdfUri ?: return
        val uiStrokes = binding.inkCanvasView.getStrokes()
        val uiShapes = binding.inkCanvasView.getShapes()
        
        val strokesByPage =
            uiStrokes.groupBy { it.pageIndex.coerceAtLeast(0) }
                .mapValues { (_, strokes) -> strokes.map(::mapToDataStroke) }
        val shapesByPage =
            uiShapes.groupBy { it.pageIndex.coerceAtLeast(0) }
                .mapValues { (_, shapes) -> shapes.map(::mapToDataShape) }
        
        val data = com.sanjog.pdfscrollreader.data.model.AnnotationData(
            pdfPath = uri.toString(),
            annotations = strokesByPage,
            shapes = shapesByPage
        )
        
        val repo = com.sanjog.pdfscrollreader.data.repository.AnnotationRepository(requireContext())
        repo.save(data)
        Log.d("PdfViewerFragment", "Saved ${uiStrokes.size} strokes and ${uiShapes.size} shapes to repository")
    }

    private fun loadAnnotations() {
        val uri = pdfUri ?: return
        val repo = com.sanjog.pdfscrollreader.data.repository.AnnotationRepository(requireContext())
        val data = repo.load(uri.toString()) ?: return
        
        val allUiStrokes = mutableListOf<InkCanvasView.Stroke>()
        data.annotations.forEach { (page, strokes) ->
            allUiStrokes.addAll(strokes.map { mapToUiStroke(it, page) })
        }
        
        val allUiShapes = data.shapes.values.flatten().map { mapToUiShape(it) }
        
        binding.inkCanvasView.setStrokes(allUiStrokes)
        binding.inkCanvasView.setShapes(allUiShapes)
        Log.d("PdfViewerFragment", "Loaded ${allUiStrokes.size} strokes and ${allUiShapes.size} shapes from repository")
    }

    private fun mapToDataStroke(uiStroke: InkCanvasView.Stroke): com.sanjog.pdfscrollreader.data.model.Stroke {
        return com.sanjog.pdfscrollreader.data.model.Stroke(
            id = uiStroke.id,
            points = uiStroke.points.map { com.sanjog.pdfscrollreader.data.model.Stroke.Point(it.x, it.y, 1f) },
            color = uiStroke.color,
            strokeWidth = uiStroke.strokeWidth,
            toolType = when(uiStroke.tool) {
                InkCanvasView.ToolType.HIGHLIGHTER -> com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.HIGHLIGHTER
                else -> com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.PEN
            }
        )
    }

    private fun mapToUiStroke(dataStroke: com.sanjog.pdfscrollreader.data.model.Stroke, pageIndex: Int): InkCanvasView.Stroke {
        return InkCanvasView.Stroke(
            id = dataStroke.id,
            pageIndex = pageIndex,
            points = dataStroke.points.map { PointF(it.x, it.y) }.toMutableList(),
            color = dataStroke.color,
            strokeWidth = dataStroke.strokeWidth,
            tool = when(dataStroke.toolType) {
                com.sanjog.pdfscrollreader.data.model.Stroke.ToolType.HIGHLIGHTER -> InkCanvasView.ToolType.HIGHLIGHTER
                else -> InkCanvasView.ToolType.PEN
            }
        )
    }

    private fun mapToDataShape(uiShape: InkCanvasView.ShapeAnnotation): com.sanjog.pdfscrollreader.data.model.ShapeAnnotation {
        return com.sanjog.pdfscrollreader.data.model.ShapeAnnotation(
            id = uiShape.id,
            type = when(uiShape.shapeType) {
                InkCanvasView.ShapeType.RECTANGLE -> com.sanjog.pdfscrollreader.data.model.ShapeType.RECTANGLE
                else -> com.sanjog.pdfscrollreader.data.model.ShapeType.CIRCLE
            },
            left = uiShape.normLeft,
            top = uiShape.normTop,
            right = uiShape.normRight,
            bottom = uiShape.normBottom,
            strokeColor = uiShape.strokeColor,
            fillColor = uiShape.fillColor,
            strokeWidth = uiShape.strokeWidth,
            fillAlpha = uiShape.fillAlpha,
            page = uiShape.pageIndex
        )
    }

    private fun mapToUiShape(dataShape: com.sanjog.pdfscrollreader.data.model.ShapeAnnotation): InkCanvasView.ShapeAnnotation {
        return InkCanvasView.ShapeAnnotation(
            id = dataShape.id,
            pageIndex = dataShape.page,
            shapeType = when(dataShape.type) {
                com.sanjog.pdfscrollreader.data.model.ShapeType.RECTANGLE -> InkCanvasView.ShapeType.RECTANGLE
                else -> InkCanvasView.ShapeType.ELLIPSE
            },
            normLeft = dataShape.left,
            normTop = dataShape.top,
            normRight = dataShape.right,
            normBottom = dataShape.bottom,
            strokeColor = dataShape.strokeColor,
            strokeWidth = dataShape.strokeWidth,
            fillColor = dataShape.fillColor ?: Color.TRANSPARENT,
            fillAlpha = dataShape.fillAlpha
        )
    }

    private fun saveAnnotationsIfNeeded() {
        // Handled by onChangeListener
    }

    private fun setupWindowInsets() {
        val root = _binding?.root ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = _binding ?: return@setOnApplyWindowInsetsListener insets
            
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            
            // Position annotation toolbar below system bars (status bar/cutout)
            val topInset = maxOf(systemBars.top, displayCutout.top)
            
            b.annotationToolbarContainer.setPadding(0, topInset, 0, 0)
            Log.d("Toolbar_Debug", "Setting toolbar padding top: $topInset")
            
            // Also adjust the page indicator to stay below the toolbar
            val indicatorParams = b.tvPageIndicator.layoutParams as ViewGroup.MarginLayoutParams
            val toolbarHeightPx = (36 * resources.displayMetrics.density).toInt() // Slim height: 36dp
            val indicatorGapPx = (12 * resources.displayMetrics.density).toInt()
            indicatorParams.topMargin = topInset + toolbarHeightPx + indicatorGapPx
            b.tvPageIndicator.layoutParams = indicatorParams

            val marginBottom = resources.getDimensionPixelSize(R.dimen.control_bar_margin_bottom)
            val marginEnd = resources.getDimensionPixelSize(R.dimen.control_bar_margin_end)
            
            val params = b.controlsContainer.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = systemBars.bottom + marginBottom
            params.rightMargin = systemBars.right + marginEnd
            b.controlsContainer.layoutParams = params

            b.pdfViewerContainer.updatePadding(
                bottom = systemBars.bottom
            )

            insets
        }
        // Force an initial dispatch of insets to ensure correct positioning from the start
        root.requestApplyInsets()
    }

    private var pageDelegate: InkCanvasView.PageDelegate? = null

    private fun setupPageDelegate() {
        android.util.Log.d("INK_DEBUG", "setupPageDelegate called: androidXPdfFragment=${androidXPdfFragment != null}")
        val pdfView = getPdfViewInternal() ?: return
        android.util.Log.d("INK_DEBUG", "setupPageDelegate: pdfView=$pdfView")

        Log.d("PDF_FIX", "Setting up PageDelegate using native PdfView APIs (via reflection)")
        
        pageDelegate = object : InkCanvasView.PageDelegate {
            override fun getVisiblePageIndices(): List<Int> {
                val first = pdfView.firstVisiblePage
                val count = pdfView.visiblePagesCount
                return if (count > 0) {
                    (first until first + count).toList()
                } else {
                    emptyList()
                }
            }

            override fun getPageBounds(pageIndex: Int): RectF? {
                return currentPageLocations.get(pageIndex)
            }

            override fun getPageIndexAtPoint(x: Float, y: Float): Int {
                return pdfView.viewToPdfPoint(x, y)?.pageNum?.coerceAtLeast(0) ?: -1
            }

            override fun normalizePoint(pageIndex: Int, x: Float, y: Float): PointF {
                val pdfPoint = pdfView.viewToPdfPoint(x, y) ?: return PointF(x, y)
                return PointF(pdfPoint.x, pdfPoint.y)
            }

            override fun projectPoint(pageIndex: Int, normX: Float, normY: Float): PointF {
                return pdfView.pdfToViewPoint(PdfPoint(pageIndex, normX, normY)) ?: PointF(-1000f, -1000f)
            }
        }
        binding.inkCanvasView.setPageDelegate(pageDelegate!!)
        Log.d("InkDraw", "pageDelegate set: $pageDelegate")
        android.util.Log.d("INK_DEBUG", "setupPageDelegate complete: delegate=${pageDelegate != null}")
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private fun setupPdfFragment() {
        if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            Log.d("PdfViewerFragment", "Creating AndroidXPdfFragment (Phase 3.2 In-App Annotations)")
            
            val fragment = AppEditablePdfViewerFragment()
            androidXPdfFragment = fragment
            
            fragment.listener = object : AppEditablePdfViewerFragment.Listener {
                override fun onApplyEditsSuccess(handle: androidx.pdf.PdfWriteHandle) {
                    handleApplyEditsSuccess(handle)
                }

                override fun onApplyEditsFailed(throwable: Throwable) {
                    handleApplyEditsFailed(throwable)
                }

                override fun onDocumentLoaded() {
                    restoreLastPosition()
                    view?.post {
                        if (_binding != null && isAdded) {
                            setupPageDelegate()
                        }
                    }
                }
                override fun onPdfViewCreated(pdfView: PdfView) {
                    viewportChangedListener?.let(pdfView::removeOnViewportChangedListener)
                    gestureStateChangedListener?.let(pdfView::removeOnGestureStateChangedListener)

                    viewportChangedListener =
                        object : PdfView.OnViewportChangedListener {
                            override fun onViewportChanged(
                                firstVisiblePage: Int,
                                visiblePagesCount: Int,
                                pageLocations: SparseArray<RectF>,
                                zoomLevel: Float
                            ) {
                                currentZoom = zoomLevel
                                binding.inkCanvasView.setZoom(zoomLevel)
                                currentPageLocations.clear()
                                for (i in 0 until pageLocations.size()) {
                                    currentPageLocations.put(pageLocations.keyAt(i), RectF(pageLocations.valueAt(i)))
                                }
                                if (isPlaying) restartScrollerIfIfRunning()
                            }
                        }
                    pdfView.addOnViewportChangedListener(viewportChangedListener!!)

                    gestureStateChangedListener =
                        object : PdfView.OnGestureStateChangedListener {
                            override fun onGestureStateChanged(newState: Int) {
                                lastUserInteractionTime = SystemClock.uptimeMillis()
                                isUserTouching = newState != PdfView.GESTURE_STATE_IDLE
                            }
                        }
                    pdfView.addOnGestureStateChangedListener(gestureStateChangedListener!!)

                    setupPageIndicator(pdfView)
                    setupPageDelegate()
                    loadAnnotations()
                    setAnnotationUiState(false)

                    val fragmentView = fragment.requireView()
                    if (!annotationUiHidden) {
                        fragmentView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                if (annotationUiHidden) {
                                    fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    return
                                }
                                setAnnotationUiState(false)
                                annotationUiHidden = true
                                fragmentView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            }
                        })
                    }
                }
            }

            childFragmentManager.beginTransaction()
                .replace(R.id.pdf_viewer_container, fragment, "pdf_viewer_fragment_tag")
                .runOnCommit {
                    Log.d("PdfViewerFragment", "Fragment transaction committed")
                    pdfUri?.let {
                        Log.d("PdfViewerFragment", "Setting documentUri: $it")
                        fragment.documentUri = it
                        restoreLastPosition()
                    }
                }
                .commit()
        }
    }

    private fun safeSetToolboxVisible(visible: Boolean) {
        setAnnotationUiState(visible)
    }

    private fun findRecyclerView(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findRecyclerView(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun getPdfPageCount(pdfView: View): Int {
        if (pdfView is PdfView) {
            return pdfView.pdfDocument?.pageCount ?: 0
        }
        return try {
            val methods = listOf("getPageCount", "getPaginationModel")
            for (name in methods) {
                try {
                    val method = pdfView.javaClass.getMethod(name)
                    method.isAccessible = true
                    val result = method.invoke(pdfView)
                    if (result is Int) return result
                    if (result != null) {
                        val countMethod = result.javaClass.getMethod("getPageCount")
                        return countMethod.invoke(result) as Int
                    }
                } catch (e: Exception) {}
            }
            0
        } catch (e: Exception) { 0 }
    }

    // getTrueVerticalScrollRange was removed to rely entirely on dynamic virtual progress.

    private fun getVerticalScrollRange(view: View): Int {
        val rv = if (view is RecyclerView) view else findRecyclerView(view)
        if (rv is RecyclerView) return rv.computeVerticalScrollRange()
        return try {
            val method = View::class.java.getDeclaredMethod("computeVerticalScrollRange")
            method.isAccessible = true
            method.invoke(view) as Int
        } catch (e: Exception) {
            if (view is ViewGroup && view.childCount > 0) {
                view.getChildAt(0).height
            } else {
                view.height
            }
        }
    }

    private fun getVerticalScrollOffset(view: View): Int {
        if (view is RecyclerView) return view.computeVerticalScrollOffset()
        return try {
            val method = view.javaClass.getMethod("computeVerticalScrollOffset")
            method.isAccessible = true
            (method.invoke(view) as? Int) ?: view.scrollY
        } catch (e: Exception) {
            try {
                val method = View::class.java.getDeclaredMethod("computeVerticalScrollOffset")
                method.isAccessible = true
                (method.invoke(view) as? Int) ?: view.scrollY
            } catch (e2: Exception) {
                view.scrollY
            }
        }
    }

    private fun setupPageIndicator(scrollableView: View) {
        val binding = _binding ?: return
        
        // Target the internal RecyclerView for better metrics and scroll control
        val actualRv = findRecyclerView(scrollableView)
        pdfScrollableView = actualRv ?: scrollableView
        
        val rv = pdfScrollableView!!
        Log.d("AutoScroll_Fix", "Monitoring scroll on: ${rv.javaClass.simpleName}")
        
        binding.tvPageIndicator.visibility = View.VISIBLE

        val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            updatePageIndicator()
            // Trigger updateNoDrawRegions on scroll to ensure clipping stays aligned
            updateNoDrawRegions()
        }
        rv.viewTreeObserver.addOnScrollChangedListener(scrollListener)

        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            updatePageIndicator()
        }
        rv.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        rv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                try {
                    v.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
                    v.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                } catch (e: Exception) {
                }
            }
        })

        if (rv is RecyclerView) {
            rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updatePageIndicator()
                    // Trigger updateNoDrawRegions on scroll to ensure clipping stays aligned
                    updateNoDrawRegions()
                }
            })
        }
        
        updatePageIndicator()
    }

    private fun updatePageIndicator() {
        val binding = _binding ?: return
        val rv = pdfScrollableView ?: return
        
        // Only force re-projection when annotations are actively being displayed
        if (binding.inkCanvasView.visibility == View.VISIBLE && 
            (viewerMode == ViewerMode.ANNOTATION || binding.inkCanvasView.getStrokes().isNotEmpty() || binding.inkCanvasView.getShapes().isNotEmpty())) {
            binding.inkCanvasView.invalidate()
        }

        if (rv is PdfView) {
            val pageCount = rv.pdfDocument?.pageCount ?: 0
            if (pageCount > 0) {
                binding.tvPageIndicator.text = "${rv.firstVisiblePage + 1} / $pageCount"
                binding.tvPageIndicator.visibility = View.VISIBLE
            }
            return
        }

        if (rv.javaClass.name.contains("PdfView", ignoreCase = true)) {
            try {
                val method = rv.javaClass.getMethod("getCurrentPageIndicatorLabel")
                val label = method.invoke(rv) as? String
                if (!label.isNullOrEmpty()) {
                    binding.tvPageIndicator.text = label
                    binding.tvPageIndicator.visibility = View.VISIBLE
                    return
                }
            } catch (e: Exception) {
            }
        }

        val offset = getVerticalScrollOffset(rv)
        val range = getVerticalScrollRange(rv)
        val extent = rv.height
        
        if (range > extent) {
            val pageCount = if (rv is RecyclerView) (rv.adapter?.itemCount ?: 0) else 1
            if (pageCount > 1) {
                val progress = offset.toFloat() / (range - extent).coerceAtLeast(1)
                val currentPage = (progress * (pageCount - 1)).toInt() + 1
                binding.tvPageIndicator.text = "$currentPage / $pageCount"
                binding.tvPageIndicator.visibility = View.VISIBLE
            } else {
                val progress = (offset.toFloat() / (range - extent).coerceAtLeast(1) * 100).toInt()
                binding.tvPageIndicator.text = "$progress%"
                binding.tvPageIndicator.visibility = View.VISIBLE
            }
        }
    }

    private fun setupFab() {
        binding.fabAutoScroll.setOnClickListener {
            if (isPlaying) {
                stopAutoScroll()
            } else {
                startAutoScroll()
            }
        }
    }

    private fun startAutoScroll() {
        if (viewerMode == ViewerMode.ANNOTATION) {
            Toast.makeText(requireContext(), "Exit annotation mode before auto-scroll", Toast.LENGTH_SHORT).show()
            return
        }
        val rv = pdfScrollableView ?: return
        
        isPlaying = true
        updateFabIcon(true)

        val totalDurationMs = durationSeconds * 1000L
        Log.d("AutoScroll_Calib", "Auto-scroll started. Total duration=$totalDurationMs ms")

        scrollRunnable = object : Runnable {
            private var lastTickTime = SystemClock.uptimeMillis()
            private var virtualProgress = -1f // -1f means we need to sync with current physical offset
            private var consecutiveFalseCount = 0

            override fun run() {
                if (!isPlaying) return
                
                val rvInternal = pdfScrollableView ?: return
                val now = SystemClock.uptimeMillis()
                val tickMs = now - lastTickTime
                lastTickTime = now

                val inCooldown = now - lastUserInteractionTime < USER_SCROLL_COOLDOWN_MS
                if (isUserTouching || inCooldown) {
                    virtualProgress = -1f // De-sync, user is manually navigating
                    scrollHandler.postDelayed(this, 16)
                    return
                }

                val currentRange = getVerticalScrollRange(rvInternal)
                val currentOffset = getVerticalScrollOffset(rvInternal)
                val viewportHeight = rvInternal.height
                val maxOffset = (currentRange - viewportHeight).coerceAtLeast(1)

                // Check if we've truly reached the bottom (can't scroll down anymore)
                val canScrollDown = rvInternal.canScrollVertically(1)

                if (tickMs > 0) {
                    if (virtualProgress < 0f) {
                        virtualProgress = currentOffset.toFloat() / maxOffset
                    }

                    // Progress increment per millisecond
                    val progressPerMs = 1f / totalDurationMs
                    virtualProgress += progressPerMs * tickMs
                    // Don't clamp strictly to 1f, allow it to push slightly beyond to ensure we hit the bottom
                    
                    val effectiveProgress = virtualProgress.coerceAtMost(1.05f) // Cap at 105% to avoid infinite offset
                    val targetOffset = (effectiveProgress * maxOffset).toInt()
                    val deltaToScroll = targetOffset - currentOffset

                    if (deltaToScroll > 0) {
                        rvInternal.scrollBy(0, deltaToScroll)
                    }

                    // Only stop if we reached our time AND we truly cannot scroll down anymore.
                    // Also check if we're on the last page to avoid stopping during dynamic page loads.
                    val pageCount = getPdfPageCount(rvInternal)
                    val isLastPage = (pageCount <= 1) || (getCurrentPageNumber() >= pageCount - 1)
                    
                    if (!canScrollDown) {
                        consecutiveFalseCount++
                    } else {
                        consecutiveFalseCount = 0
                    }
                    
                    if (consecutiveFalseCount >= 3 && virtualProgress >= 0.98f && isLastPage) {
                        // Allow a small over-scroll at the end to hit absolute bottom
                        rvInternal.scrollBy(0, 999)
                        stopAutoScroll()
                        return
                    }

                    // If virtualProgress hit 1.0 but we CAN still scroll, keep pushing
                    if (virtualProgress >= 1f && canScrollDown) {
                        // Force scroll by a small amount to reach true bottom
                        rvInternal.scrollBy(0, 16)
                    }
                }
                
                scrollHandler.postDelayed(this, 16)
            }
        }
        scrollHandler.post(scrollRunnable!!)
    }

    private fun stopAutoScroll() {
        isPlaying = false
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
        updateFabIcon(false)
        Log.d("PdfViewerFragment", "Auto-scroll stopped")
    }

    private fun setupDurationControls() {
        val prefs = requireContext().getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        val uriKey = pdfUri?.toString() ?: ""
        if (uriKey.isNotEmpty()) {
            durationSeconds = prefs.getInt("${uriKey}_duration", 180)
        }

        updateDurationDisplay()

        binding.btnTimeMinus.setOnClickListener {
            durationSeconds = (durationSeconds - 5).coerceAtLeast(10)
            saveDurationSetting()
            updateDurationDisplay()
            restartScrollerIfIfRunning()
        }

        binding.btnTimePlus.setOnClickListener {
            durationSeconds = (durationSeconds + 5).coerceAtMost(3600)
            saveDurationSetting()
            updateDurationDisplay()
            restartScrollerIfIfRunning()
        }

        binding.tvDurationDisplay.setOnClickListener {
            showDurationPickerDialog()
        }
    }

    private fun saveDurationSetting() {
        val uriKey = pdfUri?.toString() ?: return
        if (uriKey.isEmpty()) return
        val prefs = requireContext().getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("${uriKey}_duration", durationSeconds).apply()
    }

    private fun updateDurationDisplay() {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        binding.tvDurationDisplay.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun restartScrollerIfIfRunning() {
        if (isPlaying) {
            stopAutoScroll()
            startAutoScroll()
        }
    }

    private fun showDurationPickerDialog() {
        val dialogLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        val minutesPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds / 60
            wrapSelectorWheel = false
        }

        val colonLabel = TextView(requireContext()).apply {
            text = " : "
            textSize = 24f
        }

        val secondsPicker = NumberPicker(requireContext()).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds % 60
            wrapSelectorWheel = true
        }

        dialogLayout.addView(minutesPicker)
        dialogLayout.addView(colonLabel)
        dialogLayout.addView(secondsPicker)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Song Duration")
            .setView(dialogLayout)
            .setPositiveButton("Set") { _, _ ->
                val mins = minutesPicker.value
                val secs = secondsPicker.value
                durationSeconds = (mins * 60 + secs).coerceAtLeast(10)
                saveDurationSetting()
                updateDurationDisplay()
                restartScrollerIfIfRunning()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateFabIcon(isPlaying: Boolean) {
        binding.fabAutoScroll.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }



    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoScroll()

        getPdfViewInternal()?.let { pdfView ->
            viewportChangedListener?.let(pdfView::removeOnViewportChangedListener)
            gestureStateChangedListener?.let(pdfView::removeOnGestureStateChangedListener)
        }
        viewportChangedListener = null
        gestureStateChangedListener = null
        
        _binding?.let { b ->
            b.inkCanvasView.cancelCurrentGesture() // Ensure no leak or crash on teardown
            toolbarLayoutListener?.let { b.annotationToolbarContainer.viewTreeObserver.removeOnGlobalLayoutListener(it) }
            controlsLayoutListener?.let { b.controlsContainer.viewTreeObserver.removeOnGlobalLayoutListener(it) }
        }
        toolbarLayoutListener = null
        controlsLayoutListener = null

        _binding = null
    }
}
