package com.attunetv.launcher

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Renders app tiles. Uses the app's TV banner when present, otherwise icon + name. */
class AppAdapter(
    private val large: Boolean,
    private val onClick: (AppEntry) -> Unit,
    private val onMenu: (AppEntry, Int) -> Unit,
    private val onFocus: (AppEntry?) -> Unit,
) : RecyclerView.Adapter<AppAdapter.Holder>() {

    var items: MutableList<AppEntry> = mutableListOf()
        private set

    /** Key of the tile currently being moved (shows a "moving" outline). */
    var movingKey: String? = null
        set(value) {
            val old = field
            field = value
            items.indexOfFirst { it.key == old }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
            items.indexOfFirst { it.key == value }.takeIf { it >= 0 }?.let { notifyItemChanged(it) }
        }

    fun submit(newItems: List<AppEntry>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        if (large) {
            val r = parent.resources
            val w = r.getDimensionPixelSize(R.dimen.dock_card_w)
            v.findViewById<View>(R.id.card).layoutParams.apply { width = w; height = r.getDimensionPixelSize(R.dimen.dock_card_h) }
            v.findViewById<View>(R.id.caption).layoutParams.width = w
        }
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: View = view.findViewById(R.id.card)
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val name: TextView = view.findViewById(R.id.name)
        private val caption: TextView = view.findViewById(R.id.caption)

        init { card.clipToOutline = true }

        fun bind(app: AppEntry) {
            if (app.banner != null) {
                banner.setImageDrawable(app.banner)
                banner.visibility = View.VISIBLE
                icon.visibility = View.GONE
                name.visibility = View.GONE
            } else {
                banner.visibility = View.GONE
                icon.setImageDrawable(app.icon)
                icon.visibility = View.VISIBLE
                name.text = app.label
                name.visibility = View.VISIBLE
            }
            caption.text = app.label
            card.alpha = if (app.hidden) 0.4f else 1f
            card.isActivated = app.key == movingKey

            card.setOnClickListener { onClick(app) }
            card.setOnLongClickListener {
                onMenu(app, bindingAdapterPosition); true
            }
            card.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) {
                    onMenu(app, bindingAdapterPosition); true
                } else false
            }
            card.setOnFocusChangeListener { v, hasFocus ->
                val s = if (hasFocus) 1.12f else 1f
                v.animate().scaleX(s).scaleY(s).translationZ(if (hasFocus) 12f else 0f)
                    .setDuration(140).start()
                caption.animate().alpha(if (hasFocus) 1f else 0f).setDuration(140).start()
                if (hasFocus) onFocus(app)
            }
        }
    }
}
