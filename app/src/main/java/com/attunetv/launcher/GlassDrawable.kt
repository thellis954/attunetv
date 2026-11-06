package com.attunetv.launcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

/**
 * Frosted glass that works on Android 11: draws the slice of the pre-blurred wallpaper
 * that sits behind [host], then a soft white tint, a hairline rim and a top highlight.
 */
class GlassDrawable(
    private val host: View,
    private val radiusPx: Float,
    private val tintAlpha: Int = 0x24,
) : Drawable() {

    var focused = false
        set(v) { field = v; invalidateSelf() }

    private val path = Path()
    private val rect = RectF()
    private val loc = IntArray(2)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val d = host.resources.displayMetrics.density
        rect.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        path.reset(); path.addRoundRect(rect, radiusPx, radiusPx, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(path)
        val blurred = Wallpaper.current?.blurred
        val root = host.rootView
        if (blurred != null && root.width > 0) {
            host.getLocationInWindow(loc)
            val sx = host.scaleX; val sy = host.scaleY
            canvas.save()
            canvas.scale(1 / sx, 1 / sy, host.width / 2f, host.height / 2f)
            canvas.drawBitmap(blurred, null,
                RectF(-loc[0].toFloat(), -loc[1].toFloat(), (root.width - loc[0]).toFloat(), (root.height - loc[1]).toFloat()),
                bmpPaint)
            canvas.restore()
        }
        fill.color = if (focused) 0xF2FFFFFF.toInt() else (tintAlpha shl 24) or 0xFFFFFF
        canvas.drawRect(rect, fill)
        canvas.restore()

        rim.strokeWidth = d
        rim.color = if (focused) 0xFFFFFFFF.toInt() else 0x3DFFFFFF
        rect.inset(d / 2, d / 2)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, rim)
        // Top-edge highlight that fades out downwards, for a lit-glass feel.
        shine.strokeWidth = d
        shine.shader = LinearGradient(0f, rect.top, 0f, rect.top + radiusPx * 1.5f,
            0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radiusPx, radiusPx, shine)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
