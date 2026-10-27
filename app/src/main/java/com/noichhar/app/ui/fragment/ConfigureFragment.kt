package com.noichhar.app.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentConfigureBinding
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.activity.DetailActivity
import com.noichhar.app.ui.activity.SettingsActivity
import com.noichhar.app.util.Constants

class ConfigureFragment : Fragment() {
    private var _binding: FragmentConfigureBinding? = null
    private val b get() = _binding!!
    private val pref get() = NOichharApp.instance.preferenceManager
    private var updating = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentConfigureBinding.inflate(inflater, container, false); return b.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        b.swBoot.setOnCheckedChangeListener { _, checked -> if (!updating) pref.startOnBoot = checked }
        b.swLogs.setOnCheckedChangeListener { _, checked -> if (!updating) pref.enableLogs = checked }
        b.swUnknown.setOnCheckedChangeListener { _, checked ->
            if (!updating) { pref.blockUnknown = checked; NOichharVpnService.instance?.getDnsCache()?.clear() }
        }
        b.rgDns.setOnCheckedChangeListener { _, id ->
            if (updating) return@setOnCheckedChangeListener
            if (id == R.id.rb_custom) {
                startActivity(DetailActivity.intent(requireContext(), DetailActivity.SCREEN_DNS))
                renderSettings(); return@setOnCheckedChangeListener
            }
            val provider = when (id) {
                R.id.rb_system -> Constants.DnsProvider.SYSTEM
                R.id.rb_g -> Constants.DnsProvider.GOOGLE
                R.id.rb_q -> Constants.DnsProvider.QUAD9
                R.id.rb_ag -> Constants.DnsProvider.ADGUARD
                else -> Constants.DnsProvider.CLOUDFLARE
            }
            pref.dnsProvider = provider
            if (provider != Constants.DnsProvider.SYSTEM) pref.dnsProtocol = Constants.DnsProtocol.DOH
            b.tvDnsSummary.text = pref.getDnsSummary()
            NOichharVpnService.instance?.reloadConfiguration()
        }
        b.btnDnsDetails.setOnClickListener { startActivity(DetailActivity.intent(requireContext(), DetailActivity.SCREEN_DNS)) }
        b.btnMoreSettings.setOnClickListener { startActivity(Intent(requireContext(), SettingsActivity::class.java)) }
        b.btnUsageAccess.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                .onFailure { Toast.makeText(requireContext(), "Usage Access settings are unavailable", Toast.LENGTH_SHORT).show() }
        }
    }
    override fun onResume() { super.onResume(); renderSettings() }
    private fun renderSettings() {
        updating = true
        b.swBoot.isChecked = pref.startOnBoot; b.swLogs.isChecked = pref.enableLogs; b.swUnknown.isChecked = pref.blockUnknown
        b.rgDns.check(when (pref.dnsProvider) {
            Constants.DnsProvider.SYSTEM -> R.id.rb_system
            Constants.DnsProvider.CUSTOM -> R.id.rb_custom
            Constants.DnsProvider.GOOGLE -> R.id.rb_g
            Constants.DnsProvider.QUAD9 -> R.id.rb_q
            Constants.DnsProvider.ADGUARD -> R.id.rb_ag
            Constants.DnsProvider.CLOUDFLARE -> R.id.rb_cf
        })
        b.tvDnsSummary.text = pref.getDnsSummary()
        updating = false
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
