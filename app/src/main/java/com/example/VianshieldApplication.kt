package com.example

import android.app.Application
import com.example.logging.CrashReporter
import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import com.example.logging.StartupContextTracker
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.sfa.Application as SfaApplication
import java.io.File

class VianshieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OperationalLogCatcher.initialize(this)
        StartupContextTracker.initialize(this)
        CrashReporter.install(this)
        CrashReporter.diagnosePreviousProcessExits(this)

        // Initialize SFA global application context
        SfaApplication.initialize(this)

        // Bootstrap native libbox sandbox environment
        val singboxBase = File(filesDir, "singbox").apply { mkdirs() }
        val workingDir = getExternalFilesDir(null) ?: filesDir
        val tempDir = File(cacheDir, "singbox").apply { mkdirs() }

        runCatching {
            val options = SetupOptions().apply {
                basePath = singboxBase.absolutePath
                workingPath = workingDir.absolutePath
                tempPath = tempDir.absolutePath
                logMaxLines = 3000
                debug = BuildConfig.DEBUG
                crashReportSource = "Vianshield"
                appVersion = BuildConfig.VERSION_CODE.toString()
                appMarketingVersion = BuildConfig.VERSION_NAME
            }
            Libbox.setup(options)
            OperationalLogCatcher.info(
                LogCategory.ENGINE_INIT,
                "VianshieldApp",
                "Official SFA Libbox.setup initialized successfully"
            )
        }.onFailure {
            OperationalLogCatcher.warn(
                LogCategory.ENGINE_INIT,
                "VianshieldApp",
                "Libbox.setup notification: ${it.message}"
            )
        }
    }
}
