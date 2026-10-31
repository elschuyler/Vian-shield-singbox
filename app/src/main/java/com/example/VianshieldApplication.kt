package com.example

import android.app.Application
import com.example.logging.CrashReporter
import com.example.logging.OperationalLogCatcher
import com.example.logging.StartupContextTracker

class VianshieldApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OperationalLogCatcher.initialize(this)
        StartupContextTracker.initialize(this)
        CrashReporter.install(this)
        CrashReporter.diagnosePreviousProcessExits(this)
    }
}
