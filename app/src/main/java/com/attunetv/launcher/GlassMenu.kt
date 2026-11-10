package com.attunetv.launcher

import android.app.Activity
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * In-window frosted-glass menu (replaces stock AlertDialogs). Lives in the activity's own
 * window so the glass can sample the blurred wallpaper behind it. Back closes it.
 */
class GlassMenu(private val activity: Activity) {

    data class Item(val label: String, val icon: Drawable? = null, val action: () -> Unit)

    private val content: FrameLayout = activity.findViewById(android.R.id.content)
    private var overlay: FrameLayout? = null
    private var restoreFocus: View? = null
    private val blocked = mutableListOf<Pair<ViewGroup, Int>>()

    val isOpen get() = overlay != null

    fun show(title: String, subtitle: String? = null, items: List<Item>) {
        if (isOpen) dismiss(animate = false)
        val d = activity.resources.displayMetrics.density
        restoreFocus = activity.currentFocus

        // Keep focus inside the menu.
        for (i in 0 until content.childCount) {
            (content.getChildAt(i) as? ViewGroup)?.let {
                blocked += it to it.descendantFocusability
                it.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }

        val scrim = FrameLayout(activity).apply {
            setBackgroundColor(0x66000000)
            alpha = 0f
            isClickable = true
            setOnClickListener { dismiss() }
        }

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            minimumWidth = (320 * d).toInt()
            elevation = 24 * d
            clipToOutline = false
        }
        panel.background = GlassDrawable(panel, 22 * d, tintAlpha = 0x99, tintColor = 0x12161E)

        panel.addView(TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            if (Build.VERSION.SDK_INT >= 26) fontVariationSettings = "'wght' 620"
            maxLines = 1
            setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
        })
        if (!subtitle.isNullOrBlank()) panel.addView(TextView(activity).apply {
            text = subtitle
            textSize = 12f
            setTextColor(0xB3FFFFFF.toInt())
            if (Build.VERSION.SDK_INT >= 26) fontVariationSettings = "'wght' 500"
            setPadding((14 * d).toInt(), (2 * d).toInt(), (14 * d).toInt(), 0)
        })
        panel.addView(View(activity), LinearLayout.LayoutParams(1, (10 * d).toInt()))

        var first: View? = null
        items.forEach { item ->
            val row = Button(activity).apply {
                text = item.label
                isAllCaps = false
                textSize = 15f
                if (Build.VERSION.SDK_INT >= 26) fontVariationSettings = "'wght' 560"
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                stateListAnimator = null
                minHeight = 0; minimumHeight = 0
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                setTextColor(activity.getColorStateList(R.color.pill_text))
                setBackgroundResource(R.drawable.menu_row_bg)
                compoundDrawablePadding = (12 * d).toInt()
                item.icon?.mutate()?.let { ic ->
                    val s = (18 * d).toInt(); ic.setBounds(0, 0, s, s)
                    setCompoundDrawablesRelative(ic, null, null, null)
                    compoundDrawableTintList = activity.getColorStateList(R.color.pill_text)
                }
                setOnClickListener { dismiss(); item.action() }
            }
            panel.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * d).toInt()).apply {
                topMargin = (2 * d).toInt()
            })
            if (first == null) first = row
        }

        val root = FrameLayout(activity)
        root.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        content.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = root

        scrim.animate().alpha(1f).setDuration(180).start()
        panel.alpha = 0f; panel.scaleX = 0.92f; panel.scaleY = 0.92f
        panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(240)
            .setInterpolator(OvershootInterpolator(1.4f)).start()
        panel.post { first?.requestFocus() }
    }

    fun dismiss(animate: Boolean = true) {
        val root = overlay ?: return
        overlay = null
        blocked.forEach { (v, f) -> v.descendantFocusability = f }
        blocked.clear()
        val finish = {
            content.removeView(root)
            restoreFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
            restoreFocus = null
        }
        if (!animate) { finish(); return }
        root.animate().alpha(0f).setDuration(140).setInterpolator(DecelerateInterpolator())
            .withEndAction { finish() }.start()
    }
}
