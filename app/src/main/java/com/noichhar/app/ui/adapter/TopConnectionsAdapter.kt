package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.database.dao.AppTrafficSummary
import com.noichhar.app.databinding.ItemTopConnectionBinding

class TopConnectionsAdapter : ListAdapter<AppTrafficSummary, TopConnectionsAdapter.VH>(DIFF) {

    private var maxCount: Long = 1L

    override fun submitList(list: List<AppTrafficSummary>?) {
        maxCount = (list?.maxOfOrNull { it.totalConn } ?: 1L).coerceAtLeast(1L)
        if (currentList != list) super.submitList(list) { notifyDataSetChanged() }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemTopConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemTopConnectionBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: AppTrafficSummary) {
            b.tvName.text = s.appName.ifEmpty { s.packageName }
            b.tvCount.text = s.totalConn.toString()
            b.progress.progress = ((s.totalConn * 100.0) / maxCount).toInt().coerceIn(0, 100)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AppTrafficSummary>() {
            override fun areItemsTheSame(a: AppTrafficSummary, b: AppTrafficSummary) = a.appUid == b.appUid
            override fun areContentsTheSame(a: AppTrafficSummary, b: AppTrafficSummary) = a == b
        }
    }
}
