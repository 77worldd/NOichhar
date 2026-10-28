package com.noichhar.app.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.databinding.FragmentStatsBinding
import com.noichhar.app.database.dao.TrafficTotals
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.stats.TimeWindow
import com.noichhar.app.ui.adapter.MostAllowedAdapter
import com.noichhar.app.ui.adapter.TopConnectionsAdapter
import com.noichhar.app.ui.viewmodel.StatsViewModel
import com.noichhar.app.ui.widget.ChartSegment
import com.noichhar.app.ui.widget.SegmentedTimeSelector
import com.noichhar.app.util.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val b get() = _binding!!
    private val vm: StatsViewModel by viewModels()
    private val topAdapter = TopConnectionsAdapter()
    private val trafficAdapter = MostAllowedAdapter()
    private var access = false
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false); return b.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        b.rvTopConnections.layoutManager = LinearLayoutManager(requireContext()); b.rvTopConnections.adapter = topAdapter
        b.rvMostAllowed.layoutManager = LinearLayoutManager(requireContext()); b.rvMostAllowed.adapter = trafficAdapter
        b.timeSelector.select(when (vm.window) {
            TimeWindow.HOUR_1 -> SegmentedTimeSelector.Window.H1
            TimeWindow.HOUR_24 -> SegmentedTimeSelector.Window.H24
            TimeWindow.DAY_7 -> SegmentedTimeSelector.Window.D7
        })
        b.timeSelector.onSelectionChanged = { vm.selectWindow(when (it) {
            SegmentedTimeSelector.Window.H1 -> TimeWindow.HOUR_1
            SegmentedTimeSelector.Window.H24 -> TimeWindow.HOUR_24
            SegmentedTimeSelector.Window.D7 -> TimeWindow.DAY_7
        }) }
        b.btnUsageAccess.setOnClickListener { openUsageAccess() }
        b.btnCloseAll.setOnClickListener {
            val cache = NOichharVpnService.instance?.getDnsCache()
            if (cache == null || !NOichharVpnService.isActive)
                Toast.makeText(requireContext(), "Start protection to clear its DNS cache", Toast.LENGTH_SHORT).show()
            else { cache.clear(); Toast.makeText(requireContext(), "DNS cache cleared", Toast.LENGTH_SHORT).show() }
        }
        vm.totals.observe(viewLifecycleOwner) { totals -> renderTotals(totals) }
        vm.topConnections.observe(viewLifecycleOwner) { list ->
            topAdapter.submitList(list)
            b.tvNoRequests.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            val palette = listOf(R.color.chart_segment_1, R.color.chart_segment_2, R.color.chart_segment_3,
                R.color.chart_segment_4, R.color.chart_segment_5, R.color.chart_segment_6)
                .map { ContextCompat.getColor(requireContext(), it) }
            val segments = list.mapIndexed { i, stat -> ChartSegment(stat.totalConn.toFloat(), palette[i % palette.size], stat.appName) }
            b.radialChart.setData(segments, FormatUtils.formatCount(list.sumOf { it.totalConn }), "TOP APPS")
        }
        vm.topTraffic.observe(viewLifecycleOwner) { list ->
            trafficAdapter.submitList(if (access) list else emptyList())
            b.tvNoBandwidth.visibility = if (!access || list.isEmpty()) View.VISIBLE else View.GONE
            b.tvNoBandwidth.text = if (!access) "Grant Usage Access to see per-app bandwidth." else "No usage samples in this period."
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    NOichharApp.instance.trafficTracker.coverage.collect { renderCoverage() }
                }
                launch { while (isActive) { renderCoverage(); vm.refresh(); delay(5_000) } }
            }
        }
    }
    override fun onResume() { super.onResume(); renderCoverage(); vm.refresh() }
    override fun onStart() {
        super.onStart()
        NOichharApp.instance.trafficTracker.onUiVisible()
    }
    override fun onStop() {
        NOichharApp.instance.trafficTracker.onUiHidden()
        super.onStop()
    }
    private fun renderCoverage() {
        if (_binding == null) return
        val tracker = NOichharApp.instance.trafficTracker
        access = tracker.hasUsageStatsPermission()
        val coverage = tracker.coverage.value
        b.btnUsageAccess.visibility = if (access) View.GONE else View.VISIBLE
        b.tvUsageNote.text = when {
            !access -> "Grant Usage Access for device-wide app bandwidth. Request counts work without it."
            !NOichharVpnService.isActive -> "Device-wide usage is sampled while protection is active. Android may report usage with a delay."
            !coverage.wifiAvailable && !coverage.mobileAvailable -> "Waiting for Android network-usage data. Samples may be delayed."
            !coverage.mobileAvailable -> "* Totals are partial: mobile data is unavailable. Android may delay samples."
            !coverage.wifiAvailable -> "* Totals are partial: Wi-Fi data is unavailable. Android may delay samples."
            else -> "Device-wide usage sampled while protection is active. Android may delay samples; older devices estimate metering by transport."
        }
        renderTotals(vm.totals.value)
        if (!access) trafficAdapter.submitList(emptyList())
    }
    private fun renderTotals(totals: TrafficTotals?) {
        if (_binding == null) return
        val coverage = NOichharApp.instance.trafficTracker.coverage.value
        val partial = NOichharVpnService.isActive && coverage.usageAccess &&
            (coverage.wifiAvailable != coverage.mobileAvailable)
        val unavailable = NOichharVpnService.isActive && coverage.usageAccess &&
            !coverage.wifiAvailable && !coverage.mobileAvailable
        fun display(value: Long): String {
            if (!access || (unavailable && value == 0L)) return "—"
            return FormatUtils.formatBytes(value) + if (partial) "*" else ""
        }
        b.tvUnmetered.text = totals?.let { display(it.unmetered) } ?: "—"
        b.tvMetered.text = totals?.let { display(it.metered) } ?: "—"
        b.tvOverall.text = totals?.let { display(it.overall) } ?: "—"
    }
    private fun openUsageAccess() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { Toast.makeText(requireContext(), "Usage Access settings are unavailable", Toast.LENGTH_SHORT).show() }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
