package com.attunetv.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.widget.ImageView

/**
 * "Dynamic app preview": the background picks up the focused app's banner art,
 * heavily blurred (downscale to a few pixels → smooth upscale), and cross-fades.
 */
class Backdrop(private val a: ImageView, private val b: ImageView, private val res: Resources) {

    private val cache = LruCache<String, Bitmap>(48)
    private var front = a
    private var currentKey: String? = null

    fun show(app: AppEntry?) {
        if (app == null) { clear(); return }
        if (app.key == currentKey) return
        currentKey = app.key
        val bmp = cache.get(app.key) ?: tiny(app.banner ?: app.icon).also { cache.put(app.key, it) }
        val back = if (front === a) b else a
        back.setImageDrawable(BitmapDrawable(res, bmp).apply { isFilterBitmap = true })
        back.animate().alpha(1f).setDuration(450).start()
        front.animate().alpha(0f).setDuration(450).start()
        front = back
    }

    fun clear() {
        currentKey = null
        a.animate().alpha(0f).setDuration(300).start()
        b.animate().alpha(0f).setDuration(300).start()
    }

    private fun tiny(d: Drawable): Bitmap {
        val w = 16; val h = 9
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val old = d.bounds
        d.setBounds(0, 0, w, h); d.draw(c); d.bounds = old
        return bmp
    }
}
