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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.card)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val fallback: View = view.findViewById(R.id.fallback)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val name: TextView = view.findViewById(R.id.name)
        private val spring = OvershootInterpolator(2.2f)

        init { card.clipToOutline = true }

        fun bind(app: AppEntry) {
            val r = card.resources.displayMetrics.density * 14
            if (app.banner != null) {
                banner.setImageDrawable(app.banner)
                banner.visibility = View.VISIBLE
                fallback.visibility = View.GONE
                card.background = GradientDrawable().apply { cornerRadius = r; setColor(0xFF15181F.toInt()) }
            } else {
                banner.visibility = View.GONE
                fallback.visibility = View.VISIBLE
                icon.setImageDrawable(app.icon)
                name.text = app.label
                card.background = GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(Colors.shade(app.color, 0.62f), Colors.shade(app.color, 0.32f)),
                ).apply { cornerRadius = r }
            }
            if (Build.VERSION.SDK_INT >= 28) {
                card.outlineSpotShadowColor = app.color
                card.outlineAmbientShadowColor = app.color
            }
            card.alpha = if (app.hidden) 0.4f else 1f
            card.isActivated = app.key == movingKey
            card.contentDescription = app.label

            card.setOnClickListener { onClick(app) }
            card.setOnLongClickListener { onMenu(app); true }
            card.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { onMenu(app); true } else false
            }
            card.setOnFocusChangeListener { v, hasFocus ->
                val d = v.resources.displayMetrics.density
                v.animate().cancel()
                v.animate()
                    .scaleX(if (hasFocus) 1.08f else 1f).scaleY(if (hasFocus) 1.08f else 1f)
                    .translationZ(if (hasFocus) 22 * d else 0f)
                    .setInterpolator(if (hasFocus) spring else null)
                    .setDuration(if (hasFocus) 260 else 160)
                    .start()
                if (hasFocus) onFocus(app)
            }
        }
    }
}
