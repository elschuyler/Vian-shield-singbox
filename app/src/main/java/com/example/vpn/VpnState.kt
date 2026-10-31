package com.example.vpn

/**
 * VpnState: Distinct, truthful states for VPN lifecycle.
 * - STOPPED
 * - START_REQUESTED
 * - PERMISSION_REQUIRED
 * - STARTING
 * - RUNNING (Connected)
 * - STOPPING
 * - FAILED (Error)
 */
sealed class VpnState {
    data object Stopped : VpnState()
    data object StartRequested : VpnState()
    data object PermissionRequired : VpnState()
    data class Starting(val stage: String) : VpnState()

    open class Connected(
        val endpoint: String,
        val localAddress: String,
        val mtu: Int,
        val connectedSince: Long = System.currentTimeMillis()
    ) : VpnState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Connected) return false
            return endpoint == other.endpoint && localAddress == other.localAddress && mtu == other.mtu
        }
        override fun hashCode(): Int = endpoint.hashCode() * 31 + localAddress.hashCode()
        override fun toString(): String = "Connected(endpoint='$endpoint', localAddress='$localAddress', mtu=$mtu)"
    }

    class Running(
        endpoint: String,
        localAddress: String,
        mtu: Int,
        connectedSince: Long = System.currentTimeMillis()
    ) : Connected(endpoint, localAddress, mtu, connectedSince)

    data object Stopping : VpnState()

    open class Failed(
        val errorCode: String,
        val message: String,
        val details: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) : VpnState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Failed) return false
            return errorCode == other.errorCode && message == other.message
        }
        override fun hashCode(): Int = errorCode.hashCode() * 31 + message.hashCode()
        override fun toString(): String = "Failed(errorCode='$errorCode', message='$message', details=$details)"
    }

    class Error(
        errorCode: String,
        message: String,
        timestamp: Long = System.currentTimeMillis(),
        details: String? = null
    ) : Failed(errorCode, message, details, timestamp)
}

