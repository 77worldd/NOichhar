package com.noichhar.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lifecycle of the local tunnel as seen by the UI.
 *
 * A single process-wide [StateFlow] keeps the home screen in sync with the
 * service on the same frame the state changes: no broadcast round-trip, no
 * waiting for the next resume, so the button never appears "stuck" on
 * ACTIVATE while the tunnel is already coming up.
 */
enum class VpnConnectionState { INACTIVE, CONNECTING, ACTIVE }

object VpnStateStore {

    private val _state = MutableStateFlow(VpnConnectionState.INACTIVE)
    val state: StateFlow<VpnConnectionState> = _state.asStateFlow()

    val current: VpnConnectionState
        get() = _state.value

    fun set(value: VpnConnectionState) {
        if (_state.value != value) _state.value = value
    }
}
