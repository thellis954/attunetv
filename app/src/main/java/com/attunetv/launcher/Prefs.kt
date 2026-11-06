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
    }
}
