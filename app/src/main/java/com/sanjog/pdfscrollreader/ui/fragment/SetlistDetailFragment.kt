package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanjog.pdfscrollreader.data.model.Setlist
import com.sanjog.pdfscrollreader.data.model.SetlistEntry
import com.sanjog.pdfscrollreader.data.repository.SetlistRepository
import com.sanjog.pdfscrollreader.databinding.FragmentSetlistDetailBinding
import com.sanjog.pdfscrollreader.ui.adapter.SetlistEntryAdapter
import com.sanjog.pdfscrollreader.ui.fragment.pdfviewer.BatchExportManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import kotlinx.coroutines.launch

class SetlistDetailFragment : Fragment() {

    private var _binding: FragmentSetlistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: SetlistRepository
    private lateinit var adapter: SetlistEntryAdapter
    private var setlistId: String? = null

    // Selection mode state
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()

    interface SetlistDetailListener {
        fun onEntrySelected(entry: SetlistEntry, setlist: Setlist)
    }

    private var listener: SetlistDetailListener? = null

    // Multi-file picker: OpenMultipleDocuments returns List<Uri>
    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            var added = 0
            var skipped = 0
            for (uri in uris) {
                if (isDuplicate(uri)) {
                    skipped++
                } else {
                    addEntry(uri)
                    added++
                }
            }
            val msg = buildString {
                if (added > 0) append("Added $added song${if (added > 1) "s" else ""}")
                if (skipped > 0) {
                    if (added > 0) append(", ")
                    append("$skipped duplicate${if (skipped > 1) "s" else ""} skipped")
                }
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Batch export: pick destination folder
    private val exportFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { folderUri ->
        folderUri?.let { performBatchExport(it) }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SetlistDetailListener) {
            listener = context
        }
        repository = SetlistRepository(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setlistId = arguments?.getString(ARG_SETLIST_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetlistDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupSelectionToolbar()
        loadSetlist()
    }

    private fun setupToolbar() {
        val setlist = repository.getById(setlistId ?: "")
        binding.toolbar.title = setlist?.name ?: "Setlist Detail"
        binding.toolbar.setNavigationOnClickListener {
            if (isSelectionMode) {
                exitSelectionMode()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun setupSelectionToolbar() {
        binding.selectionToolbar.visibility = View.GONE

        binding.btnSelectionCancel.setOnClickListener { exitSelectionMode() }
        binding.btnSelectAll.setOnClickListener {
            val setlist = repository.getById(setlistId ?: "") ?: return@setOnClickListener
            val allIds = setlist.entries.map { it.id }.toSet()
            if (selectedIds.size == allIds.size) {
                // Deselect all
                selectedIds.clear()
            } else {
                selectedIds.clear()
                selectedIds.addAll(allIds)
            }
            updateSelectionUI()
            loadSetlist()
        }
        binding.btnSelectionExport.setOnClickListener {
            if (selectedIds.isEmpty()) {
                Toast.makeText(requireContext(), "No items selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportFolderLauncher.launch(null)
        }
        binding.btnSelectionRemove.setOnClickListener {
            if (selectedIds.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle("Remove Selected")
                .setMessage("Remove ${selectedIds.size} song${if (selectedIds.size > 1) "s" else ""} from setlist?")
                .setPositiveButton("Remove") { _, _ ->
                    selectedIds.forEach { entryId ->
                        repository.removeEntry(setlistId!!, entryId)
                    }
                    exitSelectionMode()
                    loadSetlist()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun enterSelectionMode(initialEntryId: String? = null) {
        isSelectionMode = true
        selectedIds.clear()
        initialEntryId?.let { selectedIds.add(it) }
        binding.selectionToolbar.visibility = View.VISIBLE
        binding.toolbar.visibility = View.GONE
        binding.fabAddEntry.visibility = View.GONE
        updateSelectionUI()
        loadSetlist()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        binding.selectionToolbar.visibility = View.GONE
        binding.toolbar.visibility = View.VISIBLE
        binding.fabAddEntry.visibility = View.VISIBLE
        loadSetlist()
    }

    private fun updateSelectionUI() {
        val count = selectedIds.size
        binding.tvSelectionCount.text = "$count selected"

        val setlist = repository.getById(setlistId ?: "")
        val allCount = setlist?.entries?.size ?: 0
        binding.btnSelectAll.text = if (count == allCount && allCount > 0) "Deselect All" else "Select All"
    }

    private fun toggleSelection(entryId: String) {
        if (selectedIds.contains(entryId)) {
            selectedIds.remove(entryId)
        } else {
            selectedIds.add(entryId)
        }
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
        } else {
            updateSelectionUI()
            loadSetlist()
        }
    }

    private fun setupRecyclerView() {
        adapter = SetlistEntryAdapter(
            onItemClick = { entry ->
                if (isSelectionMode) {
                    toggleSelection(entry.id)
                } else {
                    repository.getById(setlistId ?: "")?.let {
                        listener?.onEntrySelected(entry, it)
                    }
                }
            },
            onDeleteClick = { entry ->
                if (!isSelectionMode) {
                    repository.removeEntry(setlistId!!, entry.id)
                    loadSetlist()
                }
            },
            onLongClick = { entry ->
                if (isSelectionMode) {
                    toggleSelection(entry.id)
                } else {
                    enterSelectionMode(entry.id)
                }
            }
        )
        adapter.setSelectionState(isSelectionMode, selectedIds)
        binding.rvEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEntries.adapter = adapter
        
        // Setup ItemTouchHelper for drag & drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = !isSelectionMode

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // Save new order
                repository.getById(setlistId ?: "")?.let { sl ->
                    val updatedEntries = adapter.currentList.mapIndexed { index, entry ->
                        entry.copy(orderIndex = index)
                    }
                    repository.save(sl.copy(entries = updatedEntries))
                    loadSetlist()
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvEntries)
    }

    private fun setupFab() {
        binding.fabAddEntry.setOnClickListener {
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }
    }

    private fun isDuplicate(uri: Uri): Boolean {
        val setlist = repository.getById(setlistId ?: "") ?: return false
        return setlist.entries.any { it.pdfUri == uri.toString() }
    }

    private fun addEntry(uri: Uri) {
        val id = setlistId ?: return
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some providers do not offer persistable grants; keep the entry anyway.
        }
        val name = com.sanjog.pdfscrollreader.util.SaftUtil.getFileDisplayName(requireContext(), uri)
        val entry = SetlistEntry(
            pdfUri = uri.toString(),
            displayName = name,
            orderIndex = adapter.itemCount
        )
        repository.addEntry(id, entry)
        loadSetlist()
    }

    private fun performBatchExport(folderUri: Uri) {
        val setlist = repository.getById(setlistId ?: "") ?: return
        val entriesToExport = setlist.entries.filter { it.id in selectedIds }
        if (entriesToExport.isEmpty()) return

        val items = entriesToExport.map { entry ->
            BatchExportManager.ExportItem(
                pdfUri = Uri.parse(entry.pdfUri),
                displayName = entry.displayName
            )
        }

        val manager = BatchExportManager(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val result = manager.exportAll(items, folderUri)
            Toast.makeText(
                requireContext(),
                "Exported ${result.success} of ${result.total} PDFs" +
                    if (result.failed > 0) " (${result.failed} failed)" else "",
                Toast.LENGTH_LONG
            ).show()
            exitSelectionMode()
        }
    }

    private fun showEntryOptionsDialog(entry: SetlistEntry) {
        val options = arrayOf("Rename", "Move To", "Copy", "Remove")
        AlertDialog.Builder(requireContext())
            .setTitle(entry.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameEntryDialog(entry)
                    1 -> showMoveToDialog(entry)
                    2 -> showCopyDialog(entry)
                    3 -> {
                        repository.removeEntry(setlistId!!, entry.id)
                        loadSetlist()
                    }
                }
            }
            .show()
    }

    private fun showRenameEntryDialog(entry: SetlistEntry) {
        val input = android.widget.EditText(requireContext()).apply {
            setText(entry.displayName)
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename PDF")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank() && newName != entry.displayName) {
                    repository.getById(setlistId ?: "")?.let { sl ->
                        val updatedEntries = sl.entries.map { 
                            if (it.id == entry.id) it.copy(displayName = newName) else it 
                        }
                        repository.save(sl.copy(entries = updatedEntries))
                        loadSetlist()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMoveToDialog(entry: SetlistEntry) {
        val allSetlists = repository.getAll().filter { it.id != setlistId }
        if (allSetlists.isEmpty()) {
            Toast.makeText(requireContext(), "No other setlists available", Toast.LENGTH_SHORT).show()
            return
        }
        val names = allSetlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Move to Setlist")
            .setItems(names) { _, which ->
                val targetSetlist = allSetlists[which]
                repository.addEntry(targetSetlist.id, entry.copy(id = java.util.UUID.randomUUID().toString(), orderIndex = targetSetlist.entries.size))
                repository.removeEntry(setlistId!!, entry.id)
                loadSetlist()
                Toast.makeText(requireContext(), "Moved to ${targetSetlist.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCopyDialog(entry: SetlistEntry) {
        val allSetlists = repository.getAll().filter { it.id != setlistId }
        if (allSetlists.isEmpty()) {
            Toast.makeText(requireContext(), "No other setlists available", Toast.LENGTH_SHORT).show()
            return
        }
        val names = allSetlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Copy to Setlist")
            .setItems(names) { _, which ->
                val targetSetlist = allSetlists[which]
                repository.addEntry(targetSetlist.id, entry.copy(id = java.util.UUID.randomUUID().toString(), orderIndex = targetSetlist.entries.size))
                Toast.makeText(requireContext(), "Copied to ${targetSetlist.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveDialog(entry: SetlistEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Song")
            .setMessage("Remove '${entry.displayName}' from setlist?")
            .setPositiveButton("Remove") { _, _ ->
                repository.removeEntry(setlistId ?: "", entry.id)
                loadSetlist()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSetlist() {
        val setlist = repository.getById(setlistId ?: "")
        val entries = setlist?.entries?.sortedBy { it.orderIndex } ?: emptyList()
        adapter.setSelectionState(isSelectionMode, selectedIds)
        adapter.submitList(entries)
        binding.tvEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val ARG_SETLIST_ID = "setlist_id"

        fun newInstance(setlistId: String): SetlistDetailFragment {
            return SetlistDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SETLIST_ID, setlistId)
                }
            }
        }
    }
}
