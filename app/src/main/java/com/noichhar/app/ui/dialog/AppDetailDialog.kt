package com.noichhar.app.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.noichhar.app.R
import com.noichhar.app.database.entity.AppInfo
import com.noichhar.app.databinding.DialogAppDetailBinding
import com.noichhar.app.util.PackageUtils

class AppDetailDialog(
    context: Context,
    private val app: AppInfo,
    private val onSave: (AppInfo) -> Unit
) {
    private val binding = DialogAppDetailBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    init {
        binding.tvName.text = app.appName
        binding.tvPackage.text = app.packageName
        binding.ivIcon.setImageDrawable(
            PackageUtils.getAppIcon(context, app.packageName)
        )

        binding.switchAllow.isChecked = app.isInternetAllowed
        binding.switchWifi.isChecked = app.wifiAllowed
        binding.switchMobile.isChecked = app.mobileAllowed
        binding.switchBackground.isChecked = app.backgroundAllowed

        dialog = AlertDialog.Builder(context)
            .setTitle(app.appName)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                onSave(
                    app.copy(
                        isInternetAllowed = binding.switchAllow.isChecked,
                        wifiAllowed = binding.switchWifi.isChecked,
                        mobileAllowed = binding.switchMobile.isChecked,
                        backgroundAllowed = binding.switchBackground.isChecked
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    fun show() = dialog.show()
}
