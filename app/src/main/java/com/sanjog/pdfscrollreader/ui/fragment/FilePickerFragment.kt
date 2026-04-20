package com.sanjog.pdfscrollreader.ui.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.sanjog.pdfscrollreader.databinding.FragmentFilePickerBinding
import com.sanjog.pdfscrollreader.ui.MainActivity
import com.sanjog.pdfscrollreader.ui.adapter.RecentlyOpenedAdapter
import com.sanjog.pdfscrollreader.data.repository.RecentlyOpenedRepository
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
        recentAdapter = RecentlyOpenedAdapter { entry ->
            val uri = Uri.parse(entry.uri)
            listener?.onPdfSelected(uri)
        }
        binding.rvRecentlyOpened.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentlyOpened.adapter = recentAdapter
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
