package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.R
import com.noichhar.app.database.entity.ConnectionLog
import com.noichhar.app.databinding.ItemConnectionBinding
import com.noichhar.app.util.PackageUtils

class ConnectionAdapter : ListAdapter<ConnectionLog, ConnectionAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemConnectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(log: ConnectionLog) {
            val context = binding.root.context
            binding.tvAppName.text = log.appName
            binding.tvDestination.text = log.destinationIp
            binding.tvProtocol.text = context.getString(
                if (log.isBlocked) R.string.connection_blocked else R.string.connection_allowed
            )
            binding.tvProtocol.setTextColor(
                context.getColor(if (log.isBlocked) R.color.status_blocked else R.color.status_allowed)
            )

            val packageName = PackageUtils.getPackageNameForUid(context, log.appUid)
            val icon = packageName?.let { PackageUtils.getAppIcon(context, it) }
                ?: ContextCompat.getDrawable(context, if (log.isBlocked) R.drawable.ic_block else R.drawable.ic_allow)
            binding.ivAppIcon.setImageDrawable(icon)
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ConnectionLog>() {
            override fun areItemsTheSame(oldItem: ConnectionLog, newItem: ConnectionLog) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ConnectionLog, newItem: ConnectionLog) =
                oldItem == newItem
        }
    }
}
