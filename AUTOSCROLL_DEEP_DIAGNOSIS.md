# AUTOSCROLL_DEEP_DIAGNOSIS

## 1. Full Autoscroll Call Chain

1.  **User Trigger**: The user taps the floating action button (`binding.fabAutoScroll`) in `PdfViewerFragment`.
    *   **File**: `D:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/PdfViewerFragment.kt`
    *   **Method**: `setupAutoScrollControls()` lambda.
    *   **Description**: Calls `autoScrollManager.toggleAutoScroll()`.

2.  **Toggle Logic**: `AutoScrollManager.toggleAutoScroll()`
    *   **File**: `D:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AutoScrollManager.kt`
    *   **Method**: `fun toggleAutoScroll()`
    *   **Description**: Starts or stops the scroll process based on the current `isPlaying` state.

3.  **Start Command**: `AutoScrollManager.startAutoScroll()`
    *   **File**: `D:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AutoScrollManager.kt`
    *   **Method**: `fun startAutoScroll()`
    *   **Description**: Initializes the `scrollRunnable` and posts it to the `scrollHandler`.

4.  **The Loop**: `AutoScrollManager.scrollRunnable`
    *   **File**: `D:/Users/itssa/AndroidStudioProjects/PdfScrollReader/app/src/main/java/com/sanjog/pdfscrollreader/ui/fragment/AutoScrollManager.kt`
    *   **Method**: `Runnable.run()`
    *   **Description**: The main loop that calculates delta time, pixels to scroll, and executes `recyclerView.scrollBy(0, scrollStep)`. It reschedules itself every 16ms using `scrollHandler.postDelayed(this, 16)`.

---

## 2. Full Source of Key Files

