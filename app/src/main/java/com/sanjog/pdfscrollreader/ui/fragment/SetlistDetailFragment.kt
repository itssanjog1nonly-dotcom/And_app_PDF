package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanjog.pdfscrollreader.data.model.Setlist
import com.sanjog.pdfscrollreader.data.model.SetlistEntry
import com.sanjog.pdfscrollreader.data.repository.SetlistRepository
import com.sanjog.pdfscrollreader.databinding.FragmentSetlistDetailBinding
import com.sanjog.pdfscrollreader.ui.adapter.SetlistEntryAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper

class SetlistDetailFragment : Fragment() {

    private var _binding: FragmentSetlistDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: SetlistRepository
    private lateinit var adapter: SetlistEntryAdapter
    private var setlistId: String? = null

    interface SetlistDetailListener {
        fun onEntrySelected(entry: SetlistEntry, setlist: Setlist)
    }

    private var listener: SetlistDetailListener? = null

    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { addEntry(it) }
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
        loadSetlist()
    }

    private fun setupToolbar() {
        val setlist = repository.getById(setlistId ?: "")
        binding.toolbar.title = setlist?.name ?: "Setlist Detail"
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = SetlistEntryAdapter(
            onItemClick = { entry ->
                repository.getById(setlistId ?: "")?.let {
                    listener?.onEntrySelected(entry, it)
                }
            },
            onDeleteClick = { entry ->
                repository.removeEntry(setlistId!!, entry.id)
                loadSetlist()
            },
            onLongClick = { entry ->
                showEntryOptionsDialog(entry)
            }
        )
        binding.rvEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEntries.adapter = adapter
        
        // Setup ItemTouchHelper for drag & drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
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

    private fun showEntryOptionsDialog(entry: SetlistEntry) {
        val options = arrayOf("Rename", "Move To", "Copy", "Remove")
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
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
            android.widget.Toast.makeText(requireContext(), "No other setlists available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = allSetlists.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Move to Setlist")
            .setItems(names) { _, which ->
                val targetSetlist = allSetlists[which]
                repository.addEntry(targetSetlist.id, entry.copy(id = java.util.UUID.randomUUID().toString(), orderIndex = targetSetlist.entries.size))
                repository.removeEntry(setlistId!!, entry.id)
                loadSetlist()
                android.widget.Toast.makeText(requireContext(), "Moved to ${targetSetlist.name}", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCopyDialog(entry: SetlistEntry) {
        val allSetlists = repository.getAll().filter { it.id != setlistId }
        if (allSetlists.isEmpty()) {
            android.widget.Toast.makeText(requireContext(), "No other setlists available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = allSetlists.map { it.name }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Copy to Setlist")
            .setItems(names) { _, which ->
                val targetSetlist = allSetlists[which]
                repository.addEntry(targetSetlist.id, entry.copy(id = java.util.UUID.randomUUID().toString(), orderIndex = targetSetlist.entries.size))
                android.widget.Toast.makeText(requireContext(), "Copied to ${targetSetlist.name}", android.widget.Toast.LENGTH_SHORT).show()
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
