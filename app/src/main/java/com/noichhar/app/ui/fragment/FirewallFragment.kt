package com.noichhar.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentFirewallBinding
import com.noichhar.app.ui.adapter.BlocklistAdapter
import com.noichhar.app.ui.adapter.ConnectionAdapter
import com.noichhar.app.ui.viewmodel.FirewallViewModel

class FirewallFragment : Fragment() {

    private var _binding: FragmentFirewallBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FirewallViewModel by viewModels()
    private lateinit var connectionAdapter: ConnectionAdapter
    private lateinit var blocklistAdapter: BlocklistAdapter
    private lateinit var allowlistAdapter: BlocklistAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirewallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupRecyclerViews()
        observeData()
    }

    private fun setupUI() {
        binding.switchBlockUnknown.isChecked = viewModel.blockUnknown
        binding.switchBlockUnknown.setOnCheckedChangeListener { _, isChecked ->
            viewModel.blockUnknown = isChecked
        }

        binding.switchBlockNewApps.isChecked = viewModel.blockNewApps
        binding.switchBlockNewApps.setOnCheckedChangeListener { _, isChecked ->
            viewModel.blockNewApps = isChecked
        }

        binding.switchLockdown.isChecked = viewModel.lockdownMode
        binding.switchLockdown.setOnCheckedChangeListener { _, isChecked ->
            viewModel.lockdownMode = isChecked
        }

        binding.btnUpdateBlocklist.setOnClickListener { viewModel.updateBlocklists() }
        binding.btnAddCustomDomain.setOnClickListener { showAddBlockDomainDialog() }
        binding.btnAddAllowDomain.setOnClickListener { showAddAllowDomainDialog() }
    }

    private fun setupRecyclerViews() {
        blocklistAdapter = BlocklistAdapter(allowMode = false) { entry ->
            viewModel.removeCustomBlockDomain(entry)
        }
        binding.rvUserRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUserRules.adapter = blocklistAdapter

        allowlistAdapter = BlocklistAdapter(allowMode = true) { entry ->
            viewModel.removeCustomAllowDomain(entry)
        }
        binding.rvAllowRules.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAllowRules.adapter = allowlistAdapter

        connectionAdapter = ConnectionAdapter()
        binding.rvConnections.layoutManager = LinearLayoutManager(requireContext())
        binding.rvConnections.adapter = connectionAdapter
    }

    private fun observeData() {
        viewModel.blocklistCount.observe(viewLifecycleOwner) { count ->
            binding.tvBlocklistCount.text = getString(R.string.blocklist_count, count)
        }

        viewModel.userBlocklist.observe(viewLifecycleOwner) { rules ->
            blocklistAdapter.submitList(rules)
            binding.rvUserRules.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
            binding.tvNoCustomRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.userAllowlist.observe(viewLifecycleOwner) { rules ->
            allowlistAdapter.submitList(rules)
            binding.rvAllowRules.visibility = if (rules.isEmpty()) View.GONE else View.VISIBLE
            binding.tvNoAllowRules.visibility = if (rules.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.connections.observe(viewLifecycleOwner) { connections ->
            connectionAdapter.submitList(connections)
            binding.rvConnections.visibility = if (connections.isEmpty()) View.GONE else View.VISIBLE
            binding.tvNoConnections.visibility = if (connections.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.operationMessage.observe(viewLifecycleOwner) { message ->
            if (message == null) return@observe
            val text = message.argument?.let { getString(message.stringRes, it) }
                ?: getString(message.stringRes)
            Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
            viewModel.clearOperationMessage()
        }
    }

    private fun showAddBlockDomainDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.domain_rule_hint)
            setPadding(48, 32, 48, 32)
            maxLines = 1
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_block_rule)
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val domain = editText.text.toString().trim()
                if (domain.isNotEmpty()) viewModel.addCustomBlockDomain(domain)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddAllowDomainDialog() {
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.domain_rule_hint)
            maxLines = 1
        }
        val checkBox = CheckBox(requireContext()).apply {
            text = getString(R.string.include_subdomains)
            isChecked = true
        }
        container.addView(editText)
        container.addView(checkBox)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_allow_rule)
            .setMessage(R.string.allowlist_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val domain = editText.text.toString().trim()
                if (domain.isNotEmpty()) {
                    viewModel.addCustomAllowDomain(domain, checkBox.isChecked)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
