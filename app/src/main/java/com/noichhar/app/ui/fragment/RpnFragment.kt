package com.noichhar.app.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentRpnBinding
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.activity.AppManagerActivity
import com.noichhar.app.ui.activity.DetailActivity
import com.noichhar.app.ui.activity.LogActivity
import com.noichhar.app.util.Constants
import com.noichhar.app.util.FormatUtils
import kotlinx.coroutines.launch

class RpnFragment : Fragment() {
    private var _binding: FragmentRpnBinding? = null
    private val b get() = _binding!!
    private val app get() = NOichharApp.instance
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentRpnBinding.inflate(inflater, container, false); return b.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        app.database.blocklistDao().getBlockRuleCount().observe(viewLifecycleOwner) {
            b.tvBlocklistCount.text = "${FormatUtils.formatCount(it.toLong())} block rules installed"
        }
        b.rgMode.check(when (app.preferenceManager.protectionMode) {
            Constants.ProtectionMode.DNS_ONLY -> R.id.rb_dns
            Constants.ProtectionMode.APP_RULES_ONLY -> R.id.rb_firewall
            Constants.ProtectionMode.BOTH -> R.id.rb_both
        })
        b.rgMode.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.rb_dns -> Constants.ProtectionMode.DNS_ONLY
                R.id.rb_firewall -> Constants.ProtectionMode.APP_RULES_ONLY
                else -> Constants.ProtectionMode.BOTH
            }
            if (app.preferenceManager.protectionMode != mode) {
                app.preferenceManager.protectionMode = mode
                NOichharVpnService.instance?.reloadConfiguration()
            }
        }
        b.btnUpdateLists.setOnClickListener {
            b.btnUpdateLists.isEnabled = false
            b.btnUpdateLists.text = "Updating…"
            viewLifecycleOwner.lifecycleScope.launch {
                val count = runCatching { app.blocklistManager.updateBlocklists() }.getOrDefault(0)
                if (_binding == null) return@launch
                b.btnUpdateLists.isEnabled = true; b.btnUpdateLists.text = "Update Blocklists Now"
                if (count > 0) {
                    NOichharVpnService.instance?.refreshProtection()
                    Toast.makeText(requireContext(), "Blocklists updated: $count rules", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(requireContext(), "Update failed; existing rules were kept", Toast.LENGTH_LONG).show()
            }
        }
        b.btnAddRule.setOnClickListener { addRule() }
        b.btnManageRules.setOnClickListener { startActivity(DetailActivity.intent(requireContext(), DetailActivity.SCREEN_RULES)) }
        b.btnManageApps.setOnClickListener { startActivity(Intent(requireContext(), AppManagerActivity::class.java)) }
        b.btnViewLogs.setOnClickListener { startActivity(Intent(requireContext(), LogActivity::class.java)) }
    }
    private fun addRule() {
        val input = EditText(requireContext()).apply {
            hint = "example.com or *.example.com"; maxLines = 1
            val pad = (20 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        }
        AlertDialog.Builder(requireContext()).setTitle("Add block rule").setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = input.text.toString().trim()
                viewLifecycleOwner.lifecycleScope.launch {
                    val added = app.blocklistManager.addCustomDomain(value)
                    if (added) NOichharVpnService.instance?.getDnsCache()?.clear()
                    Toast.makeText(requireContext(), if (added) "Rule added" else "Enter a valid domain", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
