package com.attunetv.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

object Colors {
    private const val FALLBACK = 0xFF3A4660.toInt()

    /** The most "brand-like" color in a drawable: saturated, not near-white/black. */
    fun dominant(d: Drawable): Int = try {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val old = d.copyBounds()
        d.setBounds(0, 0, n, n); d.draw(Canvas(bmp)); d.bounds = old
        val hsv = FloatArray(3)
        var r = 0.0; var g = 0.0; var b = 0.0; var wsum = 0.0
        for (y in 0 until n) for (x in 0 until n) {
            val c = bmp.getPixel(x, y)
            if (Color.alpha(c) < 200) continue
            Color.colorToHSV(c, hsv)
            if (hsv[2] < 0.15f || (hsv[1] < 0.12f && hsv[2] > 0.85f)) continue
            val w = (hsv[1] * hsv[1] * hsv[2]).toDouble() + 0.01
            r += Color.red(c) * w; g += Color.green(c) * w; b += Color.blue(c) * w; wsum += w
        }
        if (wsum < 0.5) FALLBACK else Color.rgb((r / wsum).toInt(), (g / wsum).toInt(), (b / wsum).toInt())
    } catch (_: Exception) { FALLBACK }

    fun shade(c: Int, factor: Float): Int =
        Color.rgb((Color.red(c) * factor).toInt(), (Color.green(c) * factor).toInt(), (Color.blue(c) * factor).toInt())

    fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)
}
