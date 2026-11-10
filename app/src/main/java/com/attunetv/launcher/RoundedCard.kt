package com.attunetv.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout

/**
 * App tile container. Clips its content to a rounded rect itself (outline clipping proved
 * unreliable for lifted/scaled tiles on Fire OS) and draws the focus / move ring on top,
 * inside its bounds so nothing can cut it off. The outline is still used for the soft neutral shadow.
 */
class RoundedCard @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val d = resources.displayMetrics.density
    private val radius = 14 * d
    private val clip = Path()
    private val rect = RectF()
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val dash = DashPathEffect(floatArrayOf(9 * d, 5 * d), 0f)

    /** For snapshot tests, where real focus can't be driven. */
    var forceRing = false
        set(v) { field = v; invalidate() }

    init {
        setWillNotDraw(false)
        // Android 8+ paints a translucent rectangle over focused views that have no focused
        // state of their own. On a rounded tile that square overlay looked like broken corners.
        if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        clipToOutline = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) =
                outline.setRoundRect(Rect(0, 0, view.width, view.height), radius)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clip.reset()
        clip.addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clip)
        super.draw(canvas)
        canvas.restore()

        val moving = isActivated
        if (isFocused || moving || forceRing) {
            // A dark inner keyline keeps the white focus ring visible on white app art.
            ring.pathEffect = null
            ring.color = 0xCC111923.toInt()
            val edge = 4.5f * d
            rect.set(edge, edge, width - edge, height - edge)
            canvas.drawRoundRect(rect, radius - edge, radius - edge, ring)
            ring.color = if (moving) 0xFF7AA2FF.toInt() else 0xFFFFFFFF.toInt()
            ring.pathEffect = if (moving) dash else null
            val inset = ring.strokeWidth / 2
            rect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(rect, radius - inset, radius - inset, ring)
        } else {
            // Hairline edge so light banners don't bleed into light backgrounds.
            ring.color = 0x1AFFFFFF; ring.pathEffect = null
            val sw = ring.strokeWidth; ring.strokeWidth = d
            rect.set(d / 2, d / 2, width - d / 2, height - d / 2)
            canvas.drawRoundRect(rect, radius, radius, ring)
            ring.strokeWidth = sw
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun setActivated(activated: Boolean) {
        super.setActivated(activated)
        invalidate()
    }
}
