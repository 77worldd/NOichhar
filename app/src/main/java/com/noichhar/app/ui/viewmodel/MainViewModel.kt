package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.noichhar.app.NOichharApp
import com.noichhar.app.service.NOichharVpnService

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NOichharApp.instance.database
    private val dnsLogDao = database.dnsLogDao()

    val blockedCount: LiveData<Long> = dnsLogDao.getBlockedCount()
    val allowedCount: LiveData<Long> = dnsLogDao.getAllowedCount()
    val totalCount: LiveData<Long> = dnsLogDao.getTotalCount()
    val blocklistCount: LiveData<Int> = database.blocklistDao().getCount()
    val appCount: LiveData<Int> = database.appInfoDao().getCountLive()

    private val _vpnActive = MutableLiveData(false)
    val vpnActive: LiveData<Boolean> = _vpnActive

    fun refreshVpnStatus() {
        _vpnActive.value = NOichharVpnService.isActive
    }

    fun setVpnActive(active: Boolean) {
        _vpnActive.value = active
    }

    fun getDnsSummary(): String = NOichharApp.instance.preferenceManager.getDnsSummary()
}
