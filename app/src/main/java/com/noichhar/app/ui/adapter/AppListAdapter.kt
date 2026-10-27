package com.noichhar.app.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.noichhar.app.R
import com.noichhar.app.database.entity.AppInfo
import com.noichhar.app.databinding.ItemAppBinding
import com.noichhar.app.util.PackageUtils

class AppListAdapter(
    private val onAppToggled: (AppInfo, Boolean) -> Unit,
    private val onWifiToggled: (AppInfo, Boolean) -> Unit,
    private val onMobileToggled: (AppInfo, Boolean) -> Unit,
    private val onAppClicked: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppListAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo) {
            val context = binding.root.context
            binding.tvAppName.text = app.appName
            binding.tvPackageName.text = app.packageName
            binding.tvAppConnections.text = context.getString(
                R.string.app_rule_summary,
                context.getString(if (app.wifiAllowed) R.string.enabled else R.string.disabled),
                context.getString(if (app.mobileAllowed) R.string.enabled else R.string.disabled),
                context.getString(if (app.backgroundAllowed) R.string.enabled else R.string.disabled)
            )

            binding.ivAppIcon.setImageDrawable(
                PackageUtils.getAppIcon(context, app.packageName)
                    ?: ContextCompat.getDrawable(context, R.drawable.ic_apps)
            )

            tintNetworkButton(binding.btnWifi, app.wifiAllowed)
            tintNetworkButton(binding.btnMobile, app.mobileAllowed)

            binding.switchApp.setOnCheckedChangeListener(null)
            binding.switchApp.isChecked = app.isInternetAllowed
            binding.switchApp.setOnCheckedChangeListener { _, isChecked ->
                onAppToggled(app, isChecked)
            }

            binding.btnWifi.setOnClickListener { onWifiToggled(app, !app.wifiAllowed) }
            binding.btnMobile.setOnClickListener { onMobileToggled(app, !app.mobileAllowed) }
            binding.root.setOnClickListener { onAppClicked(app) }
            binding.root.alpha = if (app.isInternetAllowed) 1.0f else 0.55f
        }

        private fun tintNetworkButton(button: android.widget.ImageButton, enabled: Boolean) {
            val color = ContextCompat.getColor(
                button.context,
                if (enabled) R.color.text_tertiary else R.color.text_hint
            )
            button.setColorFilter(color)
            button.alpha = if (enabled) 1.0f else 0.55f
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem == newItem
        }
    }
}
