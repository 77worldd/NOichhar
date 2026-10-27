package com.noichhar.app.ui.fragment

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentHomeBinding
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.activity.SettingsActivity
import com.noichhar.app.ui.viewmodel.MainViewModel
import com.noichhar.app.util.Constants

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(requireContext(), R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_VPN_STATE_CHANGED && _binding != null) {
                viewModel.setVpnActive(intent.getBooleanExtra(Constants.EXTRA_VPN_ACTIVE, false))
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeData()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            requireContext(),
            vpnStateReceiver,
            IntentFilter(Constants.ACTION_VPN_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        runCatching { requireContext().unregisterReceiver(vpnStateReceiver) }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshVpnStatus()
        updateConnectionInfo()
    }

    private fun setupUI() {
        binding.btnToggle.setOnClickListener { toggleVpn() }
        binding.imgShield.setOnClickListener { toggleVpn() }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }
        binding.btnUpdateFilters.setOnClickListener { viewModel.updateBlocklists() }
        binding.toggleProtection.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.setAdvancedProtection(checkedId == R.id.btn_maximum)
        }
    }

    private fun observeData() {
        viewModel.vpnActive.observe(viewLifecycleOwner) { active -> updateUI(active) }
        viewModel.advancedProtection.observe(viewLifecycleOwner) { enabled -> updateProtectionMode(enabled) }
        viewModel.blockedCount.observe(viewLifecycleOwner) { binding.tvBlockedCount.text = formatCount(it) }
        viewModel.allowedCount.observe(viewLifecycleOwner) { binding.tvAllowedCount.text = formatCount(it) }
        viewModel.totalCount.observe(viewLifecycleOwner) { binding.tvTotalCount.text = formatCount(it) }
        viewModel.blocklistCount.observe(viewLifecycleOwner) {
            binding.tvBlocklistInfo.text = getString(R.string.blocklist_count, it)
        }
        viewModel.appCount.observe(viewLifecycleOwner) {
            binding.tvAppInfo.text = getString(R.string.app_count, it)
        }
        viewModel.operationMessage.observe(viewLifecycleOwner) { message ->
            if (message == null) return@observe
            val text = message.argument?.let { getString(message.stringRes, it) }
                ?: getString(message.stringRes)
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
            viewModel.clearOperationMessage()
        }
    }

    private fun updateUI(active: Boolean) {
        if (active) {
            binding.imgShield.setImageResource(R.drawable.ic_shield_active)
            binding.tvStatus.text = getString(R.string.vpn_active)
            binding.tvStatusDetail.text = getString(R.string.tap_to_deactivate)
            binding.btnToggle.text = getString(R.string.stop)
            binding.tvProtectionState.text = getString(R.string.protection_active_short)
            binding.tvProtectionState.contentDescription = getString(R.string.protection_active_short)
        } else {
            binding.imgShield.setImageResource(R.drawable.ic_shield)
            binding.tvStatus.text = getString(R.string.vpn_inactive)
            binding.tvStatusDetail.text = getString(R.string.tap_to_activate)
            binding.btnToggle.text = getString(R.string.start)
            binding.tvProtectionState.text = getString(R.string.protection_paused)
            binding.tvProtectionState.contentDescription = getString(R.string.protection_paused)
        }
        updateConnectionInfo()
    }

    private fun updateProtectionMode(enabled: Boolean) {
        binding.toggleProtection.check(
            if (enabled) R.id.btn_maximum else R.id.btn_balanced
        )
        binding.tvProtectionSummary.text = getString(
            if (enabled) R.string.maximum_protection_summary else R.string.balanced_protection_summary
        )
        binding.tvProtectionLayers.text = getString(
            if (NOichharVpnService.advancedHostFilteringAvailable) {
                R.string.protection_layers_full
            } else {
                R.string.protection_layers_basic
            }
        )
    }

    private fun updateConnectionInfo() {
        binding.tvDnsInfo.text = getString(R.string.dns_info, viewModel.getDnsSummary())
    }

    private fun toggleVpn() {
        if (NOichharVpnService.isActive) stopVpnService() else requestVpnPermission()
    }

    private fun requestVpnPermission() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.vpn_permission_title)
            .setMessage(R.string.vpn_permission_message)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                val intent = VpnService.prepare(requireContext())
                if (intent != null) vpnPermissionLauncher.launch(intent) else startVpnService()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startVpnService() {
        val intent = Intent(requireContext(), NOichharVpnService::class.java).apply {
            action = Constants.ACTION_START
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        viewModel.setVpnActive(true)
    }

    private fun stopVpnService() {
        val intent = Intent(requireContext(), NOichharVpnService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        NOichharVpnService.instance?.stopVpn() ?: requireContext().startService(intent)
        viewModel.setVpnActive(false)
    }

    private fun formatCount(count: Long): String = when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
