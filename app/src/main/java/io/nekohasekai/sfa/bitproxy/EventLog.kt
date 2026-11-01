// SPDX-License-Identifier: GPL-3.0-or-later
package io.nekohasekai.sfa.bitproxy

import com.example.logging.LogCategory
import com.example.logging.OperationalLogCatcher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object EventLog {
    private const val MAX_ENTRIES = 200
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val buffer = ConcurrentLinkedDeque<String>()

    fun log(message: String) {
        val entry = "${timeFormat.format(Date())} $message"
        buffer.addLast(entry)
        while (buffer.size > MAX_ENTRIES) {
            buffer.pollFirst()
        }
        OperationalLogCatcher.info(LogCategory.VPN_LIFECYCLE, "SFA", message)
    }

    fun events(): List<String> = buffer.toList()
}
