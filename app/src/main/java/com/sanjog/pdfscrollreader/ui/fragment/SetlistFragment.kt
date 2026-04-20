package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sanjog.pdfscrollreader.data.model.Setlist
import com.sanjog.pdfscrollreader.data.repository.SetlistRepository
import com.sanjog.pdfscrollreader.databinding.FragmentSetlistBinding
import com.sanjog.pdfscrollreader.ui.adapter.SetlistAdapter

class SetlistFragment : Fragment() {

    private var _binding: FragmentSetlistBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: SetlistRepository
    private lateinit var adapter: SetlistAdapter

    interface SetlistListener {
        fun onSetlistSelected(setlist: Setlist)
    }

    private var listener: SetlistListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is SetlistListener) {
            listener = context
        }
        repository = SetlistRepository(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetlistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        loadSetlists()
    }

    private fun setupRecyclerView() {
        adapter = SetlistAdapter(
            onItemClick = { listener?.onSetlistSelected(it) },
            onLongClick = { showOptionsDialog(it) }
        )
        binding.rvSetlists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSetlists.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddSetlist.setOnClickListener {
            showCreateDialog()
        }
    }

    private fun showCreateDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Setlist Name"
        }
        AlertDialog.Builder(requireContext())
            .setTitle("New Setlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    repository.createSetlist(name)
                    loadSetlists()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOptionsDialog(setlist: Setlist) {
        val options = arrayOf("Rename", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(setlist.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameDialog(setlist)
                    1 -> showDeleteDialog(setlist)
                }
            }
            .show()
    }

    private fun showRenameDialog(setlist: Setlist) {
        val input = EditText(requireContext()).apply {
            setText(setlist.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename Setlist")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newName = input.text.toString()
                if (newName.isNotBlank() && newName != setlist.name) {
                    repository.rename(setlist.id, newName)
                    loadSetlists()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteDialog(setlist: Setlist) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Setlist")
            .setMessage("Are you sure you want to delete '${setlist.name}'?")
            .setPositiveButton("Delete") { _, _ ->
                repository.delete(setlist.id)
                loadSetlists()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSetlists() {
        val lists = repository.getAll()
        adapter.submitList(lists)
        binding.tvEmptyState.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
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
