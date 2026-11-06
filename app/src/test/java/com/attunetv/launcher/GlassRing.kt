package com.attunetv.launcher

import android.content.res.Resources
import android.graphics.drawable.GradientDrawable

object GlassRing {
    fun focused(r: Resources) = GradientDrawable().apply {
        cornerRadius = 14 * r.displayMetrics.density
        setStroke((2.5f * r.displayMetrics.density).toInt(), 0xFFFFFFFF.toInt())
    }
}
