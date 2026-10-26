package com.noichhar.app.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.noichhar.app.R
import com.noichhar.app.databinding.DialogDnsConfigBinding

class DnsConfigDialog(
    context: Context,
    initialUrl: String = "",
    initialName: String = "",
    private val onSave: (String, String) -> Unit
) {
    private val binding = DialogDnsConfigBinding.inflate(LayoutInflater.from(context))
    private val dialog: AlertDialog

    init {
        binding.etDnsUrl.setText(initialUrl)
        binding.etDnsName.setText(initialName)
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = binding.etDnsUrl.text?.toString()?.trim().orEmpty()
                val name = binding.etDnsName.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) onSave(url, name)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    fun show() = dialog.show()
}
