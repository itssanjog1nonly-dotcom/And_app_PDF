package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.sanjog.pdfscrollreader.databinding.FragmentFilePickerBinding
import com.sanjog.pdfscrollreader.ui.MainActivity
import com.sanjog.pdfscrollreader.ui.adapter.RecentlyOpenedAdapter
import com.sanjog.pdfscrollreader.data.model.RecentlyOpenedEntry
import com.sanjog.pdfscrollreader.data.model.SetlistEntry
import com.sanjog.pdfscrollreader.data.repository.RecentlyOpenedRepository
import com.sanjog.pdfscrollreader.data.repository.SetlistRepository
import androidx.recyclerview.widget.LinearLayoutManager

class FilePickerFragment : Fragment() {

    private var _binding: FragmentFilePickerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var recentAdapter: RecentlyOpenedAdapter
    private lateinit var recentRepository: RecentlyOpenedRepository

    interface FilePickerListener {
        fun onPdfSelected(uri: Uri)
    }

    private var listener: FilePickerListener? = null

    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some providers only grant transient access; open the file while we have it.
            }
            listener?.onPdfSelected(it)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FilePickerListener) {
            listener = context
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recentRepository = RecentlyOpenedRepository(requireContext())
        setupRecentRecyclerView()
        
        binding.btnOpenFile.setOnClickListener {
            openPdfLauncher.launch(arrayOf("application/pdf"))
        }
        binding.btnMySetlists.setOnClickListener {
            (requireActivity() as? MainActivity)?.showSetlists()
        }
        
        loadRecentlyOpened()
    }

    private fun setupRecentRecyclerView() {
        recentAdapter = RecentlyOpenedAdapter(
            onItemClick = { entry ->
                val uri = Uri.parse(entry.uri)
                listener?.onPdfSelected(uri)
            },
            onLongClick = { entry ->
                showRecentFileOptionsDialog(entry)
            }
        )
        binding.rvRecentlyOpened.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentlyOpened.adapter = recentAdapter
    }

    private fun showRecentFileOptionsDialog(entry: RecentlyOpenedEntry) {
        val options = arrayOf("Open", "Add to Setlist...")
        AlertDialog.Builder(requireContext())
            .setTitle(entry.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val uri = Uri.parse(entry.uri)
                        listener?.onPdfSelected(uri)
                    }
                    1 -> showAddToSetlistDialog(entry)
                }
            }
            .show()
    }

    private fun showAddToSetlistDialog(entry: RecentlyOpenedEntry) {
        val repo = SetlistRepository(requireContext())
        val setlists = repo.getAll()
        if (setlists.isEmpty()) {
            Toast.makeText(requireContext(), "No setlists yet. Create one first!", Toast.LENGTH_SHORT).show()
            return
        }
        val names = setlists.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Setlist")
            .setItems(names) { _, which ->
                val target = setlists[which]
                val uri = entry.uri
                // Duplicate check
                if (target.entries.any { it.pdfUri == uri }) {
                    Toast.makeText(requireContext(), "Already in ${target.name}", Toast.LENGTH_SHORT).show()
                    return@setItems
                }
                val newEntry = SetlistEntry(
                    pdfUri = uri,
                    displayName = entry.displayName,
                    orderIndex = target.entries.size
                )
                repo.addEntry(target.id, newEntry)
                Toast.makeText(requireContext(), "Added to ${target.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadRecentlyOpened() {
        val entries = recentRepository.getAll()
        recentAdapter.submitList(entries)
        
        val hasRecent = entries.isNotEmpty()
        binding.tvRecentlyOpenedLabel.visibility = if (hasRecent) View.VISIBLE else View.GONE
        binding.rvRecentlyOpened.visibility = if (hasRecent) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadRecentlyOpened()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
