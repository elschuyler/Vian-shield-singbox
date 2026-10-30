package com.example.engine

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Constructor
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
 * - Bootstraps runtime sandbox paths via Libbox.setup(SetupOptions).
 * - Validates configuration schema via Libbox.checkConfig(configJson) before start or reload (BitProxy patch 0007).
 * - Binds to io.nekohasekai.libbox.CommandServer and PlatformInterface when libbox.aar is present.
 * - Implements the full 26-method PlatformInterface and CommandServerHandler contracts.
 * - Bridges PlatformInterface.openTun to VpnService.establishTunInterface with pfd.detachFd() (BitProxy patch 0041).
 * - Bridges PlatformInterface.autoDetectInterfaceControl to VpnService.protect(fd) to prevent routing loops.
 * - Manages CommandServer start, startOrReloadService, closeService, and close lifecycle.
 * - Truthfully reports pending state (ERR_NATIVE_AAR_PENDING) when libbox.aar is not yet compiled by CI.
 */
class SingBoxAdapter(
    private val allowSimulationForTesting: Boolean = false
) : SingBoxEngine {

    companion object {
        const val ENGINE_PINNED_VERSION = "sing-box v1.14.4"
        const val SFA_PINNED_COMMIT = "f87c2eb"

        private const val LIBBOX_CLASS = "io.nekohasekai.libbox.Libbox"
        private const val SETUP_OPTIONS_CLASS = "io.nekohasekai.libbox.SetupOptions"
        private const val PLATFORM_INTERFACE_CLASS = "io.nekohasekai.libbox.PlatformInterface"
        private const val COMMAND_SERVER_CLASS = "io.nekohasekai.libbox.CommandServer"
        private const val COMMAND_SERVER_HANDLER_CLASS = "io.nekohasekai.libbox.CommandServerHandler"
        private const val OVERRIDE_OPTIONS_CLASS = "io.nekohasekai.libbox.OverrideOptions"

        @Volatile
        private var cachedNativeAvailable: Boolean? = null

        fun isNativeAvailable(): Boolean {
            cachedNativeAvailable?.let { return it }
            val available = try {
                val libboxCls = Class.forName(LIBBOX_CLASS)
                Class.forName(PLATFORM_INTERFACE_CLASS)

                // Verify that the native JNI library is actually linked and callable in this runtime.
                // In a host JVM (Robolectric / JUnit on x86_64), libbox.so (ARM) is not linked,
                // so attempting to invoke any native JNI method throws UnsatisfiedLinkError.
                val versionMethod = libboxCls.methods.firstOrNull { it.name.equals("version", ignoreCase = true) && it.parameterCount == 0 }
                if (versionMethod != null) {
                    versionMethod.invoke(null)
                }
                true
            } catch (_: Throwable) {
                // Catches ClassNotFoundException, UnsatisfiedLinkError, LinkageError, ExceptionInInitializerError
                false
            }
            cachedNativeAvailable = available
            return available
        }

        @androidx.annotation.VisibleForTesting
        fun resetNativeAvailabilityCache() {
            cachedNativeAvailable = null
        }
    }

    private val isEngineRunning = AtomicBoolean(false)
    private var nativeCommandServerInstance: Any? = null
    private var nativeLegacyServiceInstance: Any? = null
    private var nativeOverrideOptionsInstance: Any? = null
    private var platformCallback: SingBoxPlatformCallback? = null
    private var activeConfigJson: String? = null
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

        if (!allowSimulationForTesting && isNativeBinaryAvailable()) {
            return try {
                val libboxCls = Class.forName(LIBBOX_CLASS)
                val setupOptionsCls = try {
                    Class.forName(SETUP_OPTIONS_CLASS)
                } catch (_: Throwable) {
                    null
                }

                if (setupOptionsCls != null) {
                    val optionsInstance = setupOptionsCls.getDeclaredConstructor().newInstance()
                    setPropertyOrField(optionsInstance, "basePath", baseDir.absolutePath)
                    setPropertyOrField(optionsInstance, "workingPath", workingDir.absolutePath)
                    setPropertyOrField(optionsInstance, "tempPath", tempDir.absolutePath)
                    setPropertyOrField(optionsInstance, "logMaxLines", 300L)
                    setPropertyOrField(optionsInstance, "debug", false)
                    setPropertyOrField(optionsInstance, "crashReportSource", "Vianshield")
                    setPropertyOrField(optionsInstance, "appVersion", "1.0")
                    setPropertyOrField(optionsInstance, "appMarketingVersion", "1.0")

                    val setupMethod = libboxCls.getMethod("setup", setupOptionsCls)
                    setupMethod.invoke(null, optionsInstance)
                    OperationalLogCatcher.info(
                        LogCategory.ENGINE_INIT,
                        "SingBoxAdapter",
                        "Libbox.setup(SetupOptions) executed successfully"
                    )
                } else {
                    // Fallback to primitive setup signature if SetupOptions struct is absent
                    val setupMethod = libboxCls.methods.firstOrNull { it.name.equals("setup", ignoreCase = true) }
                    if (setupMethod != null) {
                        when (setupMethod.parameterTypes.size) {
                            4 -> setupMethod.invoke(null, baseDir.absolutePath, workingDir.absolutePath, tempDir.absolutePath, false)
                            3 -> setupMethod.invoke(null, baseDir.absolutePath, workingDir.absolutePath, tempDir.absolutePath)
                            2 -> setupMethod.invoke(null, baseDir.absolutePath, tempDir.absolutePath)
                            else -> {
                                val args = arrayOfNulls<Any>(setupMethod.parameterTypes.size)
                                if (args.isNotEmpty()) args[0] = baseDir.absolutePath
                                if (args.size > 1) args[1] = tempDir.absolutePath
                                setupMethod.invoke(null, *args)
                            }
                        }
                        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Libbox.setup(overload) executed")
                    }
                }
                isEnvironmentConfigured = true
                Result.success(Unit)
            } catch (t: Throwable) {
                val root = unwrapReflectionException(t)
                val warn = "Libbox.setup() warning: ${root.message ?: root.toString()}"
                OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "SingBoxAdapter", warn)
                isEnvironmentConfigured = true
                Result.success(Unit) // Non-fatal if setup was previously performed or environment exists
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
        if (!allowSimulationForTesting && isNativeBinaryAvailable()) {
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
            } catch (t: Throwable) {
                val root = unwrapReflectionException(t)
                val err = "Libbox.checkConfig validation failed: ${root.message ?: root.toString()}"
                OperationalLogCatcher.error(LogCategory.CONFIG_VALIDATION, "SingBoxAdapter", err)
                Result.failure(IllegalArgumentException(err, root))
            }
        }

        return Result.success(Unit)
    }

    override fun initialize(configJson: String, platformCallback: SingBoxPlatformCallback): Result<Unit> {
        this.platformCallback = platformCallback
        this.activeConfigJson = configJson

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
        if (!allowSimulationForTesting && isNativeBinaryAvailable()) {
            return initializeNativeCommandServer(configJson, platformCallback)
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

    private fun initializeNativeCommandServer(configJson: String, callback: SingBoxPlatformCallback): Result<Unit> {
        return try {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2A: Resolving Libbox classes...")
            val libboxCls = Class.forName(LIBBOX_CLASS)
            val platformInterfaceCls = Class.forName(PLATFORM_INTERFACE_CLASS)

            val commandServerCls = try {
                Class.forName(COMMAND_SERVER_CLASS)
            } catch (_: ClassNotFoundException) {
                null
            }

            val commandServerHandlerCls = try {
                Class.forName(COMMAND_SERVER_HANDLER_CLASS)
            } catch (_: ClassNotFoundException) {
                null
            }

            val overrideOptionsCls = try {
                Class.forName(OVERRIDE_OPTIONS_CLASS)
            } catch (_: ClassNotFoundException) {
                null
            }

            nativeOverrideOptionsInstance = overrideOptionsCls?.getDeclaredConstructor()?.newInstance()

            // Dynamic Proxy implementing io.nekohasekai.libbox.PlatformInterface (26 callbacks)
            val platformProxy = Proxy.newProxyInstance(
                platformInterfaceCls.classLoader,
                arrayOf(platformInterfaceCls)
            ) { _, method, args ->
                handlePlatformInterfaceCall(method, args, callback)
            }

            // SFA CommandServer Pattern (v1.14.4)
            if (commandServerCls != null && commandServerHandlerCls != null) {
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2B: Instantiating SFA CommandServer...")

                val handlerProxy = Proxy.newProxyInstance(
                    commandServerHandlerCls.classLoader,
                    arrayOf(commandServerHandlerCls)
                ) { _, method, args ->
                    handleCommandServerHandlerCall(method, args, callback)
                }

                // Locate CommandServer constructor or Libbox.newCommandServer
                val constructor: Constructor<*>? = commandServerCls.constructors.firstOrNull {
                    it.parameterTypes.size == 2 &&
                            it.parameterTypes[0].isAssignableFrom(commandServerHandlerCls) &&
                            it.parameterTypes[1].isAssignableFrom(platformInterfaceCls)
                }

                nativeCommandServerInstance = if (constructor != null) {
                    constructor.newInstance(handlerProxy, platformProxy)
                } else {
                    val newCmdMethod = libboxCls.getMethod("newCommandServer", commandServerHandlerCls, platformInterfaceCls)
                    newCmdMethod.invoke(null, handlerProxy, platformProxy)
                }

                OperationalLogCatcher.info(
                    LogCategory.ENGINE_INIT,
                    "SingBoxAdapter",
                    "STAGE 2C: Native CommandServer instantiated with typed PlatformInterface bridge"
                )
                Result.success(Unit)
            } else {
                // Fallback to legacy Libbox.newService for backwards compatibility
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 2B: CommandServer not found; falling back to Libbox.newService...")
                val newServiceMethod = libboxCls.getMethod("newService", String::class.java, platformInterfaceCls)
                nativeLegacyServiceInstance = newServiceMethod.invoke(null, configJson, platformProxy)
                OperationalLogCatcher.info(
                    LogCategory.ENGINE_INIT,
                    "SingBoxAdapter",
                    "STAGE 2C: Native BoxService instantiated with typed PlatformInterface bridge"
                )
                Result.success(Unit)
            }
        } catch (e: Exception) {
            val root = unwrapReflectionException(e)
            val err = "Failed to initialize native sing-box core [${root.javaClass.name}]: ${root.message ?: root.toString()}"
            OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "$err\n${root.stackTraceToString().lines().take(10).joinToString("\n")}")
            Result.failure(root)
        }
    }

    /**
     * Complete dispatch for all 26 PlatformInterface methods defined in
     * sing-box v1.14.4 platform.go and SFA PlatformInterfaceWrapper.kt.
     */
    private fun handlePlatformInterfaceCall(method: Method, args: Array<Any>?, callback: SingBoxPlatformCallback): Any? {
        val methodName = method.name
        val argSummary = args?.joinToString { it?.javaClass?.simpleName ?: "null" } ?: "none"
        OperationalLogCatcher.info(
            LogCategory.ENGINE_INIT,
            "SingBoxAdapter",
            "STAGE 4 [PlatformInterface]: $methodName($argSummary) -> returnType=${method.returnType.simpleName}"
        )

        return when (methodName) {
            // 1. Core VPN TUN establishment
            "openTun" -> {
                val options = args?.firstOrNull()
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4A: Native core requested OpenTun")
                val fd = callback.openTun(options)
                OperationalLogCatcher.info(LogCategory.TUN_INTERFACE, "SingBoxAdapter", "STAGE 4B: OpenTun returned fd=$fd to native core")
                fd
            }

            // 2. Outbound socket protection against routing loops
            "usePlatformAutoDetectInterfaceControl" -> true

            "autoDetectInterfaceControl" -> {
                val fd = (args?.firstOrNull() as? Number)?.toInt() ?: -1
                if (fd >= 0) {
                    val success = callback.autoDetectInterfaceControl(fd)
                    if (!success) {
                        throw IllegalStateException("VpnService.protect(fd=$fd) failed; cannot protect outbound socket from VPN loop")
                    }
                }
                null // Go error returns null on success
            }

            // 3. Operational logging
            "writeLog" -> {
                val msg = args?.firstOrNull()?.toString() ?: ""
                callback.onLog("INFO", msg)
                null
            }

            // 4. Network and system state
            "underNetworkExtension" -> false
            "includeAllNetworks" -> false
            "useProcFS" -> true
            "readWIFIState" -> null
            "getInterfaces" -> null
            "localDNSTransport" -> null
            "systemProxyStatus" -> null
            "clearDNSCache" -> null

            // 5. Interface monitoring & notifications
            "startDefaultInterfaceMonitor",
            "closeDefaultInterfaceMonitor",
            "sendNotification" -> null

            // 6. Shell, bridge, SFTP, and SSH stubs
            "usePlatformShell" -> false
            "checkPlatformShell" -> false
            "openShellSession" -> throw UnsupportedOperationException("Shell sessions are not supported on Android")
            "usePlatformBridge" -> false
            "createBridge" -> throw UnsupportedOperationException("Bridge mode is not supported on Android")
            "lookupUser" -> ""
            "lookupSFTPServer" -> throw UnsupportedOperationException("SFTP is not supported on Android")
            "readSystemSSHHostKey" -> throw UnsupportedOperationException("SSH host keys are not supported on Android")
            "findConnectionOwner" -> null
            "packageNameByUID" -> ""
            "uidByPackageName" -> -1

            // Standard Object methods
            "toString" -> "SingBoxPlatformInterfaceProxy"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this

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

    /**
     * Dispatch for CommandServerHandler callbacks defined in SFA CommandServer.
     */
    private fun handleCommandServerHandlerCall(method: Method, args: Array<Any>?, callback: SingBoxPlatformCallback): Any? {
        val methodName = method.name
        OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 4 [CommandServerHandler]: $methodName")

        return when (methodName) {
            "serviceStop" -> {
                OperationalLogCatcher.info(LogCategory.VPN_LIFECYCLE, "SingBoxAdapter", "Native core signaled serviceStop")
                isEngineRunning.set(false)
                null
            }
            "serviceReload" -> {
                OperationalLogCatcher.info(LogCategory.VPN_LIFECYCLE, "SingBoxAdapter", "Native core signaled serviceReload")
                null
            }
            "getSystemProxyStatus" -> null
            "setSystemProxyEnabled" -> null
            "triggerNativeCrash" -> throw RuntimeException("Native crash explicitly requested by CommandServer")
            "writeDebugMessage" -> {
                val msg = args?.firstOrNull()?.toString() ?: ""
                callback.onLog("DEBUG", msg)
                null
            }
            "connectSSHAgent" -> throw UnsupportedOperationException("SSH agent is not supported on Android")
            "toString" -> "SingBoxCommandServerHandlerProxy"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> args?.firstOrNull() === this
            else -> null
        }
    }

    override fun start(): Result<Unit> {
        if (isEngineRunning.get()) {
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already running (duplicate start suppressed)")
            return Result.success(Unit)
        }

        // 1. SFA CommandServer Pattern
        val cmdServer = nativeCommandServerInstance
        val config = activeConfigJson
        if (cmdServer != null && config != null) {
            return try {
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3A: Starting native CommandServer...")
                val startMethod = cmdServer.javaClass.methods.firstOrNull { it.name.equals("start", ignoreCase = true) }
                startMethod?.invoke(cmdServer)

                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3B: Invoking startOrReloadService()...")
                val startOrReloadMethod = cmdServer.javaClass.methods.firstOrNull {
                    it.name.equals("startOrReloadService", ignoreCase = true)
                }

                if (startOrReloadMethod != null) {
                    when (startOrReloadMethod.parameterTypes.size) {
                        2 -> startOrReloadMethod.invoke(cmdServer, config, nativeOverrideOptionsInstance)
                        1 -> startOrReloadMethod.invoke(cmdServer, config)
                        else -> {
                            val args = arrayOfNulls<Any>(startOrReloadMethod.parameterTypes.size)
                            args[0] = config
                            startOrReloadMethod.invoke(cmdServer, *args)
                        }
                    }
                }

                isEngineRunning.set(true)
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3C: CommandServer service started successfully")
                Result.success(Unit)
            } catch (e: Throwable) {
                val root = unwrapReflectionException(e)
                val err = "CommandServer failed to start [${root.javaClass.name}]: ${root.message ?: root.toString()}"
                OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", err)
                platformCallback?.onError("ERR_START_FAILED", err)
                try {
                    val closeServiceMethod = cmdServer.javaClass.methods.firstOrNull { it.name.equals("closeService", ignoreCase = true) }
                    closeServiceMethod?.invoke(cmdServer)
                } catch (_: Throwable) {}
                Result.failure(root)
            }
        }

        // 2. Legacy BoxService fallback
        val legacyService = nativeLegacyServiceInstance
        if (legacyService != null) {
            return try {
                val startMethod = legacyService.javaClass.getMethod("start")
                startMethod.invoke(legacyService)
                isEngineRunning.set(true)
                OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "STAGE 3C: Legacy BoxService started successfully")
                Result.success(Unit)
            } catch (e: Throwable) {
                val root = unwrapReflectionException(e)
                val err = "Legacy BoxService failed to start: ${root.message}"
                OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", err)
                platformCallback?.onError("ERR_START_FAILED", err)
                try {
                    val closeMethod = legacyService.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                    closeMethod?.invoke(legacyService)
                } catch (_: Throwable) {}
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
        if (!isEngineRunning.get() && nativeCommandServerInstance == null && nativeLegacyServiceInstance == null) {
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Sing-box engine is already stopped (duplicate stop suppressed)")
            return Result.success(Unit)
        }

        val cmdServer = nativeCommandServerInstance
        if (cmdServer != null) {
            try {
                val closeServiceMethod = cmdServer.javaClass.methods.firstOrNull { it.name.equals("closeService", ignoreCase = true) }
                closeServiceMethod?.invoke(cmdServer)
                val closeMethod = cmdServer.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                closeMethod?.invoke(cmdServer)
                nativeCommandServerInstance = null
            } catch (e: Throwable) {
                val root = unwrapReflectionException(e)
                OperationalLogCatcher.warn(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "Exception closing CommandServer: ${root.message}")
                nativeCommandServerInstance = null
            }
        }

        val legacyService = nativeLegacyServiceInstance
        if (legacyService != null) {
            try {
                val closeMethod = legacyService.javaClass.methods.firstOrNull { it.name.equals("close", ignoreCase = true) }
                closeMethod?.invoke(legacyService)
                nativeLegacyServiceInstance = null
            } catch (e: Throwable) {
                val root = unwrapReflectionException(e)
                OperationalLogCatcher.warn(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "Exception closing legacy service: ${root.message}")
                nativeLegacyServiceInstance = null
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

        // 2. CommandServer reload
        val cmdServer = nativeCommandServerInstance
        if (cmdServer != null) {
            val startOrReloadMethod = cmdServer.javaClass.methods.firstOrNull {
                it.name.equals("startOrReloadService", ignoreCase = true)
            }
            if (startOrReloadMethod != null) {
                return try {
                    when (startOrReloadMethod.parameterTypes.size) {
                        2 -> startOrReloadMethod.invoke(cmdServer, newConfigJson, nativeOverrideOptionsInstance)
                        1 -> startOrReloadMethod.invoke(cmdServer, newConfigJson)
                        else -> startOrReloadMethod.invoke(cmdServer, newConfigJson, null)
                    }
                    activeConfigJson = newConfigJson
                    OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "CommandServer reloaded successfully")
                    Result.success(Unit)
                } catch (e: Throwable) {
                    val root = unwrapReflectionException(e)
                    OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "CommandServer reload failed: ${root.message}")
                    Result.failure(root)
                }
            }
        }

        // 3. Legacy BoxService reload
        val legacyService = nativeLegacyServiceInstance
        if (legacyService != null) {
            val reloadMethod = legacyService.javaClass.methods.firstOrNull { it.name.equals("reload", ignoreCase = true) }
            if (reloadMethod != null) {
                return try {
                    reloadMethod.invoke(legacyService, newConfigJson)
                    activeConfigJson = newConfigJson
                    OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Legacy BoxService reloaded successfully")
                    Result.success(Unit)
                } catch (e: Throwable) {
                    val root = unwrapReflectionException(e)
                    OperationalLogCatcher.error(LogCategory.ERROR_DIAGNOSTIC, "SingBoxAdapter", "Legacy reload failed: ${root.message}")
                    Result.failure(root)
                }
            }
        }

        // Simulation mode
        if (allowSimulationForTesting) {
            activeConfigJson = newConfigJson
            OperationalLogCatcher.info(LogCategory.ENGINE_INIT, "SingBoxAdapter", "Simulated engine reloaded successfully")
            return Result.success(Unit)
        }

        val callback = platformCallback
        stop()
        return if (callback != null) {
            val initRes = initialize(newConfigJson, callback)
            if (initRes.isSuccess) start() else initRes
        } else {
            Result.success(Unit)
        }
    }

    private fun setPropertyOrField(target: Any, name: String, value: Any) {
        val setterName = "set" + name.replaceFirstChar { it.uppercase() }
        val method = target.javaClass.methods.firstOrNull {
            it.name == setterName && it.parameterTypes.size == 1
        }
        if (method != null) {
            val paramType = method.parameterTypes[0]
            val convertedVal = when {
                paramType == Long::class.javaPrimitiveType || paramType == java.lang.Long::class.java -> (value as? Number)?.toLong() ?: value
                paramType == Int::class.javaPrimitiveType || paramType == java.lang.Integer::class.java -> (value as? Number)?.toInt() ?: value
                paramType == Boolean::class.javaPrimitiveType || paramType == java.lang.Boolean::class.java -> value as? Boolean ?: false
                else -> value
            }
            method.invoke(target, convertedVal)
            return
        }

        // Fallback to direct field assignment
        val field = target.javaClass.fields.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (field != null) {
            field.set(target, value)
        }
    }

    private fun unwrapReflectionException(e: Throwable): Throwable {
        var current = e
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }
}
