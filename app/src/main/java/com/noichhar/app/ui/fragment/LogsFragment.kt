package com.noichhar.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentLogsBinding
import com.noichhar.app.ui.adapter.DnsLogAdapter
import com.noichhar.app.ui.viewmodel.DnsViewModel

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DnsViewModel by viewModels()
    private lateinit var adapter: DnsLogAdapter

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportLogs(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupToolbar()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = DnsLogAdapter()
        binding.rvLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLogs.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearchLogs.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_clear_logs -> {
                    AlertDialog.Builder(requireContext())
                        .setMessage(R.string.confirm_clear_logs)
                        .setPositiveButton(R.string.yes) { _, _ -> viewModel.clearLogs() }
                        .setNegativeButton(R.string.no, null)
                        .show()
                    true
                }
                R.id.action_export_logs -> {
                    Toast.makeText(requireContext(), R.string.logs_export_started, Toast.LENGTH_SHORT).show()
                    exportLauncher.launch(getString(R.string.logs_export_filename))
                    true
                }
                else -> false
            }
        }
    }

    private fun observeData() {
        viewModel.filteredLogs.observe(viewLifecycleOwner) { logs ->
            adapter.submitList(logs)
            binding.tvNoLogs.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvLogs.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.exportResult.observe(viewLifecycleOwner) { success ->
            if (success == null) return@observe
            Toast.makeText(
                requireContext(),
                if (success) R.string.logs_exported else R.string.logs_export_failed,
                Toast.LENGTH_SHORT
            ).show()
            viewModel.clearExportResult()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
