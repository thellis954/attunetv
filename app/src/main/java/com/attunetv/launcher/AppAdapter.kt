package com.attunetv.launcher

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * App tiles: the app's own TV banner when it has one; otherwise a tile tinted with the
 * icon's color. Focus = springy lift + white ring + glow in the app's color.
 */
class AppAdapter(
    private val onClick: (AppEntry) -> Unit,
    private val onMenu: (AppEntry) -> Unit,
    private val onFocus: (AppEntry) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    var items: MutableList<AppEntry> = mutableListOf()
        private set

    var movingKey: String? = null
        set(value) {
            val old = field
            field = value
            items.indexOfFirst { it.key == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            items.indexOfFirst { it.key == value }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    @Suppress("NotifyDataSetChanged")
    fun submit(newItems: List<AppEntry>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = items.size

    private fun fresh(d: android.graphics.drawable.Drawable) =
        d.constantState?.newDrawable()?.mutate() ?: d

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            // Shadow/glow follows the tile's rounded shape inside the item padding.
            view.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    val p = v.paddingLeft
                    outline.setRoundRect(p, v.paddingTop, v.width - v.paddingRight, v.height - v.paddingBottom,
                        14 * v.resources.displayMetrics.density)
                }
            }
        }


        private val card: View = view.findViewById(R.id.card)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val fallback: View = view.findViewById(R.id.fallback)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val name: TextView = view.findViewById(R.id.name)
        private val spring = OvershootInterpolator(2.2f)


        fun bind(app: AppEntry) {
            val r = card.resources.displayMetrics.density * 14
            if (app.banner != null) {
                // Each ImageView needs its own Drawable instance: a shared one has a single set of
                // bounds, so two views showing the same app fight and one draws blank/tiny.
                val b = fresh(app.banner)
                banner.setImageDrawable(b)
                // Exact-ish 16:9 banners fill the tile; others are fitted (never cropped) on their color.
                val w = b.intrinsicWidth; val h = b.intrinsicHeight
                val ratio = if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
                val fills = kotlin.math.abs(ratio - 16f / 9f) < 0.06f
                banner.scaleType = if (fills) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                banner.visibility = View.VISIBLE
                fallback.visibility = View.GONE
                card.background = GradientDrawable().apply {
                    cornerRadius = r; setColor(if (fills) 0xFF15181F.toInt() else Colors.shade(app.color, 0.35f))
                }
            } else {
                banner.visibility = View.GONE
                fallback.visibility = View.VISIBLE
                icon.setImageDrawable(fresh(app.icon))
                name.text = app.label
                card.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Colors.shade(app.color, 0.62f), Colors.shade(app.color, 0.32f)),
                ).apply { cornerRadius = r }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                itemView.outlineSpotShadowColor = app.color
                itemView.outlineAmbientShadowColor = app.color
            }
            card.alpha = if (app.hidden) 0.4f else 1f
            card.isActivated = app.key == movingKey
            if (!card.isFocused) { itemView.animate().cancel(); itemView.scaleX = 1f; itemView.scaleY = 1f; itemView.translationZ = 0f }
            card.contentDescription = app.label

            card.setOnClickListener { onClick(app) }
            card.setOnLongClickListener { onMenu(app); true }
            card.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { onMenu(app); true } else false
            }
            card.setOnFocusChangeListener { v, hasFocus ->
                val d = v.resources.displayMetrics.density
                val s = if (hasFocus) 1.08f else 1f
                val z = if (hasFocus) 22 * d else 0f
                // Scale the item container, not the card: on Fire OS a scaled view loses its own
                // rounded clip (square corners on the focused tile). A scaled parent keeps it.
                val target = itemView
                target.animate().cancel()
                target.animate().scaleX(s).scaleY(s).translationZ(z)
                    .setInterpolator(if (hasFocus) spring else null)
                    .setDuration(if (hasFocus) 260 else 160).start()
                if (hasFocus) onFocus(app)
            }
        }
    }
}
