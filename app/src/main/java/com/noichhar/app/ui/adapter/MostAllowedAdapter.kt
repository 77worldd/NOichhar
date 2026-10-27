package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.database.dao.AppTrafficSummary
import com.noichhar.app.databinding.ItemMostAllowedBinding
import com.noichhar.app.util.FormatUtils

class MostAllowedAdapter : ListAdapter<AppTrafficSummary, MostAllowedAdapter.VH>(DIFF) {

    private var maxBytes: Long = 1L

    override fun submitList(list: List<AppTrafficSummary>?) {
        maxBytes = (list?.maxOfOrNull { it.totalBytes } ?: 1L).coerceAtLeast(1L)
        if (currentList != list) super.submitList(list) { notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMostAllowedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemMostAllowedBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: AppTrafficSummary) {
            b.tvName.text = s.appName.ifEmpty { s.packageName }
            b.tvBytes.text = FormatUtils.formatBytes(s.totalBytes)
            b.progress.progress = ((s.totalBytes * 100.0) / maxBytes).toInt().coerceIn(0, 100)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppTrafficSummary>() {
            override fun areItemsTheSame(a: AppTrafficSummary, b: AppTrafficSummary) = a.appUid == b.appUid
            override fun areContentsTheSame(a: AppTrafficSummary, b: AppTrafficSummary) = a == b
        }
    }
}
