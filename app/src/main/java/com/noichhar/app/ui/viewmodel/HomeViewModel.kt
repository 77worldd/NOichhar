package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.stats.TimeWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = NOichharApp.instance.database.trafficStatDao()
    val activity = dao.observeActivity(TimeWindow.DAY_7.sinceMillis())
    val heatmap = MutableLiveData<IntArray>()

    fun refreshHeatmap() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val until = System.currentTimeMillis()
                val since = until - 24 * 3_600_000L
                val values = IntArray(120)
                dao.activityBuckets(since, until, 12 * 60_000L).forEach { row ->
                    if (row.bucket in values.indices) values[row.bucket] = row.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                values
            }
            heatmap.value = data
        }
    }
}
