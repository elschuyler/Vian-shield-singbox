// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.database

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.bg.VPNService
import java.io.File

object Settings {
    private const val PREFS_NAME = "sfa_settings"
    private const val KEY_CONFIG_PATH = "selected_config_path"
    private const val KEY_STARTED_BY_USER = "started_by_user"
    private const val KEY_ALLOW_BYPASS = "allow_bypass"
    private const val KEY_AUTO_REDIRECT = "auto_redirect"
    private const val KEY_PER_APP_PROXY = "per_app_proxy"

    private val prefs: SharedPreferences by lazy {
        Application.application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var selectedConfigPath: String
        get() = prefs.getString(KEY_CONFIG_PATH, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CONFIG_PATH, value) }

    var selectedProfile: Long = 1L

    var startedByUser: Boolean
        get() = prefs.getBoolean(KEY_STARTED_BY_USER, false)
        set(value) = prefs.edit { putBoolean(KEY_STARTED_BY_USER, value) }

    var allowBypass: Boolean
        get() = prefs.getBoolean(KEY_ALLOW_BYPASS, false)
        set(value) = prefs.edit { putBoolean(KEY_ALLOW_BYPASS, value) }

    var autoRedirect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_REDIRECT, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_REDIRECT, value) }

    var perAppProxyEnabled: Boolean
        get() = prefs.getBoolean(KEY_PER_APP_PROXY, false)
        set(value) = prefs.edit { putBoolean(KEY_PER_APP_PROXY, value) }

    var oomKillerEnabled: Boolean = false
    var oomKillerDisabled: Boolean = true
    var oomMemoryLimitMB: Int = 256
    var powerReportEnabled: Boolean = false

    fun serviceClass(): Class<*> = VPNService::class.java

    fun getActiveConfigContent(): String {
        val path = selectedConfigPath
        if (path.isNotBlank()) {
            val file = File(path)
            if (file.isFile) {
                return file.readText()
            }
        }
        return ""
    }
}
