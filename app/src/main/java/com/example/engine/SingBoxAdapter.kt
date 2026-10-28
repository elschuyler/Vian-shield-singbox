package com.example.engine

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONObject
import java.io.File
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SingBoxAdapter:
 * Bridges Android VpnService to sing-box v1.14.4 native libbox core.
 *
 * Implements official sing-box-for-android (SFA) / BitProxy lifecycle:
 * - Bootstraps runtime sandbox paths (baseDir, workingDir, tempDir) via Libbox.setup.
 * - Validates configuration schema via Libbox.checkConfig before start or reload (BitProxy patch 0007).
 * - Dynamically binds to io.nekohasekai.libbox.Libbox and PlatformInterface when libbox.aar is present.
 * - Bridges PlatformInterface.openTun to VpnService.establishTunInterface with pfd.detachFd() (BitProxy patch 0041).
 * - Bridges PlatformInterface.autoDetectInterfaceControl to VpnService.protect(fd) to prevent routing loops.
 * - Handles service start fallback and failure cleanup (BitProxy patch 0008).
 * - Truthfully reports pending state when libbox.aar is not yet compiled by CI.
 */
class SingBoxAdapter(
    private val allowSimulationForTesting: Boolean = false
) : SingBoxEngine {

    companion object {
        const val ENGINE_PINNED_VERSION = "sing-box v1.14.4"
        const val SFA_PINNED_COMMIT = "f87c2eb"
        private const val LIBBOX_CLASS = "io.nekohasekai.libbox.Libbox"
        private const val PLATFORM_INTERFACE_CLASS = "io.nekohasekai.libbox.PlatformInterface"

        fun isNativeAvailable(): Boolean {
            return try {
                Class.forName(LIBBOX_CLASS)
                Class.forName(PLATFORM_INTERFACE_CLASS)
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private val isEngineRunning = AtomicBoolean(false)
    private var nativeServiceInstance: Any? = null
    private var platformCallback: SingBoxPlatformCallback? = null
    private var isEnvironmentConfigured = false

    override fun getEngineVersion(): String = ENGINE_PINNED_VERSION

    override fun isRunning(): Boolean = isEngineRunning.get()

    override fun isNativeBinaryAvailable(): Boolean = isNativeAvailable()

    override fun setupEnvironment(baseDir: File, workingDir: File, tempDir: File): Result<Unit> {
        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "Setting up libbox environment: base=${baseDir.absolutePath}, working=${workingDir.absolutePath}, temp=${tempDir.absolutePath}"
        )

        baseDir.mkdirs()
        workingDir.mkdirs()
        tempDir.mkdirs()

        if (isNativeBinaryAvailable()) {
            return try {
                val libboxCls = Class.forName(LIBBOX_CLASS)
                // Locate Setup method dynamically (accommodates various libbox.Setup overloads)
                val setupMethod = libboxCls.methods.firstOrNull { it.name.equals("setup", ignoreCase = true) }
                if (setupMethod != null) {
                    when (setupMethod.parameterTypes.size) {
                        4 -> setupMethod.invoke(null, baseDir.absolutePath, workingDir.absolutePath, tempDir.absolutePath, false)
                        3 -> setupMethod.invoke(null, baseDir.absolutePath, workingDir.absolutePath, tempDir.absolutePath)
                        2 -> setupMethod.invoke(null, baseDir.absolutePath, tempDir.absolutePath)
                        else -> {
                            // Try first matching or default invocation
                            val args = arrayOfNulls<Any>(setupMethod.parameterTypes.size)
                            if (args.isNotEmpty()) args[0] = baseDir.absolutePath
                            if (args.size > 1) args[1] = tempDir.absolutePath
                            setupMethod.invoke(null, *args)
                        }
                    }
                    OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Libbox.setup() invoked successfully")
                }
                isEnvironmentConfigured = true
                Result.success(Unit)
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val warn = "Libbox.setup() warning: ${root.message ?: root.toString()}"
                OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "SingBoxAdapter", warn)
                isEnvironmentConfigured = true
                Result.success(Unit) // Non-fatal if setup was already performed or optional
            }
        }

        isEnvironmentConfigured = true
        return Result.success(Unit)
    }

    override fun checkConfig(configJson: String): Result<Unit> {
        // 1. Local JSON syntax check
        try {
            val json = JSONObject(configJson)
            if (!json.has("inbounds") || (!json.has("outbounds") && !json.has("endpoints"))) {
                val err = "Invalid configuration: missing required 'inbounds' and 'outbounds'/'endpoints'"
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
                return Result.failure(IllegalArgumentException(err))
            }
        } catch (e: Exception) {
            val err = "Malformed sing-box JSON configuration: ${e.message}"
            OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
            return Result.failure(IllegalArgumentException(err, e))
        }

        // 2. Native Libbox.checkConfig verification (BitProxy patch 0007)
        if (isNativeBinaryAvailable()) {
            return try {
                val libboxCls = Class.forName(LIBBOX_CLASS)
                val checkMethod = libboxCls.methods.firstOrNull { it.name.equals("checkConfig", ignoreCase = true) }
                if (checkMethod != null) {
                    checkMethod.invoke(null, configJson)
                    OperationalLogCatcher.info(
                        LogCategory.CONFIG_VALIDATION,
                        "SingBoxAdapter",
                        "Configuration validated successfully via native Libbox.checkConfig"
                    )
                }
                Result.success(Unit)
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val err = "Libbox.checkConfig validation failed: ${root.message ?: root.toString()}"
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
                Result.failure(IllegalArgumentException(err, root))
            }
        }

        return Result.success(Unit)
    }

    override fun initialize(configJson: String, platformCallback: SingBoxPlatformCallback): Result<Unit> {
        this.platformCallback = platformCallback

        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "Initializing sing-box adapter targeting $ENGINE_PINNED_VERSION (SFA $SFA_PINNED_COMMIT)"
        )

        // Validate configuration schema before initializing core
        val validationResult = checkConfig(configJson)
        if (validationResult.isFailure) {
            return validationResult
        }

        // Check for native libbox presence
        if (isNativeBinaryAvailable()) {
            return initializeNativeService(configJson, platformCallback)
        }

        if (allowSimulationForTesting) {
            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "SingBoxAdapter",
                "Operating in verified test simulation mode (allowSimulationForTesting=true)"
            )
            return Result.success(Unit)
        }

        val missingMsg = "sing-box native engine (libbox.aar) is not present in app/libs/. Run GitHub Actions workflow (.github/workflows/build-vianshield.yml) to compile upstream $ENGINE_PINNED_VERSION."
        OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "SingBoxAdapter", missingMsg)
        return Result.failure(IllegalStateException(missingMsg))
    }

    private fun initializeNativeService(configJson: String, callback: SingBoxPlatformCallback): Result<Unit> {
        return try {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2A: Resolving Libbox and PlatformInterface classes...")
            val libboxCls = Class.forName(LIBBOX_CLASS)
            val platformInterfaceCls = Class.forName(PLATFORM_INTERFACE_CLASS)

            // Dynamic Proxy implementing io.nekohasekai.libbox.PlatformInterface
            val proxyHandler = InvocationHandler { _, method, args ->
                handlePlatformInterfaceCall(method, args, callback)
            }

            val proxy = Proxy.newProxyInstance(
                platformInterfaceCls.classLoader,
                arrayOf(platformInterfaceCls),
                proxyHandler
            )

            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2B: Calling Libbox.newService()...")
            val newServiceMethod = libboxCls.getMethod("newService", String::class.java, platformInterfaceCls)
            nativeServiceInstance = newServiceMethod.invoke(null, configJson, proxy)

            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "SingBoxAdapter",
                "STAGE 2C: Native BoxService instantiated with typed PlatformInterface bridge"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            val root = unwrapReflectionException(e)
            val err = "Failed to initialize native sing-box BoxService [${root.javaClass.name}]: ${root.message ?: root.toString()}"
            OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "$err\n${root.stackTraceToString().lines().take(10).joinToString("\n")}")
            Result.failure(root)
        }
    }

    private fun handlePlatformInterfaceCall(method: Method, args: Array<Any>?, callback: SingBoxPlatformCallback): Any? {
        val methodName = method.name
        val argSummary = args?.joinToString { it?.javaClass?.simpleName ?: "null" } ?: "none"
        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "STAGE 4 [PlatformCallback]: $methodName($argSummary) -> returnType=${method.returnType.simpleName}"
        )

        return when {
            methodName.equals("openTun", ignoreCase = true) -> {
                val options = args?.firstOrNull()
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4A: Native engine requested OpenTun")
                val fd = callback.openTun(options)
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4B: OpenTun returned fd=$fd to native engine")
                fd
            }
            methodName.equals("usePlatformAutoDetectInterfaceControl", ignoreCase = true) -> {
                true // Instruct sing-box to call autoDetectInterfaceControl for outbound sockets
            }
            methodName.equals("autoDetectInterfaceControl", ignoreCase = true) -> {
                val fd = (args?.firstOrNull() as? Number)?.toInt() ?: -1
                if (fd >= 0) {
                    val success = callback.autoDetectInterfaceControl(fd)
                    if (!success) {
                        throw IllegalStateException("VpnService.protect(fd=$fd) failed; cannot protect outbound socket from VPN loop")
                    }
                }
                null // Go error returns null on success
            }
            methodName.equals("writeLog", ignoreCase = true) -> {
                val msg = args?.firstOrNull()?.toString() ?: ""
                callback.onLog("INFO", msg)
                null
            }
            methodName == "toString" -> "SingBoxPlatformInterfaceProxy"
            methodName == "hashCode" -> System.identityHashCode(this)
            methodName == "equals" -> args?.firstOrNull() === this
            else -> {
                when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        }
    }

    override fun start(): Result<Unit> {
        if (isEngineRunning.get()) {
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already running (duplicate start suppressed)")
            return Result.success(Unit)
        }

        val service = nativeServiceInstance
        if (service != null) {
            return try {
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3A: Locating start() on ${service.javaClass.name}...")
                val startMethod = service.javaClass.getMethod("start")
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3B: Invoking BoxService.start()...")
                startMethod.invoke(service)
                isEngineRunning.set(true)
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3C: Native sing-box BoxService started successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val rootName = root.javaClass.name
                val rootMsg = root.message ?: root.localizedMessage ?: root.toString()
                val stackPreview = root.stackTraceToString().lines().take(12).joinToString("\n")
                val err = "Native sing-box BoxService failed to start [$rootName]: $rootMsg"
                OperationalLogCatcher.error(
                    LogCategory.ERROR_DIAGNOSTIC,
                    "SingBoxAdapter",
                    "$err\nCause chain: ${describeCauseChain(root)}\nStack trace:\n$stackPreview"
                )
                // BitProxy patch 0008: Notify and rollback
                platformCallback?.onError("ERR_START_FAILED", err)
                try {
                    val closeMethod = service.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                    closeMethod?.invoke(service)
                } catch (_: Exception) {}
                nativeServiceInstance = null
                Result.failure(root)
            }
        }

        if (allowSimulationForTesting) {
            isEngineRunning.set(true)
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Simulated engine core started for testing")
            return Result.success(Unit)
        }

        val err = "Cannot start sing-box: Native service instance is null (libbox.aar not linked)"
        OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "SingBoxAdapter", err)
        return Result.failure(IllegalStateException(err))
    }

    override fun stop(): Result<Unit> {
        if (!isEngineRunning.get() && nativeServiceInstance == null) {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already stopped (duplicate stop suppressed)")
            return Result.success(Unit)
        }

        val service = nativeServiceInstance
        if (service != null) {
            try {
                val closeMethod = service.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                closeMethod?.invoke(service)
                nativeServiceInstance = null
            } catch (e: Exception) {
                val root = unwrapReflectionException(e)
                val err = "Exception while closing native BoxService: ${root.message ?: root.toString()}"
                OperationalLogCatcher.warn(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", err)
                nativeServiceInstance = null
            }
        }

        isEngineRunning.set(false)
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine stopped and native resources released cleanly")
        return Result.success(Unit)
    }

    override fun reload(newConfigJson: String): Result<Unit> {
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Reload requested with updated configuration")

        // 1. Validate new configuration before making any state changes (BitProxy patch 0007)
        val validationResult = checkConfig(newConfigJson)
        if (validationResult.isFailure) {
            OperationalLogCatcher.error(
                LogCategory.CONFIG_VALIDATION,
                "SingBoxAdapter",
                "Reload rejected: new configuration failed validation; keeping current state"
            )
            return validationResult
        }

        val service = nativeServiceInstance
        if (service != null) {
            // Check if BoxService has a native reload method
            val reloadMethod = service.javaClass.methods.firstOrNull { it.name.equals("reload", ignoreCase = true) }
            if (reloadMethod != null) {
                return try {
                    reloadMethod.invoke(service, newConfigJson)
                    OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Native BoxService reloaded successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    val root = unwrapReflectionException(e)
                    OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "Native reload failed: ${root.message}")
                    Result.failure(root)
                }
            }
        }

        // If no native reload method or in simulation mode, stop and reinitialize
        val callback = platformCallback
        stop()
        return if (callback != null) {
            val initRes = initialize(newConfigJson, callback)
            if (initRes.isSuccess) start() else initRes
        } else {
            Result.success(Unit)
        }
    }

    private fun unwrapReflectionException(e: Throwable): Throwable {
        var current = e
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private fun describeCauseChain(throwable: Throwable): String {
        val sb = StringBuilder()
        var curr: Throwable? = throwable
        var depth = 0
        while (curr != null && depth < 6) {
            if (depth > 0) sb.append(" -> ")
            sb.append("[${curr.javaClass.simpleName}]: ${curr.message}")
            curr = curr.cause
            depth++
        }
        return sb.toString()
    }
}
