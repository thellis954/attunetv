package com.attunetv.launcher

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

data class CalEvent(val title: String, val start: Long, val end: Long, val allDay: Boolean) {
    fun toJson() = JSONObject().put("t", title).put("s", start).put("e", end).put("a", allDay)

    companion object {
        fun listToJson(l: List<CalEvent>) = JSONArray().apply { l.forEach { put(it.toJson()) } }.toString()
        fun listFromJson(s: String?): List<CalEvent> = try {
            val a = JSONArray(s ?: return emptyList())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                CalEvent(o.getString("t"), o.getLong("s"), o.getLong("e"), o.getBoolean("a"))
            }
        } catch (_: Exception) { emptyList() }
    }
}

/**
 * Minimal iCalendar (RFC 5545) reader: enough for Google / iCloud / Outlook feeds.
 * Handles all-day + timed events, TZID, UTC, RRULE (DAILY/WEEKLY/MONTHLY/YEARLY with
 * INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY), EXDATE, RECURRENCE-ID overrides, cancellations.
 */
object Ics {

    private class Raw(val props: MutableList<Pair<String, String>> = mutableListOf()) {
        fun first(name: String) = props.firstOrNull { it.first.substringBefore(';').equals(name, true) }
        fun all(name: String) = props.filter { it.first.substringBefore(';').equals(name, true) }
    }

    fun events(text: String, from: Long, to: Long): List<CalEvent> {
        val raws = parse(text)
        val out = mutableListOf<CalEvent>()

        // uid -> set of original start times that have been overridden/cancelled individually
        val overridden = HashMap<String, MutableSet<Long>>()
        for (r in raws) {
            val rid = r.first("RECURRENCE-ID") ?: continue
            val uid = r.first("UID")?.second ?: continue
            parseDate(rid.first, rid.second)?.let { overridden.getOrPut(uid) { mutableSetOf() }.add(it.first) }
        }

        for (r in raws) {
            if (r.first("STATUS")?.second.equals("CANCELLED", true)) continue
            val ds = r.first("DTSTART") ?: continue
            val (start, allDay, tz) = parseDate(ds.first, ds.second) ?: continue
            val de = r.first("DTEND")
            val end = de?.let { parseDate(it.first, it.second)?.first }
                ?: durationEnd(start, r.first("DURATION")?.second)
                ?: (if (allDay) start + DAY else start + HOUR)
            val length = (end - start).coerceAtLeast(0)
            val title = unescape(r.first("SUMMARY")?.second ?: "(No title)")
            val rrule = r.first("RRULE")?.second
            val isOverride = r.first("RECURRENCE-ID") != null

            if (rrule == null || isOverride) {
                if (start < to && start + length > from) out += CalEvent(title, start, start + length, allDay)
                continue
            }
            val ex = mutableSetOf<Long>()
            r.all("EXDATE").forEach { (k, v) ->
                v.split(',').forEach { one -> parseDate(k, one)?.let { ex += it.first } }
            }
            r.first("UID")?.second?.let { overridden[it]?.let(ex::addAll) }
            for (s in expand(start, tz, rrule, from - length, to)) {
                if (s in ex) continue
                if (s < to && s + length > from) out += CalEvent(title, s, s + length, allDay)
            }
        }
        return out.sortedBy { it.start }
    }

