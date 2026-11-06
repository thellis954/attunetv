package com.attunetv.launcher

import android.content.Context

/** Tiny persistence layer: favorites, hidden apps, ordering, theme, redirect toggle. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("attunetv", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = readList(KEY_FAVORITES)
        set(v) = writeList(KEY_FAVORITES, v)

    var hidden: Set<String>
        get() = readList(KEY_HIDDEN).toSet()
        set(v) = writeList(KEY_HIDDEN, v.toList())

    /** User-defined order for the "All apps" grid. Apps not listed sort alphabetically after. */
    var appOrder: List<String>
        get() = readList(KEY_ORDER)
        set(v) = writeList(KEY_ORDER, v)

    var showHidden: Boolean
        get() = sp.getBoolean(KEY_SHOW_HIDDEN, false)
        set(v) = sp.edit().putBoolean(KEY_SHOW_HIDDEN, v).apply()

    var themeIndex: Int
        get() = sp.getInt(KEY_THEME, 0)
        set(v) = sp.edit().putInt(KEY_THEME, v).apply()

    var redirectEnabled: Boolean
        get() = sp.getBoolean(KEY_REDIRECT, true)
        set(v) = sp.edit().putBoolean(KEY_REDIRECT, v).apply()

    /** Most-recently launched first. */
    var recent: List<String>
        get() = readList(KEY_RECENT)
        set(v) = writeList(KEY_RECENT, v.take(12))

    /** ZIP code or "City, State". Blank = auto-detect from IP. */
    var weatherLocation: String
        get() = sp.getString(KEY_WX_LOC, "")!!
        set(v) = sp.edit().putString(KEY_WX_LOC, v.trim()).apply()

    var fahrenheit: Boolean
        get() = sp.getBoolean(KEY_WX_F, true)
        set(v) = sp.edit().putBoolean(KEY_WX_F, v).apply()

    var showWeather: Boolean
        get() = sp.getBoolean(KEY_SHOW_WX, true)
        set(v) = sp.edit().putBoolean(KEY_SHOW_WX, v).apply()

    var showCalendar: Boolean
        get() = sp.getBoolean(KEY_SHOW_CAL, true)
        set(v) = sp.edit().putBoolean(KEY_SHOW_CAL, v).apply()

    /** iCal (.ics) feed URLs, e.g. Google Calendar "Secret address in iCal format". */
    var calendarUrls: List<String>
        get() = readList(KEY_CAL_URLS)
        set(v) = writeList(KEY_CAL_URLS, v.map { it.trim() }.filter { it.startsWith("http") || it.startsWith("webcal") })

    var clock24: Boolean
        get() = sp.getBoolean(KEY_CLOCK24, false)
        set(v) = sp.edit().putBoolean(KEY_CLOCK24, v).apply()

    /** Minutes of idle on the home screen before Ambient mode; 0 = never. */
    var ambientMinutes: Int
        get() = sp.getInt(KEY_AMBIENT, 10)
        set(v) = sp.edit().putInt(KEY_AMBIENT, v).apply()

    var openOnBoot: Boolean
        get() = sp.getBoolean(KEY_BOOT, true)
        set(v) = sp.edit().putBoolean(KEY_BOOT, v).apply()

    /** 0 = daily photo, 1 = app-color glow, 2 = plain theme gradient. */
    var backgroundMode: Int
        get() = sp.getInt(KEY_BG_MODE, BG_PHOTO)
        set(v) = sp.edit().putInt(KEY_BG_MODE, v).apply()

    /** Shown in the greeting ("Good evening, Tom"). Blank = no name. */
    var userName: String
        get() = sp.getString(KEY_NAME, "")!!
        set(v) = sp.edit().putString(KEY_NAME, v.trim()).apply()

    fun lastLaunched(key: String): Long = sp.getLong("launched_$key", 0L)
    fun markLaunched(key: String) = sp.edit().putLong("launched_$key", System.currentTimeMillis()).apply()

    fun cache(key: String): String? = sp.getString("cache_$key", null)
    fun putCache(key: String, value: String) = sp.edit().putString("cache_$key", value).apply()

    var seededDefaults: Boolean
        get() = sp.getBoolean(KEY_SEEDED, false)
        set(v) = sp.edit().putBoolean(KEY_SEEDED, v).apply()

    private fun readList(key: String): List<String> =
        sp.getString(key, "")!!.split('\n').filter { it.isNotBlank() }

    private fun writeList(key: String, v: List<String>) =
        sp.edit().putString(key, v.joinToString("\n")).apply()

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ORDER = "order"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val KEY_THEME = "theme"
        private const val KEY_REDIRECT = "redirect"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_RECENT = "recent"
        private const val KEY_WX_LOC = "wx_location"
        private const val KEY_WX_F = "wx_fahrenheit"
        private const val KEY_SHOW_WX = "show_weather"
        private const val KEY_SHOW_CAL = "show_calendar"
        private const val KEY_CAL_URLS = "calendar_urls"
        private const val KEY_CLOCK24 = "clock24"
        private const val KEY_AMBIENT = "ambient_minutes"
        private const val KEY_BOOT = "open_on_boot"
        private const val KEY_BG_MODE = "background_mode"
        private const val KEY_NAME = "user_name"
        const val BG_PHOTO = 0
        const val BG_GLOW = 1
        const val BG_THEME = 2
    }
}
