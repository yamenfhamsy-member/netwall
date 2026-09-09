package com.netwall.vpn

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** Thread-safe circular buffer of service events for the Diagnostics screen. */
object EventLog {

    data class Event(val time: Long, val tag: String, val detail: String)

    private const val MAX = 50
    private val guard = Any()
    private val events = ArrayDeque<Event>(MAX)

    fun log(tag: String, detail: String = "") {
        synchronized(guard) {
            if (events.size >= MAX) events.removeFirst()
            events.addLast(Event(System.currentTimeMillis(), tag, detail))
        }
    }

    fun snapshot(): List<Event> = synchronized(guard) { events.toList() }

    fun formatted(): String {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return snapshot().joinToString("\n") { e ->
            "${fmt.format(Date(e.time))} [${e.tag}] ${e.detail}".trim()
        }
    }
}
