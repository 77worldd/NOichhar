package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.noichhar.app.NOichharApp
import com.noichhar.app.database.entity.AppInfo
import com.noichhar.app.service.FirewallManager
import com.noichhar.app.service.NOichharVpnService
import kotlinx.coroutines.launch

enum class AppFilter { ALL, BLOCKED, ALLOWED, SYSTEM }

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val appInfoDao = NOichharApp.instance.database.appInfoDao()
    private val firewallManager = FirewallManager(application)

    val allApps: LiveData<List<AppInfo>> = appInfoDao.getAllApps()

    private val _searchQuery = MutableLiveData("")
    private val _filter = MutableLiveData(AppFilter.ALL)

    private val queryAndFilter = MediatorLiveData<Pair<String, AppFilter>>().apply {
        value = "" to AppFilter.ALL
        fun update() {
            value = (_searchQuery.value.orEmpty()) to (_filter.value ?: AppFilter.ALL)
        }
        addSource(_searchQuery) { update() }
        addSource(_filter) { update() }
    }

    val filteredApps: LiveData<List<AppInfo>> = queryAndFilter.switchMap { (query, filter) ->
        val source = if (query.isBlank()) appInfoDao.getAllApps() else appInfoDao.searchApps(query)
        source.map { apps ->
            when (filter) {
                AppFilter.ALL -> apps
                AppFilter.BLOCKED -> apps.filterNot { it.isInternetAllowed }
                AppFilter.ALLOWED -> apps.filter { it.isInternetAllowed }
                AppFilter.SYSTEM -> apps.filter { it.isSystemApp }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            runCatching { firewallManager.refreshAppList() }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppFilter) {
        _filter.value = filter
    }

    fun toggleAppInternet(uid: Int, allowed: Boolean) {
        viewModelScope.launch {
            appInfoDao.setInternetAllowed(uid, allowed)
            clearDnsCache()
        }
    }

    fun setWifiAllowed(uid: Int, allowed: Boolean) {
        viewModelScope.launch {
            appInfoDao.setWifiAllowed(uid, allowed)
            clearDnsCache()
        }
    }

    fun setMobileAllowed(uid: Int, allowed: Boolean) {
        viewModelScope.launch {
            appInfoDao.setMobileAllowed(uid, allowed)
            clearDnsCache()
        }
    }

    fun updateApp(app: AppInfo) {
        viewModelScope.launch {
            appInfoDao.update(app)
            clearDnsCache()
        }
    }

    private fun clearDnsCache() {
        NOichharVpnService.instance?.getDnsCache()?.clear()
    }
}