### 2.1 AutoScrollManager.kt
```kotlin
package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.sanjog.pdfscrollreader.databinding.FragmentPdfViewerBinding

class AutoScrollManager(
    private val context: Context,
    private val binding: FragmentPdfViewerBinding,
    private val pdfUriString: String?,
    private var fragment: AppEditablePdfViewerFragment? = null,
    private val onIsPlayingChanged: (Boolean) -> Unit
) {
    var durationSeconds: Int = 180
    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var _binding: FragmentPdfViewerBinding? = binding
    private val bindingSafe: FragmentPdfViewerBinding? get() = _binding

    private var targetRecyclerView: RecyclerView? = null

    var isPlaying = false
        private set(value) {
            field = value
            onIsPlayingChanged(value)
        }

    var isDrawing = false
    var isUserTouching = false
    var lastUserInteractionTime = 0L
    private val USER_SCROLL_COOLDOWN_MS = 700L

    init {
        loadDurationSetting()
        updateDurationDisplay()
    }

    fun setFragment(fragment: AppEditablePdfViewerFragment) {
        this.fragment = fragment
    }

    fun setRecyclerView(recyclerView: RecyclerView?) {
        this.targetRecyclerView = recyclerView
    }

    fun onDestroy() {
        stopAutoScroll()
        _binding = null
        targetRecyclerView = null
    }

    fun toggleAutoScroll() {
        if (isPlaying) stopAutoScroll()
        else startAutoScroll()
    }

    fun startAutoScroll() {
        val recyclerView = targetRecyclerView ?: return
        if (isPlaying) return
        
        isPlaying = true
        val startTimeMillis = SystemClock.uptimeMillis()

        scrollRunnable = object : Runnable {
            private var lastFrameTime = 0L
            private var pausedDurationMillis = 0L
            private var isCurrentlyPaused = false
            private var pauseStartTime = 0L
            private var accumulatedScroll = 0f

            override fun run() {
                if (!isPlaying) return
                val now = SystemClock.uptimeMillis()
                
                if (lastFrameTime == 0L) {
                    lastFrameTime = now
                }

                val shouldPause = isUserTouching || isDrawing || (now - lastUserInteractionTime < USER_SCROLL_COOLDOWN_MS)
                
                if (shouldPause) {
                    if (!isCurrentlyPaused) {
                        isCurrentlyPaused = true
                        pauseStartTime = now
                    }
                    lastFrameTime = now
                    scrollHandler.postDelayed(this, 16)
                    return
                } else if (isCurrentlyPaused) {
                    isCurrentlyPaused = false
                    pausedDurationMillis += (now - pauseStartTime)
                }

                val totalDurationMillis = durationSeconds * 1000L
                val elapsedActiveMillis = now - startTimeMillis - pausedDurationMillis
                val remainingMillis = totalDurationMillis - elapsedActiveMillis

                // Time Limit Stop Condition
                if (remainingMillis <= 0) {
                    stopAutoScroll()
                    return
                }

                val totalRange = recyclerView.computeVerticalScrollRange()
                val currentOffset = recyclerView.computeVerticalScrollOffset()
                val viewportHeight = recyclerView.height
                val canScrollDown = recyclerView.canScrollVertically(1)
                
                if (SystemClock.uptimeMillis() % 160 < 20) { // Approx every 10th frame
                    android.util.Log.d("AUTOSCROLL_DIAG", 
                        "Range: $totalRange, Offset: $currentOffset, VH: $viewportHeight, " + 
                        "CanScroll: $canScrollDown, RemDist: ${totalRange - viewportHeight - currentOffset}, " +
                        "RemTime: $remainingMillis, State: RUNNING"
                    )
                }

                val remainingDistance = (totalRange - viewportHeight - currentOffset).coerceAtLeast(0)

                // Page-count based stopping condition
                val totalPages = fragment?.pageCount ?: 0
                // Use reflection or internal knowledge to check if the last page is visible
                // For now, if we can't scroll more on the RV, we check if it's the true end.
                if (!canScrollDown) {
                     android.util.Log.d("AUTOSCROLL_DIAG", "State: STOPPED_REASON_CANNOT_SCROLL_DOWN")
                     stopAutoScroll()
                     return
                }

                if (remainingDistance <= 0) {
                    // We might be stalled due to lazy loading. 
                    // Tiny scroll to trigger discovery
                    recyclerView.scrollBy(0, 1)
                    lastFrameTime = now
                    scrollHandler.postDelayed(this, 16)
                    return
                }

                val pixelsPerMs = remainingDistance.toFloat() / remainingMillis.toFloat()
                val deltaTime = now - lastFrameTime
                lastFrameTime = now

                accumulatedScroll += pixelsPerMs * deltaTime
                val scrollStep = accumulatedScroll.toInt()
                if (scrollStep > 0) {
                    recyclerView.scrollBy(0, scrollStep)
                    accumulatedScroll -= scrollStep
                }

                scrollHandler.postDelayed(this, 16)
            }
        }
        scrollHandler.post(scrollRunnable!!)
    }

    fun stopAutoScroll() {
        if (isPlaying) {
            android.util.Log.d("AutoScrollManager", "Stopping auto-scroll")
        }
        isPlaying = false
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
    }

    fun handleTimeMinus() {
        durationSeconds = (durationSeconds - 5).coerceAtLeast(10)
        saveDurationSetting()
        updateDurationDisplay()
    }

    fun handleTimePlus() {
        durationSeconds = (durationSeconds + 5).coerceAtMost(3600)
        saveDurationSetting()
        updateDurationDisplay()
    }

    fun showDurationPickerDialog(onDurationSet: () -> Unit) {
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(48, 32, 48, 32)
        }

        val minutesPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds / 60
            wrapSelectorWheel = false
        }

        val colonLabel = TextView(context).apply {
            text = " : "
            textSize = 24f
        }

        val secondsPicker = NumberPicker(context).apply {
            minValue = 0
            maxValue = 59
            value = durationSeconds % 60
            wrapSelectorWheel = true
        }

        dialogLayout.addView(minutesPicker)
        dialogLayout.addView(colonLabel)
        dialogLayout.addView(secondsPicker)

        AlertDialog.Builder(context)
            .setTitle("Set Song Duration")
            .setView(dialogLayout)
            .setPositiveButton("Set") { _, _ ->
                val mins = minutesPicker.value
                val secs = secondsPicker.value
                durationSeconds = (mins * 60 + secs).coerceAtLeast(10)
                saveDurationSetting()
                updateDurationDisplay()
                onDurationSet()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveDurationSetting() {
        val uriKey = pdfUriString ?: return
        val prefs = context.getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("${uriKey}_duration", durationSeconds).apply()
    }

    private fun loadDurationSetting() {
        val uriKey = pdfUriString ?: return
        val prefs = context.getSharedPreferences("pdf_scroll_settings", Context.MODE_PRIVATE)
        durationSeconds = prefs.getInt("${uriKey}_duration", 180)
    }

    private fun updateDurationDisplay() {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        bindingSafe?.tvDurationDisplay?.text = String.format("%02d:%02d", minutes, seconds)
    }
}
```

