package com.sanjog.pdfscrollreader.ui

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.sanjog.pdfscrollreader.R
import com.sanjog.pdfscrollreader.databinding.ActivityMainBinding
import com.sanjog.pdfscrollreader.ui.fragment.FilePickerFragment
import com.sanjog.pdfscrollreader.ui.fragment.PdfViewerFragment
import com.sanjog.pdfscrollreader.ui.fragment.SetlistDetailFragment
import com.sanjog.pdfscrollreader.ui.fragment.SetlistFragment

class MainActivity : AppCompatActivity(), 
    FilePickerFragment.FilePickerListener,
    SetlistFragment.SetlistListener,
    SetlistDetailFragment.SetlistDetailListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Use a more transparent toolbar for the empty state
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only apply top padding to the toolbar container to allow content to flow behind
            binding.toolbar.updatePadding(top = systemBars.top)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, FilePickerFragment())
                .commit()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                    binding.toolbar.subtitle = "Tap folder to open PDF"
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onPdfSelected(uri: Uri) {
        showPdf(uri)
    }

    override fun onSetlistSelected(setlist: com.sanjog.pdfscrollreader.data.model.Setlist) {
        val fragment = SetlistDetailFragment.newInstance(setlist.id)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("setlist_detail")
            .commit()
    }

    override fun onEntrySelected(entry: com.sanjog.pdfscrollreader.data.model.SetlistEntry, setlist: com.sanjog.pdfscrollreader.data.model.Setlist) {
        showPdf(Uri.parse(entry.pdfUri), setlistId = setlist.id, entryId = entry.id)
    }

    fun showSetlists() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, SetlistFragment())
            .addToBackStack("setlists")
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open -> {
                // If we are in viewer, go back to picker
                if (supportFragmentManager.backStackEntryCount > 0) {
                    onBackPressedDispatcher.onBackPressed()
                }
                true
            }
            R.id.action_export -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? PdfViewerFragment
                fragment?.triggerExport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showPdf(uri: Uri, setlistId: String? = null, entryId: String? = null) {
        binding.toolbar.subtitle = null
        
        // Record in recently opened
        com.sanjog.pdfscrollreader.data.repository.RecentlyOpenedRepository(this).recordOpen(uri)
        
        val fragment = PdfViewerFragment.newInstance(uri, setlistId, entryId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack("pdf_viewer")
            .commit()
    }

}
