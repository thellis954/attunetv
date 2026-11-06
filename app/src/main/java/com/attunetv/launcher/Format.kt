package com.attunetv.launcher

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    fun dayLabel(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val today = Calendar.getInstance()
        val diff = daysBetween(today, c)
        return when (diff) {
            0 -> "Today"
            1 -> "Tomorrow"
            in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(t))
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(t))
        }
    }

    fun time(t: Long, clock24: Boolean): String =
        SimpleDateFormat(if (clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(t))

    fun eventWhen(e: CalEvent, clock24: Boolean): String {
        val now = System.currentTimeMillis()
        return when {
            e.allDay -> "${dayLabel(e.start)} · All day"
            e.start <= now -> "Now · until ${time(e.end, clock24)}"
            e.start - now < 60 * 60_000 -> "In ${((e.start - now) / 60_000).coerceAtLeast(1)} min"
            else -> "${dayLabel(e.start)} · ${time(e.start, clock24)}"
        }
    }

    fun shortDay(iso: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
        val diff = daysBetween(Calendar.getInstance(), Calendar.getInstance().apply { time = d })
        if (diff == 0) "Today" else SimpleDateFormat("EEE", Locale.getDefault()).format(d)
    } catch (_: Exception) { iso }

    private fun daysBetween(a: Calendar, b: Calendar): Int {
        fun mid(c: Calendar) = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((mid(b) - mid(a)) / 86_400_000.0).toInt()
    }
}