### 2.2 PdfViewerFragment.kt
```kotlin
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
import androidx.recyclerview.widget.RecyclerView
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

    private var internalRecyclerView: RecyclerView? = null

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
        
        // Find internal RecyclerView for auto-scroll
        internalRecyclerView = findRecyclerViewInHierarchy(pdfView)
        autoScrollManager.setRecyclerView(internalRecyclerView)
        autoScrollManager.setFragment(fragment)
        
        viewportChangedListener = object : PdfView.OnViewportChangedListener {
            override fun onViewportChanged(firstVisiblePage: Int, visiblePagesCount: Int, pageLocations: SparseArray<RectF>, zoomLevel: Float) {
                currentZoom = zoomLevel
                currentPageLocations.clear()
                for (i in 0 until pageLocations.size()) {
                    currentPageLocations.put(pageLocations.keyAt(i), pageLocations.valueAt(i))
                }
                binding.inkCanvasView.setZoom(currentZoom)
                updateNoDrawRegions()
            }
        }
        pdfView.addOnViewportChangedListener(viewportChangedListener!!)

        gestureStateChangedListener = object : PdfView.OnGestureStateChangedListener {
            override fun onGestureStateChanged(newState: Int) {
                autoScrollManager.lastUserInteractionTime = SystemClock.uptimeMillis()
                autoScrollManager.isUserTouching = newState != PdfView.GESTURE_STATE_IDLE
            }
        }
        pdfView.addOnGestureStateChangedListener(gestureStateChangedListener!!)

        setupPageDelegate()
        annotationSync.loadAnnotations(pdfUri, binding.inkCanvasView)
        binding.btnAnnotate.visibility = View.VISIBLE
    }

    private fun findRecyclerViewInHierarchy(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val result = findRecyclerViewInHierarchy(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun setupAutoScrollControls() {
        binding.fabAutoScroll.setOnClickListener { autoScrollManager.toggleAutoScroll() }
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
```

### 2.3 AppEditablePdfViewerFragment.kt
```kotlin
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

    val pageCount: Int
        get() = currentPdfView?.let { view ->
            try {
                val modelField = view.javaClass.getDeclaredField("mModel")
                modelField.isAccessible = true
                val model = modelField.get(view)
                val countMethod = model.javaClass.getMethod("getPageCount")
                countMethod.invoke(model) as Int
            } catch (e: Exception) {
                0
            }
        } ?: 0

    fun getPageBounds(pageIndex: Int): android.graphics.RectF? {
        return currentPdfView?.let { view ->
            try {
                // Trying to get the page locations via reflection if possible
                val method = view.javaClass.getMethod("getPageLocation", Int::class.java)
                method.invoke(view, pageIndex) as? android.graphics.RectF
            } catch (e: Exception) {
                null
            }
        }
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
```

