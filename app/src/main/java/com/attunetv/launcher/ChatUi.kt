package com.attunetv.launcher

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

/** Explicit remote focus; the launcher theme intentionally disables Android's default halo. */
object ChatUi {
    fun style(button: Button) = button.apply {
        val d=resources.displayMetrics.density
        fun shape(focused:Boolean)=GradientDrawable().apply {
            cornerRadius=12*d
            setColor(if(focused) Color.rgb(213,243,229) else Color.argb(180,29,46,56))
            setStroke((if(focused) 2*d else d).toInt(),if(focused) Color.WHITE else Color.argb(90,190,218,210))
        }
        background=StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused),shape(true))
            addState(intArrayOf(android.R.attr.state_pressed),shape(true))
            addState(intArrayOf(),shape(false))
        }
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused),intArrayOf(android.R.attr.state_pressed),intArrayOf()),
            intArrayOf(Color.rgb(14,34,28),Color.rgb(14,34,28),Color.WHITE)))
        isFocusable=true;isFocusableInTouchMode=true;isAllCaps=false
        setPadding((12*d).toInt(),0,(12*d).toInt(),0)
    }
}
