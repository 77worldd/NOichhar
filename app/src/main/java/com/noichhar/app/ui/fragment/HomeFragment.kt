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
import com.noichhar.app.service.VpnConnectionState
import com.noichhar.app.service.VpnStateStore
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
        if (result.resultCode == Activity.RESULT_OK) {
            connectNow()
        } else {
            VpnStateStore.set(VpnConnectionState.INACTIVE)
            toast(R.string.vpn_permission_denied)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Constants.ACTION_VPN_STATE_CHANGED) return
            NOichharVpnService.syncConnectionState()
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
                // Single source of truth: the service publishes every transition, so the
                // screen flips to CONNECTING/PROTECTED the moment it happens. No polling
                // and no second tap to refresh a stale label.
                launch {
                    VpnStateStore.state.collect { render(it) }
                }
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
        ContextCompat.registerReceiver(
            requireContext(), receiver, IntentFilter(Constants.ACTION_VPN_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        NOichharVpnService.syncConnectionState()
        render(VpnStateStore.current)
    }

    override fun onStop() { runCatching { requireContext().unregisterReceiver(receiver) }; super.onStop() }

    override fun onResume() { super.onResume(); NOichharVpnService.syncConnectionState() }

    private fun render(state: VpnConnectionState) {
        val binding = _binding ?: return
        val active = state == VpnConnectionState.ACTIVE
        val connecting = state == VpnConnectionState.CONNECTING

        binding.shield.setActive(active)
        binding.shield.setConnecting(connecting)
        binding.shield.contentDescription = when {
            active -> "Protection active. Tap to stop."
            connecting -> "Protection is starting."
            else -> "Protection inactive. Tap to start."
        }
        binding.tvStatusBadge.text = when {
            active -> "PROTECTED"
            connecting -> "CONNECTING"
            else -> "INACTIVE"
        }
        binding.tvStatusBadge.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (active) R.color.text_primary else R.color.text_secondary
            )
        )
        binding.tvHeroSubtitle.text = when {
            active -> "Filtering requests on this device"
            connecting -> "Starting protection…"
            else -> "Tap to activate protection"
        }
        binding.btnToggle.text = when {
            active -> "STOP"
            connecting -> "CANCEL"
            else -> "ACTIVATE"
        }
        binding.tvTimestamp.text = when {
            active -> "live"
            connecting -> "starting"
            else -> "offline"
        }
    }

    private fun toggleVpn() {
        // The store is authoritative and is updated optimistically on tap, so a
        // double tap can never open a second consent dialog while starting.
        when (VpnStateStore.current) {
            VpnConnectionState.ACTIVE -> NOichharVpnService.instance?.stopVpn() ?: stopServiceNow()
            VpnConnectionState.CONNECTING -> stopServiceNow()
            VpnConnectionState.INACTIVE -> beginActivation()
        }
    }

    private fun beginActivation() {
        val prepare = runCatching { VpnService.prepare(requireContext()) }.getOrNull()
        if (prepare == null) {
            // Consent already granted (or not required): start on this tap, no dialog,
            // no extra confirmation step standing between the user and protection.
            connectNow()
            return
        }
        AlertDialog.Builder(requireContext()).setTitle(R.string.vpn_permission_title)
            .setMessage(R.string.vpn_permission_message)
            .setPositiveButton(R.string.continue_label) { _, _ ->
                val consent = runCatching { VpnService.prepare(requireContext()) }.getOrNull()
                if (consent != null) vpnLauncher.launch(consent) else connectNow()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    /**
     * Optimistic, instant activation: the state store is updated before the service
     * is even bound, so the UI responds on the same frame as the tap.
     */
    private fun connectNow() {
        VpnStateStore.set(VpnConnectionState.CONNECTING)
        val intent = Intent(requireContext(), NOichharVpnService::class.java).setAction(Constants.ACTION_START)
        runCatching { ContextCompat.startForegroundService(requireContext(), intent) }
            .onFailure {
                VpnStateStore.set(VpnConnectionState.INACTIVE)
                toast(R.string.vpn_start_failed)
            }
    }

    private fun stopServiceNow() {
        VpnStateStore.set(VpnConnectionState.INACTIVE)
        runCatching {
            requireContext().startService(
                Intent(requireContext(), NOichharVpnService::class.java).setAction(Constants.ACTION_STOP)
            )
        }
    }

    private fun toast(message: Int) {
        runCatching { Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
