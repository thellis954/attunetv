package com.attunetv.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fire TV models report different densities for the same 1080p panel (xhdpi on sticks,
 * lower on some TV Editions), which made the UI tiny and cramped on an Insignia. Every
 * screen therefore renders on a fixed 540dp-tall canvas, so the layout looks the same everywhere.
 */
abstract class TvActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Screen.normalize(newBase))
    }
}

object Screen {
    const val CANVAS_HEIGHT_DP = 540f

    fun normalize(base: Context): Context {
        val dm = base.resources.displayMetrics
        val shortPx = min(dm.widthPixels, dm.heightPixels)
        if (shortPx <= 0) return base
        val dpi = (shortPx * 160f / CANVAS_HEIGHT_DP).roundToInt()
        val current = base.resources.configuration
        if (abs(dpi - current.densityDpi) < 4 && current.fontScale == 1f) return base
        val c = Configuration(current).apply {
            densityDpi = dpi
            fontScale = 1f // the TV's text-size setting would break the fixed canvas
            screenWidthDp = (dm.widthPixels * 160f / dpi).roundToInt()
            screenHeightDp = (dm.heightPixels * 160f / dpi).roundToInt()
            smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
        }
        return base.createConfigurationContext(c)
    }
}
