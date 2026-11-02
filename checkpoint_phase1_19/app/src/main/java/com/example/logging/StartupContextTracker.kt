package com.example.logging

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * StartupContextTracker:
 * Synchronously persists a lightweight startup checkpoint to app-private storage
 * before risky native and Android framework operations.
 *
 * This allows the application to detect exactly where startup was interrupted
 * if the process experiences an abnormal exit (e.g. SIGSEGV, OS kill).
 *
 * Privacy Guardrail:
 * NEVER persists cryptographic private keys, passwords, or full configurations.
 */
data class StartupContext(
    val lastAttemptedOperation: String = "NONE",
    val lastCompletedOperation: String = "NONE",
    val vpnPermissionGranted: Boolean = false,
    val vpnServiceCreated: Boolean = false,
    val nativeEngineSetupCompleted: Boolean = false,
    val tunCreationCompleted: Boolean = false,
    val engineStartCompleted: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("lastAttemptedOperation", lastAttemptedOperation)
        obj.put("lastCompletedOperation", lastCompletedOperation)
        obj.put("vpnPermissionGranted", vpnPermissionGranted)
        obj.put("vpnServiceCreated", vpnServiceCreated)
        obj.put("nativeEngineSetupCompleted", nativeEngineSetupCompleted)
        obj.put("tunCreationCompleted", tunCreationCompleted)
        obj.put("engineStartCompleted", engineStartCompleted)
        obj.put("timestamp", timestamp)
        return obj.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): StartupContext {
            val obj = JSONObject(jsonStr)
            return StartupContext(
                lastAttemptedOperation = obj.optString("lastAttemptedOperation", "NONE"),
                lastCompletedOperation = obj.optString("lastCompletedOperation", "NONE"),
                vpnPermissionGranted = obj.optBoolean("vpnPermissionGranted", false),
                vpnServiceCreated = obj.optBoolean("vpnServiceCreated", false),
                nativeEngineSetupCompleted = obj.optBoolean("nativeEngineSetupCompleted", false),
                tunCreationCompleted = obj.optBoolean("tunCreationCompleted", false),
                engineStartCompleted = obj.optBoolean("engineStartCompleted", false),
                timestamp = obj.optLong("timestamp", 0L)
            )
        }
    }
}

object StartupContextTracker {

    private const val CONTEXT_FILE_NAME = "startup_context.json"
    private var currentContext = StartupContext()
    private var appContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        loadPersistedContext()
    }

    @Synchronized
    fun checkpointAttempt(
        operation: String,
        vpnPermissionGranted: Boolean? = null,
        vpnServiceCreated: Boolean? = null,
        nativeEngineSetupCompleted: Boolean? = null,
        tunCreationCompleted: Boolean? = null,
        engineStartCompleted: Boolean? = null
    ) {
        currentContext = currentContext.copy(
            lastAttemptedOperation = operation,
            vpnPermissionGranted = vpnPermissionGranted ?: currentContext.vpnPermissionGranted,
            vpnServiceCreated = vpnServiceCreated ?: currentContext.vpnServiceCreated,
            nativeEngineSetupCompleted = nativeEngineSetupCompleted ?: currentContext.nativeEngineSetupCompleted,
            tunCreationCompleted = tunCreationCompleted ?: currentContext.tunCreationCompleted,
            engineStartCompleted = engineStartCompleted ?: currentContext.engineStartCompleted,
            timestamp = System.currentTimeMillis()
        )
        persist()
    }

    @Synchronized
    fun checkpointSuccess(
        operation: String,
        vpnPermissionGranted: Boolean? = null,
        vpnServiceCreated: Boolean? = null,
        nativeEngineSetupCompleted: Boolean? = null,
        tunCreationCompleted: Boolean? = null,
        engineStartCompleted: Boolean? = null
    ) {
        currentContext = currentContext.copy(
            lastCompletedOperation = operation,
            vpnPermissionGranted = vpnPermissionGranted ?: currentContext.vpnPermissionGranted,
            vpnServiceCreated = vpnServiceCreated ?: currentContext.vpnServiceCreated,
            nativeEngineSetupCompleted = nativeEngineSetupCompleted ?: currentContext.nativeEngineSetupCompleted,
            tunCreationCompleted = tunCreationCompleted ?: currentContext.tunCreationCompleted,
            engineStartCompleted = engineStartCompleted ?: currentContext.engineStartCompleted,
            timestamp = System.currentTimeMillis()
        )
        persist()
    }

    @Synchronized
    fun resetOnCleanStop() {
        currentContext = StartupContext(
            lastAttemptedOperation = "CLEAN_STOPPED",
            lastCompletedOperation = "CLEAN_STOPPED",
            vpnPermissionGranted = currentContext.vpnPermissionGranted,
            vpnServiceCreated = false,
            nativeEngineSetupCompleted = false,
            tunCreationCompleted = false,
            engineStartCompleted = false,
            timestamp = System.currentTimeMillis()
        )
        persist()
    }

    @Synchronized
    fun getContext(): StartupContext = currentContext

    private fun persist() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, CONTEXT_FILE_NAME)
            file.writeText(currentContext.toJson(), Charsets.UTF_8)
        } catch (_: Throwable) {
            // Never crash during diagnostic persistence
        }
    }

    private fun loadPersistedContext() {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, CONTEXT_FILE_NAME)
            if (file.exists() && file.length() > 0) {
                currentContext = StartupContext.fromJson(file.readText(Charsets.UTF_8))
            }
        } catch (_: Throwable) {
            currentContext = StartupContext()
        }
    }
}
