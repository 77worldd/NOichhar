package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.R
import com.noichhar.app.database.entity.BlocklistEntry
import com.noichhar.app.databinding.ItemBlocklistBinding

class BlocklistAdapter(
    private val allowMode: Boolean = false,
    private val onDeleteClicked: (BlocklistEntry) -> Unit
) : ListAdapter<BlocklistEntry, BlocklistAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlocklistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemBlocklistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: BlocklistEntry) {
            val context = binding.root.context
            binding.tvDomain.text = if (entry.isWildcard) "*.${entry.domain}" else entry.domain
            binding.tvSource.text = context.getString(
                when {
                    allowMode && entry.isWildcard -> R.string.source_allow_wildcard
                    allowMode -> R.string.source_allow_exact
                    entry.isWildcard -> R.string.source_wildcard
                    else -> R.string.source_exact
                }
            )
            binding.btnDelete.setOnClickListener { onDeleteClicked(entry) }
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<BlocklistEntry>() {
            override fun areItemsTheSame(oldItem: BlocklistEntry, newItem: BlocklistEntry) =
                oldItem.domain == newItem.domain

            override fun areContentsTheSame(oldItem: BlocklistEntry, newItem: BlocklistEntry) =
                oldItem == newItem
        }
    }
}
