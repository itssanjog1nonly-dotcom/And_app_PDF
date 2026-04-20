package com.sanjog.pdfscrollreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanjog.pdfscrollreader.data.model.RecentlyOpenedEntry
import com.sanjog.pdfscrollreader.databinding.ItemRecentDocumentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentlyOpenedAdapter(
    private val onItemClick: (RecentlyOpenedEntry) -> Unit
) : ListAdapter<RecentlyOpenedEntry, RecentlyOpenedAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentDocumentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentlyOpenedEntry) {
            binding.tvName.text = item.displayName
            
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            val dateStr = sdf.format(Date(item.lastOpened))
            
            val info = if (item.lastModified > 0) {
                "Edited ${sdf.format(Date(item.lastModified))}"
            } else {
                "Opened $dateStr"
            }
            binding.tvInfo.text = info

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecentlyOpenedEntry>() {
        override fun areItemsTheSame(oldItem: RecentlyOpenedEntry, newItem: RecentlyOpenedEntry): Boolean = oldItem.uri == newItem.uri
        override fun areContentsTheSame(oldItem: RecentlyOpenedEntry, newItem: RecentlyOpenedEntry): Boolean = oldItem == newItem
    }
}
