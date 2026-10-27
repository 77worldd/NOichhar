package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.service.NOichharVpnService
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NOichharApp.instance.database
    private val preferenceManager = NOichharApp.instance.preferenceManager
    private val blocklistManager = NOichharApp.instance.blocklistManager
    private val dnsLogDao = database.dnsLogDao()

    val blockedCount: LiveData<Long> = dnsLogDao.getBlockedCount()
    val allowedCount: LiveData<Long> = dnsLogDao.getAllowedCount()
    val totalCount: LiveData<Long> = dnsLogDao.getTotalCount()
    val blocklistCount: LiveData<Int> = database.blocklistDao().getCount()
    val appCount: LiveData<Int> = database.appInfoDao().getCountLive()

    private val _vpnActive = MutableLiveData(false)
    val vpnActive: LiveData<Boolean> = _vpnActive

    private val _advancedProtection = MutableLiveData(preferenceManager.advancedProtection)
    val advancedProtection: LiveData<Boolean> = _advancedProtection

    private val _operationMessage = MutableLiveData<UiMessage?>(null)
    val operationMessage: LiveData<UiMessage?> = _operationMessage

    fun refreshVpnStatus() {
        _vpnActive.value = NOichharVpnService.isActive
        _advancedProtection.value = preferenceManager.advancedProtection
    }

    fun setVpnActive(active: Boolean) {
        _vpnActive.value = active
    }

    fun setAdvancedProtection(enabled: Boolean) {
        if (preferenceManager.advancedProtection == enabled) {
            _advancedProtection.value = enabled
            return
        }
        preferenceManager.advancedProtection = enabled
        _advancedProtection.value = enabled
        NOichharVpnService.instance?.refreshProtection()
    }

    fun updateBlocklists() {
        viewModelScope.launch {
            _operationMessage.value = UiMessage(R.string.blocklist_updating)
            val count = runCatching { blocklistManager.updateBlocklists() }.getOrDefault(0)
            NOichharVpnService.instance?.refreshProtection()
            _operationMessage.value = if (count > 0) {
                UiMessage(R.string.blocklist_updated_count, count)
            } else {
                UiMessage(R.string.blocklist_update_failed)
            }
        }
    }

    fun clearOperationMessage() {
        _operationMessage.value = null
    }

    fun getDnsSummary(): String = preferenceManager.getDnsSummary()
}
