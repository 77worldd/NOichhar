package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.noichhar.app.database.dao.AppTrafficSummary
import com.noichhar.app.database.dao.TrafficTotals
import com.noichhar.app.stats.BandwidthAggregator
import com.noichhar.app.stats.TimeWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val aggregator = BandwidthAggregator()
    private var refreshJob: Job? = null
    var window: TimeWindow = TimeWindow.HOUR_1
        private set
    private val _totals = MutableLiveData<TrafficTotals>()
    val totals: LiveData<TrafficTotals> = _totals
    private val _topConnections = MutableLiveData<List<AppTrafficSummary>>(emptyList())
    val topConnections: LiveData<List<AppTrafficSummary>> = _topConnections
    private val _topTraffic = MutableLiveData<List<AppTrafficSummary>>(emptyList())
    val topTraffic: LiveData<List<AppTrafficSummary>> = _topTraffic

    fun selectWindow(value: TimeWindow) { window = value; refresh() }
    fun refresh() {
        refreshJob?.cancel()
        val selected = window
        refreshJob = viewModelScope.launch {
            val totals = aggregator.totalsFor(selected)
            val requests = aggregator.topConnections(selected)
            val traffic = aggregator.topTrafficApps(selected)
            _totals.value = totals; _topConnections.value = requests; _topTraffic.value = traffic
        }
    }
}
