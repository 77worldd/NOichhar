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
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val prefManager = NOichharApp.instance.preferenceManager

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

    private fun setupUI() {
        binding.switchBoot.isChecked = prefManager.startOnBoot
        binding.switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefManager.startOnBoot = isChecked
        }

        binding.switchLogs.isChecked = prefManager.enableLogs
        binding.switchLogs.setOnCheckedChangeListener { _, isChecked ->
            prefManager.enableLogs = isChecked
        }

        binding.switchDarkMode.isChecked = prefManager.darkMode
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefManager.darkMode = isChecked
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
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
            ).versionName ?: "1.0.0"
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "1.0.0"
        }
    } catch (_: Exception) {
        "1.0.0"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
