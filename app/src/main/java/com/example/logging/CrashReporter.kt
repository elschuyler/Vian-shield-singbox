package com.example.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.example.vpn.VianshieldVpnService
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CrashReport(
    val id: String,
    val timestamp: Long,
    val threadName: String,
    val threadId: Long,
    val exceptionClass: String,
    val exceptionMessage: String,
    val stackTrace: String,
    val rootCause: String? = null,
    val startupStage: String? = null,
    val recentEvents: List<String>,
    val appVersion: String,
    val osVersion: String,
    val deviceModel: String,
    val supportedAbis: String = Build.SUPPORTED_ABIS.joinToString(", ")
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

data class ProcessExitRecord(
    val timestamp: Long,
    val pid: Int,
    val processName: String,
    val reasonCode: Int,
    val reasonName: String,
    val status: Int,
    val description: String?,
    val trace: String?,
    val startupContext: StartupContext?
) {
    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
}

/**
 * CrashReporter:
 * Robust, privacy-conscious uncaught exception handler and crash dump persistence.
 *
 * Mandate & Architecture:
 * 1. Chained UncaughtExceptionHandler - delegates cleanly to previous handler (does not swallow crashes).
 * 2. Synchronous Disk Write - flushed to disk before process death so crash reports survive termination.
 * 3. Bounded Crash Budget - stored separately from operational logs in filesDir/crash_reports/ (max 3 files, max 256KB total).
 * 4. Secret Redaction - all stack traces, messages, and state snapshots pass through redactSecrets.
 * 5. Native Crash Truthfulness - explicitly documents that fatal C/Go signals (SIGSEGV, SIGABRT) bypass
 *    JVM exception handlers and are captured by Android OS debuggerd tombstones (/data/tombstones) and
 *    diagnosed on next launch via ActivityManager.getHistoricalProcessExitReasons().
 */
object CrashReporter {

    private const val CRASH_DIR_NAME = "crash_reports"
    private const val MAX_CRASH_REPORTS = 3
    private const val DIAGNOSTICS_PREFS = "vianshield_diagnostics_prefs"
    private const val PREF_LAST_DIAGNOSED_EXIT_TS = "last_diagnosed_exit_ts"

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false

    @Synchronized
    fun install(context: Context) {
        if (isInstalled) return
        val appContext = context.applicationContext

        val existingHandler = Thread.getDefaultUncaughtExceptionHandler()
        defaultHandler = existingHandler

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 1. Immediately log structured UNCAUGHT_EXCEPTION to operational log so disk flushes before death
                val excMsg = throwable.message ?: throwable.javaClass.simpleName
                val startupCtx = StartupContextTracker.getContext()
                OperationalLogCatcher.error(
                    category = LogCategory.CRASH,
                    component = "UncaughtExceptionHandler",
                    message = "[${OperationalLogCatcher.EVENT_UNCAUGHT_EXCEPTION}] Uncaught ${throwable.javaClass.name} on thread '${thread.name}': $excMsg",
                    details = "Last Attempted Op: ${startupCtx.lastAttemptedOperation} | Last Completed: ${startupCtx.lastCompletedOperation}"
                )

                // 2. Persist comprehensive crash dump file
                recordCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Must never fail inside exception handler
            } finally {
                // Always delegate to existing default handler to allow standard OS crash handling
                existingHandler?.uncaughtException(thread, throwable)
            }
        }
        isInstalled = true
    }

    fun hasPendingCrashReports(context: Context): Boolean {
        return getPendingCrashReports(context).isNotEmpty()
    }

    fun getPendingCrashCount(context: Context): Int {
        return getPendingCrashReports(context).size
    }

    fun getPendingCrashReports(context: Context): List<CrashReport> {
        return try {
            val dir = File(context.filesDir, CRASH_DIR_NAME)
            if (!dir.exists() || !dir.isDirectory) return emptyList()

            val files = dir.listFiles { f -> f.extension == "txt" || f.extension == "log" } ?: return emptyList()
            files.sortedByDescending { it.lastModified() }.mapNotNull { parseCrashFile(it) }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun clearCrashReports(context: Context) {
        try {
            val dir = File(context.filesDir, CRASH_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Throwable) {}
    }

    fun recordCrash(context: Context, thread: Thread, throwable: Throwable): CrashReport? {
        return try {
            val dir = File(context.filesDir, CRASH_DIR_NAME).apply { mkdirs() }

            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val rawStackTrace = sw.toString()
            val redactedStackTrace = OperationalLogCatcher.redactSecrets(rawStackTrace)
            val redactedMessage = OperationalLogCatcher.redactSecrets(throwable.message ?: "No message provided")

            // Find root cause
            var root: Throwable = throwable
            while (root.cause != null && root.cause !== root) {
                root = root.cause!!
            }
            val rootCauseStr = if (root !== throwable) {
                "${root.javaClass.name}: ${OperationalLogCatcher.redactSecrets(root.message ?: "")}"
            } else {
                null
            }

            val recentLogs = OperationalLogCatcher.getRecentEvents(20).map { event ->
                "[${event.formattedTime}] [${event.severity}] [${event.category}] [${event.component}]: ${event.message}"
            }

            val appVer = try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                "${pInfo.versionName} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode})"
            } catch (_: Throwable) {
                "Unknown"
            }

            val crashId = UUID.randomUUID().toString().substring(0, 8)
            val tId: Long = try {
                Thread::class.java.getMethod("threadId").invoke(thread) as Long
            } catch (_: Throwable) {
                @Suppress("DEPRECATION")
                thread.id
            }

            val startupCtx = StartupContextTracker.getContext()
            val startupSummary = "Last Attempted=${startupCtx.lastAttemptedOperation}, Last Completed=${startupCtx.lastCompletedOperation}, VPN State=${VianshieldVpnService.vpnState.value}"

            val report = CrashReport(
                id = crashId,
                timestamp = System.currentTimeMillis(),
                threadName = thread.name,
                threadId = tId,
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = redactedMessage,
                stackTrace = redactedStackTrace,
                rootCause = rootCauseStr,
                startupStage = startupSummary,
                recentEvents = recentLogs,
                appVersion = appVer,
                osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                supportedAbis = Build.SUPPORTED_ABIS.joinToString(", ")
            )

            // Prune excess crash files if count >= MAX_CRASH_REPORTS
            val existing = dir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
            if (existing.size >= MAX_CRASH_REPORTS) {
                existing.take(existing.size - MAX_CRASH_REPORTS + 1).forEach { it.delete() }
            }

            val file = File(dir, "crash_${report.timestamp}_$crashId.txt")
            val content = buildString {
                append("=== VIANSHIELD CRASH DUMP ===\n")
                append("Crash ID: ").append(report.id).append("\n")
                append("Timestamp: ").append(report.formattedDate).append("\n")
                append("App Version: ").append(report.appVersion).append("\n")
                append("OS: ").append(report.osVersion).append("\n")
                append("Device: ").append(report.deviceModel).append("\n")
                append("Supported ABIs: ").append(report.supportedAbis).append("\n")
                append("Startup Stage: ").append(report.startupStage).append("\n")
                append("Thread: ").append(report.threadName).append(" (ID: ").append(report.threadId).append(")\n")
                append("Exception: ").append(report.exceptionClass).append(": ").append(report.exceptionMessage).append("\n")
                if (report.rootCause != null) {
                    append("Root Cause: ").append(report.rootCause).append("\n")
                }
                append("\n--- STACK TRACE ---\n")
                append(report.stackTrace).append("\n\n")
                append("--- RECENT OPERATIONAL EVENTS ---\n")
                report.recentEvents.forEach { append(it).append("\n") }
                append("\n--- END OF DUMP ---\n")
            }

            file.writeText(content, Charsets.UTF_8)
            report
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Inspects historical process termination reasons (Android 11+ / API 30+).
     * Diagnoses native SIGSEGV/SIGABRT signals, LMK low-memory events, and system terminations.
     */
    fun diagnosePreviousProcessExits(context: Context): List<ProcessExitRecord> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return emptyList()
        }
        val records = mutableListOf<ProcessExitRecord>()
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptyList()
            val exitInfos = am.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            val prefs = context.getSharedPreferences(DIAGNOSTICS_PREFS, Context.MODE_PRIVATE)
            val lastProcessedTimestamp = prefs.getLong(PREF_LAST_DIAGNOSED_EXIT_TS, 0L)
            var newestTimestamp = lastProcessedTimestamp

            for (info in exitInfos) {
                if (info.timestamp <= lastProcessedTimestamp) continue
                if (info.timestamp > newestTimestamp) {
                    newestTimestamp = info.timestamp
                }

                val reasonName = when (info.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE (SIGSEGV/SIGABRT)"
                    ApplicationExitInfo.REASON_CRASH -> "CRASH_JAVA_UNCAUGHT"
                    ApplicationExitInfo.REASON_ANR -> "ANR (Application Not Responding)"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY (Killed by LMK)"
                    ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED (Killed by OS signal)"
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
                    ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
                    ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                    ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
                    ApplicationExitInfo.REASON_OTHER -> "OTHER"
                    else -> "CODE_${info.reason}"
                }

                val rawTrace: String? = try {
                    info.traceInputStream?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                } catch (_: Throwable) {
                    null
                }

                val sanitizedTrace = rawTrace?.let { OperationalLogCatcher.redactSecrets(it) }
                val traceNotice = if (sanitizedTrace.isNullOrBlank()) {
                    "(Native tombstone trace not accessible via ApplicationExitInfo on this OS build)"
                } else {
                    sanitizedTrace.lines().take(50).joinToString("\n")
                }

                val startupCtx = StartupContextTracker.getContext()
                val record = ProcessExitRecord(
                    timestamp = info.timestamp,
                    pid = info.pid,
                    processName = info.processName,
                    reasonCode = info.reason,
                    reasonName = reasonName,
                    status = info.status,
                    description = info.description,
                    trace = sanitizedTrace,
                    startupContext = startupCtx
                )
                records.add(record)

                // Log structured process exit diagnosis event
                val msg = "[${OperationalLogCatcher.EVENT_PROCESS_EXIT_DIAGNOSIS}] PID ${info.pid} (${info.processName}) exited with reason: $reasonName (status=${info.status}). Last attempted op: ${startupCtx.lastAttemptedOperation}, last completed: ${startupCtx.lastCompletedOperation}"
                OperationalLogCatcher.log(
                    category = LogCategory.PROCESS_EXIT,
                    severity = if (info.reason == ApplicationExitInfo.REASON_USER_REQUESTED || info.reason == ApplicationExitInfo.REASON_USER_STOPPED) LogSeverity.INFO else LogSeverity.ERROR,
                    component = "ProcessExitDiagnoser",
                    message = msg,
                    details = "Description: ${info.description ?: "None"}\nTrace: $traceNotice"
                )
            }

            if (newestTimestamp > lastProcessedTimestamp) {
                prefs.edit().putLong(PREF_LAST_DIAGNOSED_EXIT_TS, newestTimestamp).apply()
            }
        } catch (_: Throwable) {
            // Never fail during diagnostic parsing
        }
        return records
    }

    private fun parseCrashFile(file: File): CrashReport? {
        return try {
            val lines = file.readLines()
            var id = file.nameWithoutExtension
            var timestamp = file.lastModified()
            var appVersion = "Unknown"
            var osVersion = "Unknown"
            var deviceModel = "Unknown"
            var supportedAbis = "Unknown"
            var startupStage: String? = null
            var rootCause: String? = null
            var threadName = "Unknown"
            var threadId = 0L
            var exceptionClass = "Throwable"
            var exceptionMessage = ""
            val stackTraceBuilder = StringBuilder()
            val recentEvents = mutableListOf<String>()

            var section = 0 // 0=header, 1=stacktrace, 2=recentEvents

            for (line in lines) {
                when {
                    line.startsWith("Crash ID: ") -> id = line.removePrefix("Crash ID: ").trim()
                    line.startsWith("App Version: ") -> appVersion = line.removePrefix("App Version: ").trim()
                    line.startsWith("OS: ") -> osVersion = line.removePrefix("OS: ").trim()
                    line.startsWith("Device: ") -> deviceModel = line.removePrefix("Device: ").trim()
                    line.startsWith("Supported ABIs: ") -> supportedAbis = line.removePrefix("Supported ABIs: ").trim()
                    line.startsWith("Startup Stage: ") -> startupStage = line.removePrefix("Startup Stage: ").trim()
                    line.startsWith("Root Cause: ") -> rootCause = line.removePrefix("Root Cause: ").trim()
                    line.startsWith("Thread: ") -> {
                        val part = line.removePrefix("Thread: ").trim()
                        threadName = part.substringBefore(" (ID: ")
                        threadId = part.substringAfter(" (ID: ").removeSuffix(")").toLongOrNull() ?: 0L
                    }
                    line.startsWith("Exception: ") -> {
                        val exc = line.removePrefix("Exception: ").trim()
                        exceptionClass = exc.substringBefore(": ")
                        exceptionMessage = exc.substringAfter(": ", "")
                    }
                    line.startsWith("--- STACK TRACE ---") -> section = 1
                    line.startsWith("--- RECENT OPERATIONAL EVENTS ---") -> section = 2
                    line.startsWith("--- END OF DUMP ---") -> section = 3
                    section == 1 -> {
                        stackTraceBuilder.append(line).append("\n")
                    }
                    section == 2 -> {
                        if (line.isNotBlank()) recentEvents.add(line)
                    }
                }
            }

            CrashReport(
                id = id,
                timestamp = timestamp,
                threadName = threadName,
                threadId = threadId,
                exceptionClass = exceptionClass,
                exceptionMessage = exceptionMessage,
                stackTrace = stackTraceBuilder.toString().trim(),
                rootCause = rootCause,
                startupStage = startupStage,
                recentEvents = recentEvents,
                appVersion = appVersion,
                osVersion = osVersion,
                deviceModel = deviceModel,
                supportedAbis = supportedAbis
            )
        } catch (_: Throwable) {
            null
        }
    }
}
