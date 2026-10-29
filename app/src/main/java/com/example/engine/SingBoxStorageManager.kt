package com.example.engine

import android.content.Context
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import java.io.File

/**
 * SingBoxStorageManager:
 * Manages sandbox environment directories (baseDir, workingDir, tempDir)
 * for sing-box v1.14.4 according to official sing-box-for-android (SFA) patterns.
 *
 * In sing-box v1.14.4, directory paths are passed to the native core via Libbox.setup(SetupOptions),
 * completely eliminating the obsolete experimental.cache_file JSON configuration workaround.
 */
object SingBoxStorageManager {
    private const val SINGBOX_DIR_NAME = "singbox"
    private const val SINGBOX_TEMP_DIR_NAME = "singbox_temp"

    fun getBaseDir(context: Context): File {
        val dir = File(context.filesDir, SINGBOX_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getWorkingDir(context: Context): File {
        val dir = context.getExternalFilesDir(null) ?: getBaseDir(context)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(context: Context): File {
        val temp = File(context.cacheDir, SINGBOX_TEMP_DIR_NAME)
        if (!temp.exists()) temp.mkdirs()
        return temp
    }

    /**
     * Bootstraps and registers sandbox paths with the SingBoxEngine instance
     * using Libbox.setup(SetupOptions).
     */
    fun setupEnvironment(context: Context, engine: SingBoxEngine): Result<Unit> {
        return try {
            val baseDir = getBaseDir(context)
            val workingDir = getWorkingDir(context)
            val tempDir = getTempDir(context)

            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "StorageManager",
                "Bootstrapping sing-box sandbox directories: base=${baseDir.absolutePath}, working=${workingDir.absolutePath}, temp=${tempDir.absolutePath}"
            )
            engine.setupEnvironment(baseDir, workingDir, tempDir)
        } catch (e: Exception) {
            val err = "Failed to setup sandbox storage environment: ${e.message}"
            OperationalLogCatcher.error(LogCategory.ENGINE_INIT, "StorageManager", err)
            Result.failure(e)
        }
    }

    /**
     * Safe non-sensitive diagnostic summary of the sandbox storage paths.
     */
    fun getSafeStorageDiagnostics(context: Context): Map<String, String> {
        val baseDir = getBaseDir(context)
        val workingDir = getWorkingDir(context)
        val tempDir = getTempDir(context)
        val isWritable = baseDir.canWrite()

        return linkedMapOf(
            "Base Dir" to baseDir.absolutePath,
            "Working Dir" to workingDir.absolutePath,
            "Temp Dir" to tempDir.absolutePath,
            "Directory Exists" to baseDir.exists().toString(),
            "Directory Writable" to isWritable.toString(),
            "Storage Status" to if (isWritable) "Writable App-Private Internal Storage" else "Read-Only / Unwritable"
        )
    }
}
