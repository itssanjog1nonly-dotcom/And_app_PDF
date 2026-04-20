package com.sanjog.pdfscrollreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanjog.pdfscrollreader.data.model.SetlistEntry
import com.sanjog.pdfscrollreader.databinding.ItemSetlistEntryBinding

class SetlistEntryAdapter(
    private val onItemClick: (SetlistEntry) -> Unit,
    private val onDeleteClick: (SetlistEntry) -> Unit,
    private val onLongClick: (SetlistEntry) -> Unit
) : ListAdapter<SetlistEntry, SetlistEntryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSetlistEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, position + 1)
    }

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)
        submitList(currentList)
    }

    inner class ViewHolder(private val binding: ItemSetlistEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SetlistEntry, index: Int) {
            binding.tvIndex.text = index.toString()
            binding.tvName.text = item.displayName
            
            val mins = item.durationMinutes
            if (mins > 0) {
                binding.tvDuration.text = String.format("%02d:00", mins)
                binding.tvDuration.visibility = android.view.View.VISIBLE
            } else {
                binding.tvDuration.visibility = android.view.View.GONE
            }

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { 
                onLongClick(item)
                true 
            }
            binding.btnDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<SetlistEntry>() {
        override fun areItemsTheSame(oldItem: SetlistEntry, newItem: SetlistEntry): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SetlistEntry, newItem: SetlistEntry): Boolean = oldItem == newItem
    }
}
