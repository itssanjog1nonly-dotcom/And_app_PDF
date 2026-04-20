package com.sanjog.pdfscrollreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanjog.pdfscrollreader.data.model.SectionBookmark
import com.sanjog.pdfscrollreader.databinding.ItemSectionMarkerBinding

class SectionMarkerAdapter(
    private val onItemClick: (SectionBookmark) -> Unit,
    private val onDeleteClick: (SectionBookmark) -> Unit
) : ListAdapter<SectionBookmark, SectionMarkerAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSectionMarkerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemSectionMarkerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SectionBookmark) {
            binding.tvLabel.text = item.label
            binding.tvPage.text = "Page ${item.pageNumber + 1}"
            
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SectionBookmark>() {
        override fun areItemsTheSame(oldItem: SectionBookmark, newItem: SectionBookmark): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SectionBookmark, newItem: SectionBookmark): Boolean = oldItem == newItem
    }
}
