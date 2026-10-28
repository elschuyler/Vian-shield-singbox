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
 * In sing-box v1.14.4, directory paths are passed to the engine via Libbox.setup(),
 * eliminating the need for the experimental.cache_file JSON configuration workaround.
 */
object SingBoxStorageManager {
    private const val SINGBOX_DIR_NAME = "singbox"
    private const val CACHE_DB_FILE_NAME = "cache.db"

    fun getBaseDir(context: Context): File {
        val dir = File(context.filesDir, SINGBOX_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getWorkingDir(context: Context): File {
        return context.getExternalFilesDir(null) ?: getBaseDir(context)
    }

    fun getTempDir(context: Context): File {
        val temp = File(context.cacheDir, "singbox_temp")
        if (!temp.exists()) temp.mkdirs()
        return temp
    }

    fun getCacheDir(context: Context): File {
        return getBaseDir(context)
    }

    fun getCacheDbFile(context: Context): File {
        return File(getBaseDir(context), CACHE_DB_FILE_NAME)
    }

    /**
     * Bootstraps and registers sandbox paths with the SingBoxEngine instance.
     */
    fun setupEnvironment(context: Context, engine: SingBoxEngine): Result<Unit> {
        val baseDir = getBaseDir(context)
        val workingDir = getWorkingDir(context)
        val tempDir = getTempDir(context)
        return engine.setupEnvironment(baseDir, workingDir, tempDir)
    }

    /**
     * Backward-compatible storage preparation helper.
     */
    fun prepareCacheStorage(context: Context): Result<String> {
        return try {
            val cacheDir = getBaseDir(context)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            if (!cacheDir.canWrite()) {
                val err = "Sing-box cache directory is not writable: ${cacheDir.absolutePath}"
                OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
                return Result.failure(IllegalStateException(err))
            }

            val cacheFile = getCacheDbFile(context)
            if (cacheFile.exists()) {
                if (cacheFile.length() == 0L) {
                    OperationalLogCatcher.warn(
                        LogCategory.ENGINE_INIT,
                        "StorageManager",
                        "Found 0-byte corrupt cache.db; removing to allow clean database initialization"
                    )
                    cacheFile.delete()
                } else if (!cacheFile.canWrite()) {
                    val err = "Existing cache.db file is not writable: ${cacheFile.absolutePath}"
                    OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
                    return Result.failure(IllegalStateException(err))
                }
            }

            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "StorageManager",
                "Sing-box sandbox directories initialized (base=${cacheDir.absolutePath})"
            )
            Result.success(cacheFile.absolutePath)
        } catch (e: Exception) {
            val err = "Unexpected error preparing sing-box cache storage: ${e.message}"
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
            Result.failure(e)
        }
    }

    /**
     * Safely resets/clears transient sing-box runtime data.
     */
    fun clearCacheFile(context: Context): Boolean {
        return try {
            val file = getCacheDbFile(context)
            if (file.exists()) {
                val deleted = file.delete()
                OperationalLogCatcher.info(
                    LogCategory.ENGINE_INIT,
                    "StorageManager",
                    "Cleared sing-box cache.db (deleted=$deleted)"
                )
                deleted
            } else {
                true
            }
        } catch (e: Exception) {
            OperationalLogCatcher.error(
                LogCategory.ENGINE_INIT,
                "StorageManager",
                "Failed to clear cache.db: ${e.message}"
            )
            false
        }
    }

    /**
     * Safe non-sensitive diagnostic summary of the sandbox storage paths.
     */
    fun getSafeCacheDiagnostics(context: Context): Map<String, String> {
        val baseDir = getBaseDir(context)
        val workingDir = getWorkingDir(context)
        val tempDir = getTempDir(context)
        val cacheFile = getCacheDbFile(context)
        val isWritable = baseDir.canWrite()

        return linkedMapOf(
            "Base Dir" to baseDir.absolutePath,
            "Working Dir" to workingDir.absolutePath,
            "Temp Dir" to tempDir.absolutePath,
            "Cache Path" to cacheFile.absolutePath,
            "Directory Exists" to baseDir.exists().toString(),
            "Directory Writable" to isWritable.toString(),
            "Storage Status" to if (isWritable) "Writable App-Private Internal Storage" else "Read-Only / Unwritable"
        )
    }
}
