package com.noichhar.app.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.noichhar.app.R
import com.noichhar.app.database.entity.AppInfo
import com.noichhar.app.databinding.FragmentAppsBinding
import com.noichhar.app.ui.adapter.AppListAdapter
import com.noichhar.app.ui.dialog.AppDetailDialog
import com.noichhar.app.ui.viewmodel.AppFilter
import com.noichhar.app.ui.viewmodel.AppViewModel

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppViewModel by viewModels()
    private lateinit var adapter: AppListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSearch()
        setupFilters()
        observeData()
        viewModel.refreshApps()
    }

    private fun setupRecyclerView() {
        adapter = AppListAdapter(
            onAppToggled = { app, allowed -> viewModel.toggleAppInternet(app.uid, allowed) },
            onWifiToggled = { app, allowed -> viewModel.setWifiAllowed(app.uid, allowed) },
            onMobileToggled = { app, allowed -> viewModel.setMobileAllowed(app.uid, allowed) },
            onAppClicked = { app -> showAppDetail(app) }
        )
        binding.rvApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvApps.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            viewModel.setSearchQuery(text?.toString().orEmpty())
        }
    }

    private fun setupFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_blocked -> AppFilter.BLOCKED
                R.id.chip_allowed -> AppFilter.ALLOWED
                R.id.chip_system -> AppFilter.SYSTEM
                else -> AppFilter.ALL
            }
            viewModel.setFilter(filter)
        }
    }

    private fun observeData() {
        viewModel.filteredApps.observe(viewLifecycleOwner) { apps ->
            adapter.submitList(apps)
            binding.tvNoApps.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            binding.rvApps.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showAppDetail(app: AppInfo) {
        AppDetailDialog(requireContext(), app) { updatedApp ->
            viewModel.updateApp(updatedApp)
        }.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
