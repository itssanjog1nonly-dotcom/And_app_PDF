package com.sanjog.pdfscrollreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sanjog.pdfscrollreader.data.model.Setlist
import com.sanjog.pdfscrollreader.databinding.ItemSetlistBinding

class SetlistAdapter(
    private val onItemClick: (Setlist) -> Unit,
    private val onLongClick: (Setlist) -> Unit
) : ListAdapter<Setlist, SetlistAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSetlistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class ViewHolder(private val binding: ItemSetlistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Setlist) {
            binding.tvName.text = item.name
            binding.tvCount.text = "${item.entries.size} songs"
            
            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Setlist>() {
        override fun areItemsTheSame(oldItem: Setlist, newItem: Setlist): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Setlist, newItem: Setlist): Boolean = oldItem == newItem
    }
}
