package com.example.logging

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogSeverity {
    INFO,
    WARN,
    ERROR
}

enum class LogCategory {
    ENGINE_INIT,
    VPN_LIFECYCLE,
    CONFIG_VALIDATION,
    TUN_INTERFACE,
    NETWORK_STATE,
    ERROR_DIAGNOSTIC
}

data class LogEvent(
    val id: Long,
    val timestamp: Long,
    val category: LogCategory,
    val severity: LogSeverity,
    val component: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * OperationalLogCatcher:
 * Lightweight, strictly bounded diagnostic event catcher for Vianshield.
 *
 * Mandate & Privacy Guardrails:
 * 1. Master On/Off switch - when disabled, no logs are recorded.
 * 2. Strictly Bounded - caps in-memory retention to 500 entries (FIFO) and local disk log to 256KB.
 * 3. Secret Redaction - strips private keys, preshared keys, and credentials before recording.
 * 4. Zero Fabricated Telemetry - captures ONLY genuine observable engine, tunnel, and network events.
 * 5. No PII, browsing history, or packet tracking.
 */
object OperationalLogCatcher {

    private const val MAX_MEMORY_ENTRIES = 500
    private const val MAX_DISK_LOG_BYTES = 256 * 1024L // 256 KB max rotating file
    private const val LOG_FILE_NAME = "vianshield_operational.log"

    private val idCounter = AtomicLong(1L)
    private val _isLoggingEnabled = MutableStateFlow(true)
    val isLoggingEnabled: StateFlow<Boolean> = _isLoggingEnabled.asStateFlow()

    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    private var appContext: Context? = null

    // Base64 44-character key pattern (WireGuard 32-byte key base64 encoded)
    private val base64KeyRegex = Regex("[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=")
    private val privateKeyNamedRegex = Regex("(?i)(private_?key|preshared_?key|password|secret|token)\\s*[:=]\\s*[\"']?[^\"'\\s,]+[\"']?")

    fun initialize(context: Context) {
        appContext = context.applicationContext
        log(
            category = LogCategory.ENGINE_INIT,
            severity = LogSeverity.INFO,
            component = "LogCatcher",
            message = "OperationalLogCatcher initialized with 500-entry memory bound and rotating 256KB disk buffer"
        )
    }

    fun setLoggingEnabled(enabled: Boolean) {
        _isLoggingEnabled.value = enabled
        if (enabled) {
            log(
                category = LogCategory.ENGINE_INIT,
                severity = LogSeverity.INFO,
                component = "LogCatcher",
                message = "Diagnostic logging enabled by master switch"
            )
        }
    }

    fun log(
        category: LogCategory,
        severity: LogSeverity,
        component: String,
        message: String,
        details: String? = null
    ) {
        if (!_isLoggingEnabled.value) return

        val sanitizedMessage = redactSecrets(message)
        val sanitizedDetails = details?.let { redactSecrets(it) }

        val event = LogEvent(
            id = idCounter.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            category = category,
            severity = severity,
            component = component,
            message = sanitizedMessage,
            details = sanitizedDetails
        )

        synchronized(this) {
            val currentList = _events.value
            val updatedList = if (currentList.size >= MAX_MEMORY_ENTRIES) {
                currentList.drop(currentList.size - MAX_MEMORY_ENTRIES + 1) + event
            } else {
                currentList + event
            }
            _events.value = updatedList
        }

        appendToFile(event)
    }

    fun info(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.INFO, component, message, details)
    }

    fun warn(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.WARN, component, message, details)
    }

    fun error(category: LogCategory, component: String, message: String, details: String? = null) {
        log(category, LogSeverity.ERROR, component, message, details)
    }

    fun clear() {
        synchronized(this) {
            _events.value = emptyList()
        }
        appContext?.let { ctx ->
            try {
                val file = File(ctx.filesDir, LOG_FILE_NAME)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
    }

    fun exportFormattedLogs(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val sb = StringBuilder()
        sb.append("=== VIANSHIELD OPERATIONAL DIAGNOSTIC LOG ===\n")
        sb.append("Export Time: ").append(dateFormat.format(Date())).append("\n")
        sb.append("Total In-Memory Entries: ").append(_events.value.size).append("\n")
        sb.append("Master Switch State: ").append(if (_isLoggingEnabled.value) "ENABLED" else "DISABLED").append("\n")
        sb.append("Privacy Policy: Secrets redacted, zero browsing tracking, zero fake telemetry\n\n")

        _events.value.forEach { e ->
            sb.append("[").append(dateFormat.format(Date(e.timestamp))).append("] ")
                .append("[").append(e.severity).append("] ")
                .append("[").append(e.category).append("] ")
                .append("[").append(e.component).append("]: ")
                .append(e.message)
            if (!e.details.isNullOrBlank()) {
                sb.append(" | Details: ").append(e.details)
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Strictly redacts sensitive cryptographic keys, passwords, and tokens.
     */
    fun redactSecrets(input: String): String {
        var result = input
        result = privateKeyNamedRegex.replace(result) { match ->
            val prefix = match.groupValues[1]
            "$prefix=[REDACTED_SECRET]"
        }
        result = base64KeyRegex.replace(result, "[REDACTED_KEY]")
        return result
    }

    private fun appendToFile(event: LogEvent) {
        val ctx = appContext ?: return
        try {
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            if (file.exists() && file.length() > MAX_DISK_LOG_BYTES) {
                // Rotate by trimming top half
                val lines = file.readLines()
                if (lines.size > 200) {
                    file.writeText(lines.takeLast(lines.size / 2).joinToString("\n") + "\n")
                }
            }
            val line = "${event.timestamp}|${event.severity}|${event.category}|${event.component}|${event.message}|${event.details ?: ""}\n"
            file.appendText(line)
        } catch (_: Exception) {
            // Fail silently on disk errors to protect runtime stability
        }
    }
}
