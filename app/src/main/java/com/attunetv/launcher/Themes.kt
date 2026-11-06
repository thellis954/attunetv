package com.attunetv.launcher

import android.graphics.drawable.GradientDrawable

/** Built-in backdrops. Calm, dark, OLED-friendly gradients. */
object Themes {
    data class Theme(val name: String, val colors: IntArray)

    val all = listOf(
        Theme("Midnight", intArrayOf(0xFF0B1020.toInt(), 0xFF141B33.toInt(), 0xFF0B1020.toInt())),
        Theme("Aurora", intArrayOf(0xFF061A1A.toInt(), 0xFF0E3B3A.toInt(), 0xFF1B1037.toInt())),
        Theme("Ember", intArrayOf(0xFF1A0B0B.toInt(), 0xFF3A1A12.toInt(), 0xFF120A16.toInt())),
        Theme("Graphite", intArrayOf(0xFF101010.toInt(), 0xFF1E1E22.toInt(), 0xFF0A0A0A.toInt())),
        Theme("Pure black", intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt())),
    )

    fun background(index: Int): GradientDrawable {
        val t = all[index.mod(all.size)]
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, t.colors)
    }
}