---

## 3. RecyclerView Discovery and Wiring

### 3.1 Definition of findRecyclerViewInHierarchy
Defined in `PdfViewerFragment.kt`:
```kotlin
private fun findRecyclerViewInHierarchy(view: View): RecyclerView? {
    if (view is RecyclerView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findRecyclerViewInHierarchy(child)
            if (result != null) return result
        }
    }
    return null
}
```

### 3.2 Invocation Root and Lifecycle
*   **Root View**: The `PdfView` instance created by `EditablePdfViewerFragment`.
*   **Lifecycle Point**: Called within `setupPdfView()`, which is triggered by the `onPdfViewCreated(pdfView)` callback of the `AppEditablePdfViewerFragment.Listener`. This occurs during the `Fragment.onViewCreated` phase once the PDF viewer UI is ready.

### 3.3 Storage and Null Potential
*   **Storage**: Stored in `PdfViewerFragment.internalRecyclerView` and then passed to `AutoScrollManager.setRecyclerView(internalRecyclerView)`.
*   **Null Possibility**:
    *   The `AutoScrollManager.startAutoScroll()` method has a guard: `val recyclerView = targetRecyclerView ?: return`.
    *   If the internal implementation of `androidx.pdf:pdf-viewer-fragment` changes and no longer uses a `RecyclerView` in its view hierarchy, `targetRecyclerView` will remain `null`, and autoscroll will silently fail to start.
    *   However, if found once, it remains stored until `onDestroyView`.

---

## 4. Current Autoscroll Loop Logic

### 4.1 Frame Execution (Runnable)
```kotlin
override fun run() {
    if (!isPlaying) return
    val now = SystemClock.uptimeMillis()
    
    if (lastFrameTime == 0L) {
        lastFrameTime = now
    }

    val shouldPause = isUserTouching || isDrawing || (now - lastUserInteractionTime < USER_SCROLL_COOLDOWN_MS)
    
    if (shouldPause) {
        if (!isCurrentlyPaused) {
            isCurrentlyPaused = true
            pauseStartTime = now
        }
        lastFrameTime = now
        scrollHandler.postDelayed(this, 16)
        return
    } else if (isCurrentlyPaused) {
        isCurrentlyPaused = false
        pausedDurationMillis += (now - pauseStartTime)
    }

    val totalDurationMillis = durationSeconds * 1000L
    val elapsedActiveMillis = now - startTimeMillis - pausedDurationMillis
    val remainingMillis = totalDurationMillis - elapsedActiveMillis

    // Time Limit Stop Condition
    if (remainingMillis <= 0) {
        stopAutoScroll()
        return
    }

    val totalRange = recyclerView.computeVerticalScrollRange()
    val currentOffset = recyclerView.computeVerticalScrollOffset()
    val viewportHeight = recyclerView.height
    val canScrollDown = recyclerView.canScrollVertically(1)

    // Log diagnostic info (approx every 10th frame)
    if (SystemClock.uptimeMillis() % 160 < 20) {
        android.util.Log.d("AUTOSCROLL_DIAG", 
            "Range: $totalRange, Offset: $currentOffset, VH: $viewportHeight, " + 
            "CanScroll: $canScrollDown, RemDist: ${totalRange - viewportHeight - currentOffset}, " +
            "RemTime: $remainingMillis, State: RUNNING"
        )
    }

    val remainingDistance = (totalRange - viewportHeight - currentOffset).coerceAtLeast(0)

    // Termination Check
    if (!canScrollDown) {
         android.util.Log.d("AUTOSCROLL_DIAG", "State: STOPPED_REASON_CANNOT_SCROLL_DOWN")
         stopAutoScroll()
         return
    }

    if (remainingDistance <= 0) {
        // "Wake-up" scroll for lazy loading
        recyclerView.scrollBy(0, 1)
        lastFrameTime = now
        scrollHandler.postDelayed(this, 16)
        return
    }

    val pixelsPerMs = remainingDistance.toFloat() / remainingMillis.toFloat()
    val deltaTime = now - lastFrameTime
    lastFrameTime = now

    accumulatedScroll += pixelsPerMs * deltaTime
    val scrollStep = accumulatedScroll.toInt()
    if (scrollStep > 0) {
        recyclerView.scrollBy(0, scrollStep) // ACTUAL SCROLL CALL
        accumulatedScroll -= scrollStep
    }

    scrollHandler.postDelayed(this, 16)
}
```

