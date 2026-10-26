package com.noichhar.app.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentDnsBinding
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.ui.dialog.DnsConfigDialog
import com.noichhar.app.ui.viewmodel.DnsViewModel
import com.noichhar.app.util.Constants
import com.noichhar.app.util.DnsEndpointValidator
import com.noichhar.app.util.NetworkUtils

class DnsFragment : Fragment() {

    private var _binding: FragmentDnsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DnsViewModel by viewModels()

    private var selectedProvider: Constants.DnsProvider = Constants.DnsProvider.CLOUDFLARE
    private var selectedProtocol: Constants.DnsProtocol = Constants.DnsProtocol.DOH
    private var customName: String = ""
    private var updatingFromViewModel = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        selectedProvider = viewModel.dnsProvider.value ?: selectedProvider
        selectedProtocol = viewModel.dnsProtocol.value ?: selectedProtocol
        customName = viewModel.customDnsName.value.orEmpty()
        setupUI()
        observeData()
        updatePrivateDnsWarning()
    }

    override fun onResume() {
        super.onResume()
        updatePrivateDnsWarning()
    }

    private fun setupUI() {
        binding.rgDnsProvider.setOnCheckedChangeListener { _, checkedId ->
            if (updatingFromViewModel) return@setOnCheckedChangeListener
            selectedProvider = providerFor(checkedId)
            updateCustomVisibility()
            updateProtocolAvailability()
        }

        binding.rgDnsProtocol.setOnCheckedChangeListener { _, checkedId ->
            if (updatingFromViewModel) return@setOnCheckedChangeListener
            selectedProtocol = protocolFor(checkedId)
            binding.etCustomDns.setHint(DnsEndpointValidator.endpointHint(selectedProtocol))
        }

        binding.btnCustomDnsDialog.setOnClickListener {
            DnsConfigDialog(
                context = requireContext(),
                initialUrl = binding.etCustomDns.text?.toString().orEmpty(),
                initialName = customName
            ) { url, name ->
                binding.etCustomDns.setText(url)
                customName = name
            }.show()
        }

        binding.btnNetworkSettings.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                .onFailure { error ->
                    if (error is ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.open_network_settings, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.btnSaveDns.setOnClickListener {
            val customUrl = binding.etCustomDns.text?.toString()?.trim().orEmpty()
            val saved = viewModel.saveSettings(
                provider = selectedProvider,
                protocol = selectedProtocol,
                customUrl = customUrl,
                customName = customName
            )
            if (!saved) {
                Toast.makeText(requireContext(), R.string.custom_endpoint_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val message = if (NOichharVpnService.isActive) {
                R.string.dns_settings_reloaded
            } else {
                R.string.dns_settings_saved
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeData() {
        viewModel.dnsProvider.observe(viewLifecycleOwner) { provider ->
            selectedProvider = provider
            updatingFromViewModel = true
            binding.rgDnsProvider.check(providerRadioId(provider))
            updatingFromViewModel = false
            updateCustomVisibility()
            updateProtocolAvailability()
        }

        viewModel.dnsProtocol.observe(viewLifecycleOwner) { protocol ->
            selectedProtocol = protocol
            updatingFromViewModel = true
            binding.rgDnsProtocol.check(protocolRadioId(protocol))
            binding.etCustomDns.setHint(DnsEndpointValidator.endpointHint(protocol))
            updatingFromViewModel = false
        }

        viewModel.customDnsUrl.observe(viewLifecycleOwner) { url ->
            if (binding.etCustomDns.text?.toString() != url) binding.etCustomDns.setText(url)
        }

        viewModel.customDnsName.observe(viewLifecycleOwner) { name ->
            customName = name
        }
    }

    private fun updateCustomVisibility() {
        val visible = selectedProvider == Constants.DnsProvider.CUSTOM
        binding.tilCustomDns.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnCustomDnsDialog.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateProtocolAvailability() {
        setRadioGroupEnabled(binding.rgDnsProtocol, selectedProvider != Constants.DnsProvider.SYSTEM)
    }

    private fun setRadioGroupEnabled(group: RadioGroup, enabled: Boolean) {
        for (index in 0 until group.childCount) {
            group.getChildAt(index).isEnabled = enabled
        }
    }

    private fun updatePrivateDnsWarning() {
        val active = NetworkUtils.isPrivateDnsActive(requireContext())
        binding.cardPrivateDnsWarning.visibility = if (active) View.VISIBLE else View.GONE
    }

    private fun providerFor(id: Int): Constants.DnsProvider = when (id) {
        R.id.rb_system_dns -> Constants.DnsProvider.SYSTEM
        R.id.rb_google -> Constants.DnsProvider.GOOGLE
        R.id.rb_quad9 -> Constants.DnsProvider.QUAD9
        R.id.rb_adguard -> Constants.DnsProvider.ADGUARD
        R.id.rb_custom -> Constants.DnsProvider.CUSTOM
        else -> Constants.DnsProvider.CLOUDFLARE
    }

    private fun protocolFor(id: Int): Constants.DnsProtocol = when (id) {
        R.id.rb_dot -> Constants.DnsProtocol.DOT
        R.id.rb_plain -> Constants.DnsProtocol.PLAIN
        else -> Constants.DnsProtocol.DOH
    }

    private fun providerRadioId(provider: Constants.DnsProvider): Int = when (provider) {
        Constants.DnsProvider.SYSTEM -> R.id.rb_system_dns
        Constants.DnsProvider.GOOGLE -> R.id.rb_google
        Constants.DnsProvider.QUAD9 -> R.id.rb_quad9
        Constants.DnsProvider.ADGUARD -> R.id.rb_adguard
        Constants.DnsProvider.CUSTOM -> R.id.rb_custom
        else -> R.id.rb_cloudflare
    }

    private fun protocolRadioId(protocol: Constants.DnsProtocol): Int = when (protocol) {
        Constants.DnsProtocol.DOT -> R.id.rb_dot
        Constants.DnsProtocol.PLAIN -> R.id.rb_plain
        else -> R.id.rb_doh
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
