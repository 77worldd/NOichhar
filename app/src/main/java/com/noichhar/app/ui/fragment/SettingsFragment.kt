package com.noichhar.app.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentSettingsBinding
import com.noichhar.app.service.NOichharVpnService
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val prefManager = NOichharApp.instance.preferenceManager
    private val blocklistManager = NOichharApp.instance.blocklistManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        binding.switchAdvancedProtection.isChecked = prefManager.advancedProtection
        binding.switchBlockDoh.isChecked = prefManager.blockDohBypass
        binding.switchAutoUpdate.isChecked = prefManager.autoUpdateBlocklists
    }

    private fun setupUI() {
        binding.switchBoot.isChecked = prefManager.startOnBoot
        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefManager.startOnBoot = isChecked
        }

        binding.switchLogs.isChecked = prefManager.enableLogs
        binding.switchLogs.setOnCheckedChangeListener { _, isChecked ->
            prefManager.enableLogs = isChecked
        }

        binding.switchAdvancedProtection.isChecked = prefManager.advancedProtection
        binding.switchAdvancedProtection.setOnCheckedChangeListener { _, isChecked ->
            prefManager.advancedProtection = isChecked
            NOichharVpnService.instance?.refreshProtection()
        }

        binding.switchBlockDoh.isChecked = prefManager.blockDohBypass
        binding.switchBlockDoh.setOnCheckedChangeListener { _, isChecked ->
            prefManager.blockDohBypass = isChecked
            viewLifecycleOwner.lifecycleScope.launch {
                blocklistManager.reloadRules()
                NOichharVpnService.instance?.refreshProtection()
            }
        }

        binding.switchAutoUpdate.isChecked = prefManager.autoUpdateBlocklists
        binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            prefManager.autoUpdateBlocklists = isChecked
            NOichharApp.instance.scheduleBlocklistUpdates()
        }

        binding.btnRefreshProtection.setOnClickListener {
            val service = NOichharVpnService.instance
            if (service != null && NOichharVpnService.isActive) {
                service.refreshProtection()
                Toast.makeText(requireContext(), R.string.protection_refreshed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.start_protection_first, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnVpnSettings.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                .onFailure { error ->
                    if (error is ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.open_vpn_settings, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.tvVersion.text = getString(R.string.version_info, readVersionName())
    }

    private fun readVersionName(): String = try {
        val packageManager = requireContext().packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                requireContext().packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).versionName ?: "unknown"
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "unknown"
        }
    } catch (_: Exception) {
        "unknown"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
