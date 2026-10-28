package com.example.engine

import android.content.Context
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import java.io.File

/**
 * SingBoxStorageManager:
 * Manages app-private internal storage for sing-box v1.11.0 runtime files.
 *
 * Background:
 * On Android, the execution environment CWD is typically "/" (read-only rootfs).
 * If sing-box does not receive an explicit absolute path for its cache file,
 * it defaults to "cache.db", resulting in:
 * "initialize cache-file: open cache.db: read-only file system".
 *
 * This manager:
 * - Allocates a dedicated directory in internal app storage (context.filesDir/singbox).
 * - Prepares and validates directory write permissions before engine startup.
 * - Protects against zero-length corrupt databases caused by interrupted writes.
 * - Confines all cache mutations strictly to singbox/cache.db (zero impact on unrelated app data).
 * - Provides non-sensitive diagnostic reporting.
 */
object SingBoxStorageManager {
    private const val SINGBOX_DIR_NAME = "singbox"
    private const val CACHE_DB_FILE_NAME = "cache.db"

    /**
     * Resolves the dedicated app-private directory for sing-box runtime data.
     * Guaranteed to be inside internal app storage (/data/user/0/.../files/singbox)
     * which does not require external storage permissions and survives app updates.
     */
    fun getCacheDir(context: Context): File {
        return File(context.filesDir, SINGBOX_DIR_NAME)
    }

    /**
     * Resolves the absolute file handle for sing-box's persistent cache.db.
     */
    fun getCacheDbFile(context: Context): File {
        return File(getCacheDir(context), CACHE_DB_FILE_NAME)
    }

    /**
     * Prepares and verifies the app-private cache storage before engine startup.
     * - Creates the parent directory if missing.
     * - Verifies write permissions on the directory.
     * - Checks for zero-length / corrupt cache files from interrupted writes and safely removes them.
     * - Returns a Result containing the absolute path if safely usable, or failure if storage is unwritable.
     */
    fun prepareCacheStorage(context: Context): Result<String> {
        return try {
            val cacheDir = getCacheDir(context)
            if (!cacheDir.exists()) {
                val created = cacheDir.mkdirs()
                if (!created && !cacheDir.exists()) {
                    val err = "Failed to create sing-box storage directory: ${cacheDir.absolutePath}"
                    OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
                    return Result.failure(IllegalStateException(err))
                }
            }

            if (!cacheDir.canWrite()) {
                val err = "Sing-box cache directory is not writable: ${cacheDir.absolutePath}"
                OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
                return Result.failure(IllegalStateException(err))
            }

            val cacheFile = getCacheDbFile(context)
            if (cacheFile.exists()) {
                // If the file exists but has 0 bytes (e.g. truncated from crash/OOM during creation),
                // bbolt cannot open it and crashes with unexpected EOF or invalid header.
                // Safely delete the empty/corrupt file so sing-box initializes a fresh DB.
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
                "Sing-box app-private cache storage ready at: ${cacheFile.absolutePath} (exists=${cacheFile.exists()}, size=${cacheFile.length()}B)"
            )
            Result.success(cacheFile.absolutePath)
        } catch (e: Exception) {
            val err = "Unexpected error preparing sing-box cache storage: ${e.message}"
            OperationalLogCatcher.warn(LogCategory.ENGINE_INIT, "StorageManager", err)
            Result.failure(e)
        }
    }

    /**
     * Safely resets/clears the sing-box cache file (e.g., on corruption recovery).
     * Strictly touches ONLY singbox/cache.db without deleting any unrelated app files.
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
     * Safe non-sensitive diagnostic summary of the cache storage state.
     */
    fun getSafeCacheDiagnostics(context: Context): Map<String, String> {
        val dir = getCacheDir(context)
        val file = getCacheDbFile(context)
        val isWritable = if (dir.exists()) dir.canWrite() else context.filesDir.canWrite()
        return linkedMapOf(
            "Cache Path" to file.absolutePath,
            "Directory Exists" to dir.exists().toString(),
            "Directory Writable" to isWritable.toString(),
            "File Exists" to file.exists().toString(),
            "File Size" to if (file.exists()) "${file.length()} bytes" else "0 bytes",
            "Storage Status" to if (isWritable) "Writable App-Private Internal Storage" else "Read-Only / Unwritable"
        )
    }
}
