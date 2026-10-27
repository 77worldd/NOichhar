package com.noichhar.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.R
import com.noichhar.app.database.entity.BlocklistEntry
import com.noichhar.app.database.entity.ConnectionLog
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.util.DomainValidator
import kotlinx.coroutines.launch

data class UiMessage(val stringRes: Int, val argument: Any? = null)

class FirewallViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NOichharApp.instance.database
    private val prefManager = NOichharApp.instance.preferenceManager
    private val blocklistManager = NOichharApp.instance.blocklistManager

    val blocklistCount: LiveData<Int> = database.blocklistDao().getCount()
    val connections: LiveData<List<ConnectionLog>> = database.connectionLogDao().getRecentConnections()
    val userBlocklist: LiveData<List<BlocklistEntry>> = database.blocklistDao().getUserBlockEntries()
    val userAllowlist: LiveData<List<BlocklistEntry>> = database.blocklistDao().getUserAllowEntries()

    private val _operationMessage = MutableLiveData<UiMessage?>(null)
    val operationMessage: LiveData<UiMessage?> = _operationMessage

    var blockUnknown: Boolean
        get() = prefManager.blockUnknown
        set(value) {
            prefManager.blockUnknown = value
            clearCache()
        }

    var blockNewApps: Boolean
        get() = prefManager.blockNewApps
        set(value) {
            prefManager.blockNewApps = value
            clearCache()
        }

    var lockdownMode: Boolean
        get() = prefManager.lockdownMode
        set(value) {
            prefManager.lockdownMode = value
            clearCache()
        }

    fun addCustomBlockDomain(rawDomain: String) {
        viewModelScope.launch {
            val message = if (DomainValidator.normalize(rawDomain) == null) {
                UiMessage(R.string.rule_invalid)
            } else if (blocklistManager.addCustomDomain(rawDomain)) {
                clearCache()
                UiMessage(R.string.rule_added, rawDomain.trim())
            } else {
                UiMessage(R.string.rule_invalid)
            }
            _operationMessage.value = message
        }
    }

    fun addCustomAllowDomain(rawDomain: String, includeSubdomains: Boolean) {
        viewModelScope.launch {
            val message = if (DomainValidator.normalize(rawDomain) == null) {
                UiMessage(R.string.rule_invalid)
            } else if (blocklistManager.addCustomAllowDomain(rawDomain, includeSubdomains)) {
                clearCache()
                UiMessage(R.string.allow_rule_added, rawDomain.trim())
            } else {
                UiMessage(R.string.rule_invalid)
            }
            _operationMessage.value = message
        }
    }

    fun removeCustomBlockDomain(entry: BlocklistEntry) {
        viewModelScope.launch {
            val display = if (entry.isWildcard) "*.${entry.domain}" else entry.domain
            blocklistManager.removeDomain(display)
            clearCache()
            _operationMessage.value = UiMessage(R.string.rule_removed, display)
        }
    }

    fun removeCustomAllowDomain(entry: BlocklistEntry) {
        viewModelScope.launch {
            val display = if (entry.isWildcard) "*.${entry.domain}" else entry.domain
            blocklistManager.removeDomain(display)
            clearCache()
            _operationMessage.value = UiMessage(R.string.allow_rule_removed, display)
        }
    }

    fun updateBlocklists() {
        viewModelScope.launch {
            _operationMessage.value = UiMessage(R.string.blocklist_updating)
            val count = runCatching { blocklistManager.updateBlocklists() }.getOrDefault(0)
            clearCache()
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

    private fun clearCache() {
        NOichharVpnService.instance?.getDnsCache()?.clear()
    }
}
