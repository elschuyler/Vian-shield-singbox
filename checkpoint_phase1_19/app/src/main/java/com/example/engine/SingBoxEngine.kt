package com.example.engine

import java.io.File

/**
 * SingBoxPlatformCallback:
 * Matches the native libbox PlatformInterface contract required by sing-box.
 */
interface SingBoxPlatformCallback {
    /**
     * Called by sing-box when the TUN inbound starts.
     * Android layer must establish the TUN interface and return the detached file descriptor (int).
     */
    fun openTun(options: Any?): Int

    /**
     * Called by sing-box to exempt outbound sockets (e.g. WireGuard UDP transport) from the VPN tunnel.
     * Android layer must invoke VpnService.protect(fd).
     */
    fun autoDetectInterfaceControl(fd: Int): Boolean

    /**
     * Diagnostic logging callback from native sing-box core.
     */
    fun onLog(level: String, message: String)

    /**
     * Structured error notification from native engine.
     */
    fun onError(code: String, message: String)
}

/**
 * SingBoxEngine:
 * Structured interface following official sing-box-for-android (SFA) / BitProxy lifecycle.
 */
interface SingBoxEngine {
    /**
     * Bootstraps the libbox runtime sandbox directories (baseDir, workingDir, tempDir).
     */
    fun setupEnvironment(baseDir: File, workingDir: File, tempDir: File): Result<Unit>

    /**
     * Validates configuration syntax and schema via Libbox.checkConfig before starting.
     */
    fun checkConfig(configJson: String): Result<Unit>

    /**
     * Instantiates the core BoxService with platform callbacks.
     */
    fun initialize(configJson: String, platformCallback: SingBoxPlatformCallback): Result<Unit>

    /**
     * Starts the BoxService with watchdog timeout protection.
     */
    fun start(): Result<Unit>

    /**
     * Stops BoxService and releases native resources cleanly.
     */
    fun stop(): Result<Unit>

    /**
     * Hot-reloads configuration if supported, or performs safe rollback.
     */
    fun reload(newConfigJson: String): Result<Unit>

    fun isRunning(): Boolean
    fun isNativeBinaryAvailable(): Boolean
    fun getEngineVersion(): String
}

