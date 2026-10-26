package com.noichhar.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.noichhar.app.NOichharApp
import com.noichhar.app.database.entity.DnsLog
import com.noichhar.app.service.ConnectionTracker
import com.noichhar.app.service.NOichharVpnService
import com.noichhar.app.util.Constants
import com.noichhar.app.util.DnsEndpointValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DnsViewModel(application: Application) : AndroidViewModel(application) {

    private val database = NOichharApp.instance.database
    private val prefManager = NOichharApp.instance.preferenceManager

    private val _dnsProvider = MutableLiveData(prefManager.dnsProvider)
    val dnsProvider: LiveData<Constants.DnsProvider> = _dnsProvider

    private val _dnsProtocol = MutableLiveData(prefManager.dnsProtocol)
    val dnsProtocol: LiveData<Constants.DnsProtocol> = _dnsProtocol

    private val _customDnsUrl = MutableLiveData(prefManager.customDnsUrl)
    val customDnsUrl: LiveData<String> = _customDnsUrl

    private val _customDnsName = MutableLiveData(prefManager.customDnsName)
    val customDnsName: LiveData<String> = _customDnsName

    private val _searchQuery = MutableLiveData("")
    val filteredLogs: LiveData<List<DnsLog>> = _searchQuery.switchMap { query: String ->
        if (query.isBlank()) database.dnsLogDao().getRecentLogs() else database.dnsLogDao().searchLogs(query)
    }

    private val _exportResult = MutableLiveData<Boolean?>(null)
    val exportResult: LiveData<Boolean?> = _exportResult

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun saveSettings(
        provider: Constants.DnsProvider,
        protocol: Constants.DnsProtocol,
        customUrl: String,
        customName: String
    ): Boolean {
        if (!DnsEndpointValidator.isValid(provider, protocol, customUrl)) return false
        prefManager.saveDnsSettings(provider, protocol, customUrl, customName)
        _dnsProvider.value = provider
        _dnsProtocol.value = protocol
        _customDnsUrl.value = customUrl.trim()
        _customDnsName.value = customName.trim()

        NOichharVpnService.instance?.let { service ->
            service.getDnsCache().clear()
            service.reloadConfiguration()
        }
        return true
    }

    fun clearLogs() {
        viewModelScope.launch {
            database.dnsLogDao().deleteAll()
            ConnectionTracker().clearLogs()
        }
    }

    fun exportLogs(uri: Uri) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val logs = database.dnsLogDao().getAllLogsSync()
                    getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { stream ->
                        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        stream.bufferedWriter().use { writer ->
                            writer.write("timestamp,domain,type,status,reason,response_ip,response_ms,app_uid,app_name\n")
                            logs.forEach { log ->
                                writer.write(
                                    listOf(
                                        formatter.format(Date(log.timestamp)),
                                        log.domain,
                                        log.queryType,
                                        if (log.isBlocked) "blocked" else "allowed",
                                        log.blockReason.orEmpty(),
                                        log.responseIp.orEmpty(),
                                        log.responseTimeMs.toString(),
                                        log.appUid?.toString().orEmpty(),
                                        log.appName.orEmpty()
                                    ).joinToString(",") { csv(it) } + "\n"
                                )
                            }
                        }
                    } ?: return@runCatching false
                    true
                }.getOrDefault(false)
            }
            _exportResult.value = success
        }
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
