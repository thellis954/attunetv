package com.attunetv.launcher

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable

/** Secondary screens sit on the blurred daily photo (dimmed), or the theme gradient. */
object Backgrounds {
    fun applyDimmed(activity: Activity, prefs: Prefs) {
        val photo = Wallpaper.current
        activity.window.decorView.background =
            if (prefs.backgroundMode == Prefs.BG_PHOTO && photo != null) {
                LayerDrawable(arrayOf(
                    BitmapDrawable(activity.resources, photo.blurred).apply { isFilterBitmap = true },
                    ColorDrawable(0x8C000000.toInt()),
                ))
            } else Themes.background(prefs.themeIndex)
    }
}