    private fun parse(text: String): List<Raw> {
        // Unfold continuation lines
        val lines = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { l ->
            if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] += l.substring(1)
            else lines += l
        }
        val res = mutableListOf<Raw>()
        var cur: Raw? = null
        var depth = 0 // nested components inside VEVENT (e.g. VALARM)
        for (l in lines) {
            when {
                l.equals("BEGIN:VEVENT", true) -> { cur = Raw(); depth = 0 }
                l.equals("END:VEVENT", true) -> { cur?.let(res::add); cur = null }
                cur != null && l.startsWith("BEGIN:", true) -> depth++
                cur != null && l.startsWith("END:", true) -> depth--
                cur != null && depth == 0 -> {
                    val i = colonIndex(l)
                    if (i > 0) cur.props += l.substring(0, i) to l.substring(i + 1)
                }
            }
        }
        return res
    }

    /** First colon not inside a quoted parameter value. */
    private fun colonIndex(l: String): Int {
        var q = false
        for ((i, c) in l.withIndex()) {
            if (c == '"') q = !q else if (c == ':' && !q) return i
        }
        return -1
    }

    /** Returns (epochMillis, allDay, timezone). */
    private fun parseDate(key: String, value: String): Triple<Long, Boolean, TimeZone>? {
        val params = key.split(';').drop(1).associate { it.substringBefore('=').uppercase() to it.substringAfter('=').trim('"') }
        val v = value.trim()
        return try {
            if (params["VALUE"] == "DATE" || Regex("^\\d{8}$").matches(v)) {
                val tz = TimeZone.getDefault()
                val c = Calendar.getInstance(tz).apply {
                    clear(); set(v.substring(0, 4).toInt(), v.substring(4, 6).toInt() - 1, v.substring(6, 8).toInt())
                }
                Triple(c.timeInMillis, true, tz)
            } else {
                val utc = v.endsWith("Z")
                val tz = when {
                    utc -> TimeZone.getTimeZone("UTC")
                    params["TZID"] != null -> TimeZone.getTimeZone(mapTz(params["TZID"]!!))
                    else -> TimeZone.getDefault()
                }
                val c = Calendar.getInstance(tz).apply {
                    clear()
                    set(v.substring(0, 4).toInt(), v.substring(4, 6).toInt() - 1, v.substring(6, 8).toInt(),
                        v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), v.substring(13, 15).toInt())
                }
                Triple(c.timeInMillis, false, if (utc) TimeZone.getDefault() else tz)
            }
        } catch (_: Exception) { null }
    }

    /** Outlook feeds use Windows zone names. Map the common US ones. */
    private fun mapTz(id: String): String = when (id) {
        "Eastern Standard Time" -> "America/New_York"
        "Central Standard Time" -> "America/Chicago"
        "Mountain Standard Time" -> "America/Denver"
        "US Mountain Standard Time" -> "America/Phoenix"
        "Pacific Standard Time" -> "America/Los_Angeles"
        "Alaskan Standard Time" -> "America/Anchorage"
        "Hawaiian Standard Time" -> "Pacific/Honolulu"
        "GMT Standard Time" -> "Europe/London"
        "UTC" -> "UTC"
        else -> id
    }

    private fun durationEnd(start: Long, dur: String?): Long? {
        dur ?: return null
        val m = Regex("P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?").find(dur) ?: return null
        val g = m.groupValues.drop(1).map { it.toLongOrNull() ?: 0 }
        return start + g[0] * 7 * DAY + g[1] * DAY + g[2] * HOUR + g[3] * 60_000 + g[4] * 1000
    }

    private val DAYS = mapOf(
        "SU" to Calendar.SUNDAY, "MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY, "WE" to Calendar.WEDNESDAY,
        "TH" to Calendar.THURSDAY, "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY,
    )

    /** Occurrence start times in [from, to), honoring COUNT from the series start. */
    private fun expand(start: Long, tz: TimeZone, rule: String, from: Long, to: Long): List<Long> {
        val p = rule.split(';').associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
        val freq = p["FREQ"] ?: return listOf(start)
        val interval = p["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = p["COUNT"]?.toIntOrNull()
        val until = p["UNTIL"]?.let { parseDate("UNTIL", it)?.first?.let { u -> if (it.length == 8) u + DAY - 1 else u } }
        val byDay = p["BYDAY"]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val byMonthDay = p["BYMONTHDAY"]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()

        val out = mutableListOf<Long>()
        var n = 0
        val base = Calendar.getInstance(tz).apply { timeInMillis = start }
        val hour = base.get(Calendar.HOUR_OF_DAY); val min = base.get(Calendar.MINUTE); val sec = base.get(Calendar.SECOND)

        fun emit(t: Long): Boolean { // returns false when the series is exhausted
            if (t < start) return true
            if (until != null && t > until) return false
            if (count != null && n >= count) return false
            n++
            if (t >= to) return false
            if (t >= from) out += t
            return true
        }

        val c = Calendar.getInstance(tz).apply { timeInMillis = start }
        var guard = 0
        loop@ while (guard++ < 3000) {
            val candidates: List<Long> = when (freq) {
                "DAILY" -> listOf(c.timeInMillis)
                "WEEKLY" -> {
                    if (byDay.isEmpty()) listOf(c.timeInMillis) else {
                        // Monday of c's week (RFC 5545 default WKST=MO)
                        val wk = (c.clone() as Calendar).apply {
                            add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7))
                        }
                        byDay.mapNotNull { DAYS[it.takeLast(2)] }.map { dow ->
                            (wk.clone() as Calendar).apply {
                                val diff = ((dow - Calendar.MONDAY) + 7) % 7
                                add(Calendar.DAY_OF_MONTH, diff)
                                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, min); set(Calendar.SECOND, sec)
                            }.timeInMillis
                        }.sorted()
                    }
                }
                "MONTHLY" -> when {
                    byDay.isNotEmpty() -> byDay.mapNotNull { nthWeekday(c, it, hour, min, sec) }.sorted()
                    byMonthDay.isNotEmpty() -> byMonthDay.mapNotNull { d ->
                        val max = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val day = if (d < 0) max + d + 1 else d
                        if (day in 1..max) (c.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis else null
                    }.sorted()
                    else -> if (c.get(Calendar.DAY_OF_MONTH) == base.get(Calendar.DAY_OF_MONTH)) listOf(c.timeInMillis) else emptyList()
                }
                "YEARLY" -> listOf(c.timeInMillis)
                else -> return listOf(start).filter { it in from until to }
            }
            for (t in candidates) if (!emit(t)) break@loop
            if (c.timeInMillis > to) break
            when (freq) {
                "DAILY" -> c.add(Calendar.DAY_OF_MONTH, interval)
                "WEEKLY" -> c.add(Calendar.WEEK_OF_YEAR, interval)
                "MONTHLY" -> {
                    c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, interval)
                    if (byDay.isEmpty() && byMonthDay.isEmpty()) {
                        val want = base.get(Calendar.DAY_OF_MONTH)
                        if (want <= c.getActualMaximum(Calendar.DAY_OF_MONTH)) c.set(Calendar.DAY_OF_MONTH, want)
                    }
                }
                "YEARLY" -> c.add(Calendar.YEAR, interval)
            }
        }
        return out
    }

    /** "2TU" = 2nd Tuesday, "-1FR" = last Friday, "MO" = (approximation) first Monday. */
    private fun nthWeekday(month: Calendar, spec: String, h: Int, m: Int, s: Int): Long? {
        val dow = DAYS[spec.takeLast(2)] ?: return null
        val nth = spec.dropLast(2).toIntOrNull() ?: 1
        val c = (month.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, s)
        }
        if (nth > 0) {
            c.set(Calendar.DAY_OF_MONTH, 1)
            while (c.get(Calendar.DAY_OF_WEEK) != dow) c.add(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.DAY_OF_MONTH, 7 * (nth - 1))
        } else {
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
            while (c.get(Calendar.DAY_OF_WEEK) != dow) c.add(Calendar.DAY_OF_MONTH, -1)
            c.add(Calendar.DAY_OF_MONTH, 7 * (nth + 1))
        }
        return if (c.get(Calendar.MONTH) == month.get(Calendar.MONTH)) c.timeInMillis else null
    }

    private fun unescape(s: String) =
        s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\").trim()

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
}
