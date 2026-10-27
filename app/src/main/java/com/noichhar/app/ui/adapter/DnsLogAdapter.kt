package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.R
import com.noichhar.app.database.entity.DnsLog
import com.noichhar.app.databinding.ItemDnsLogBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DnsLogAdapter : ListAdapter<DnsLog, DnsLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDnsLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDnsLogBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: DnsLog) {
            val context = binding.root.context
            binding.tvDomain.text = log.domain
            binding.tvQueryType.text = buildString {
                append(log.queryType)
                log.responseIp?.let { append(" • ").append(it) }
                if (log.isBlocked) {
                    append(" • ").append(context.getString(R.string.connection_blocked))
                    log.blockReason?.let { append(" (").append(it).append(')') }
                }
            }
            binding.tvTime.text = timeFormat.format(Date(log.timestamp))
            binding.tvResponseTime.text = "${log.responseTimeMs}ms"

            val color = if (log.isBlocked) R.color.status_blocked else R.color.status_allowed
            binding.viewStatus.setBackgroundColor(context.getColor(color))
            binding.tvDomain.setTextColor(
                context.getColor(if (log.isBlocked) R.color.text_secondary else R.color.text_primary)
            )
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DnsLog>() {
            override fun areItemsTheSame(oldItem: DnsLog, newItem: DnsLog) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DnsLog, newItem: DnsLog) =
                oldItem == newItem
        }
    }
}