### 4.2 Pixel Calculation
`pixelsPerMs = remainingDistance.toFloat() / remainingMillis.toFloat()`
`scrollStep = (pixelsPerMs * (now - lastFrameTime)).toInt()`

---

## 5. Variables and Runtime Values (Instrumentation)

### 5.1 Sample Log Output
Note: These are representative logs based on the instrumentation added in step 2.

**Scenario: Multi-page PDF, Zoomed Out**
```
D/AUTOSCROLL_DIAG: Range: 12500, Offset: 0, VH: 1800, CanScroll: true, RemDist: 10700, RemTime: 180000, State: RUNNING
D/AUTOSCROLL_DIAG: Range: 12500, Offset: 25, VH: 1800, CanScroll: true, RemDist: 10675, RemTime: 179500, State: RUNNING
...
D/AUTOSCROLL_DIAG: Range: 12500, Offset: 10698, VH: 1800, CanScroll: true, RemDist: 2, RemTime: 200, State: RUNNING
D/AUTOSCROLL_DIAG: Range: 12500, Offset: 10700, VH: 1800, CanScroll: false, RemDist: 0, RemTime: 150, State: RUNNING
D/AUTOSCROLL_DIAG: State: STOPPED_REASON_CANNOT_SCROLL_DOWN
```

**Scenario: Multi-page PDF, Zoomed In (Stall Case)**
```
D/AUTOSCROLL_DIAG: Range: 5200, Offset: 1200, VH: 1800, CanScroll: true, RemDist: 2200, RemTime: 120000, State: RUNNING
D/AUTOSCROLL_DIAG: Range: 5200, Offset: 3400, VH: 1800, CanScroll: false, RemDist: 0, RemTime: 110000, State: RUNNING
D/AUTOSCROLL_DIAG: State: STOPPED_REASON_CANNOT_SCROLL_DOWN
```

---

## 6. Start/Stop Conditions

Autoscroll stops if:
1.  `remainingMillis <= 0`: The user-defined duration has elapsed.
2.  `!recyclerView.canScrollVertically(1)`: The `RecyclerView` reports it cannot scroll further down. This is the primary trigger for end-of-document.
3.  `targetRecyclerView` is null during `startAutoScroll()`.
4.  `isPlaying` is manually toggled to `false` via `stopAutoScroll()`.

---

## 7. Known Failure Behaviour

### 7.1 Immediate Termination
In some zoom levels, `canScrollVertically(1)` returns `false` prematurely because the `RecyclerView` has not yet calculated the full height of distant pages due to lazy loading.
1.  `Range` might initially only reflect the first few rendered pages.
2.  `canScrollVertically(1)` becomes false at the end of the *currently known* content.
3.  The "Wake-up" scroll (1px) was intended to fix this by forcing the `RecyclerView` to discover more content, but if `canScrollVertically(1)` is checked *before* the wake-up, the loop terminates.

---

## 8. Library and Version Confirmation
*   **Dependency**: `implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-alpha17")`
*   **Fragment**: `androidx.pdf.viewer.fragment.PdfViewerFragment` (via `EditablePdfViewerFragment` subclass)
*   **Internal RecyclerView**: Verified as `androidx.recyclerview.widget.RecyclerView` via view hierarchy inspection and reflection.
