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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentHomeBinding
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.viewmodel.HomeViewModel
import com.noichhar.app.util.Constants
import com.noichhar.app.util.FormatUtils
import com.noichhar.app.util.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels()
    private val vpnLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) startVpn()
        else Toast.makeText(requireContext(), R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_VPN_STATE_CHANGED && _binding != null)
                renderVpn(intent.getBooleanExtra(Constants.EXTRA_VPN_ACTIVE, false))
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false); return binding.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.btnToggle.setOnClickListener { HapticUtils.strong(it); toggleVpn() }
        binding.shield.setOnClickListener { HapticUtils.light(it); toggleVpn() }
        binding.shield.isFocusable = true
        vm.activity.observe(viewLifecycleOwner) {
            binding.tvAllowedBig.text = FormatUtils.formatCount(it.allowed)
            binding.tvBlockedBig.text = FormatUtils.formatCount(it.blocked)
        }
        vm.heatmap.observe(viewLifecycleOwner) { binding.heatmap.setData(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NOichharApp.instance.trafficTracker.rates.collect { rate ->
                        binding.tvDownRate.text = FormatUtils.formatRate(rate.down)
                        binding.tvUpRate.text = FormatUtils.formatRate(rate.up)
                        binding.waveform.setRates(rate.up, rate.down)
                    }
                }
                launch { while (isActive) { vm.refreshHeatmap(); delay(15_000) } }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(requireContext(), receiver, IntentFilter(Constants.ACTION_VPN_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        renderVpn(NOichharVpnService.isActive)
    }
    override fun onStop() { runCatching { requireContext().unregisterReceiver(receiver) }; super.onStop() }
    override fun onResume() { super.onResume(); renderVpn(NOichharVpnService.isActive) }
    private fun renderVpn(active: Boolean) {
        binding.shield.setActive(active)
        binding.shield.contentDescription = if (active) "Protection active. Tap to stop." else "Protection inactive. Tap to start."
        binding.tvStatusBadge.text = if (active) "PROTECTED" else "INACTIVE"
        binding.tvStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), if (active) R.color.accent_mint else R.color.text_secondary))
        binding.tvHeroSubtitle.text = if (active) "Filtering requests on this device" else "Tap to activate protection"
        binding.btnToggle.text = if (active) "STOP" else "ACTIVATE"
        binding.tvTimestamp.text = if (active) "live" else "offline"
    }
    private fun toggleVpn() {
        if (NOichharVpnService.isActive) {
            NOichharVpnService.instance?.stopVpn()
        } else {
            AlertDialog.Builder(requireContext()).setTitle(R.string.vpn_permission_title)
                .setMessage(R.string.vpn_permission_message)
                .setPositiveButton(R.string.continue_label) { _, _ ->
                    val intent = VpnService.prepare(requireContext())
                    if (intent != null) vpnLauncher.launch(intent) else startVpn()
                }.setNegativeButton(R.string.cancel, null).show()
        }
    }
    private fun startVpn() {
        val intent = Intent(requireContext(), NOichharVpnService::class.java).setAction(Constants.ACTION_START)
        ContextCompat.startForegroundService(requireContext(), intent)
        binding.tvHeroSubtitle.text = "Starting protection…"
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
