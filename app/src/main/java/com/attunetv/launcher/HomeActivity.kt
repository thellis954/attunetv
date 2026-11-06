package com.attunetv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var favAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
    private lateinit var favRow: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var favLabel: TextView
    private lateinit var focusTitle: TextView
    private lateinit var banner: TextView

    /** Reorder mode: which list and which tile is being moved. */
    private var moving: Pair<AppAdapter, String>? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        repo = AppRepository(this)

        favRow = findViewById(R.id.favorites)
        grid = findViewById(R.id.grid)
        favLabel = findViewById(R.id.favorites_label)
        focusTitle = findViewById(R.id.focus_title)
        banner = findViewById(R.id.setup_banner)

        favAdapter = AppAdapter(::launch, { app, _ -> showMenu(app, favorite = true) }, ::onTileFocus)
        allAdapter = AppAdapter(::launch, { app, _ -> showMenu(app, favorite = false) }, ::onTileFocus)

        favRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favRow.adapter = favAdapter
        grid.layoutManager = GridLayoutManager(this, GRID_COLUMNS)
        grid.adapter = allAdapter
        listOf(favRow, grid).forEach { it.itemAnimator?.changeDuration = 0 }

        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        banner.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        window.decorView.background = Themes.background(prefs.themeIndex)
        banner.visibility = if (HomeButtonService.isEnabled(this)) View.GONE else View.VISIBLE
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    /** Pressing Back on the home screen should do nothing (we ARE home). */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (moving != null) endMove() else grid.smoothScrollToPosition(0)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already home: jump back to the first favorite.
        if (moving == null) {
            favRow.scrollToPosition(0)
            grid.scrollToPosition(0)
            favRow.post { (favRow.getChildAt(0) ?: grid.getChildAt(0))?.findViewById<View>(R.id.card)?.requestFocus() }
        }
    }

    private fun reload() {
        val apps = repo.loadApps(prefs)
        seedFavoritesIfFirstRun(apps)

        val showHidden = prefs.showHidden
        val byKey = apps.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }
        val favKeys = favs.map { it.key }.toSet()
        val rest = apps.filter { it.key !in favKeys && (showHidden || !it.hidden) }

        favAdapter.submit(favs)
        allAdapter.submit(rest)
        favLabel.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE
        favRow.visibility = favLabel.visibility

        if (currentFocus == null) {
            (if (favs.isNotEmpty()) favRow else grid).post {
                val rv = if (favs.isNotEmpty()) favRow else grid
                rv.getChildAt(0)?.findViewById<View>(R.id.card)?.requestFocus()
            }
        }
    }

    private fun seedFavoritesIfFirstRun(apps: List<AppEntry>) {
        if (prefs.seededDefaults) return
        val byPkg = apps.associateBy { it.packageName }
        prefs.favorites = AppRepository.DEFAULT_FAVORITE_PACKAGES.mapNotNull { byPkg[it]?.key }.take(8)
        prefs.seededDefaults = true
    }

    private fun onTileFocus(app: AppEntry?) {
        focusTitle.text = app?.label.orEmpty()
    }

    private fun launch(app: AppEntry) {
        if (moving != null) { endMove(); return }
        try {
            startActivity(app.launchIntent)
        } catch (e: Exception) {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                ?: Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(app: AppEntry, favorite: Boolean) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.action_open) to { launch(app) }
        if (favorite) {
            actions += getString(R.string.action_unpin) to {
                prefs.favorites = prefs.favorites - app.key; reload()
            }
        } else {
            actions += getString(R.string.action_pin) to {
                prefs.favorites = prefs.favorites + app.key
                prefs.hidden = prefs.hidden - app.key
                reload()
            }
        }
        actions += getString(R.string.action_move) to { startMove(if (favorite) favAdapter else allAdapter, app.key) }
        if (!favorite) {
            actions += (if (app.hidden) getString(R.string.action_unhide) else getString(R.string.action_hide)) to {
                prefs.hidden = if (app.hidden) prefs.hidden - app.key else prefs.hidden + app.key
                reload()
            }
        }
        actions += getString(R.string.action_info) to {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    // ---- Reorder mode -------------------------------------------------------

    private fun startMove(adapter: AppAdapter, key: String) {
        moving = adapter to key
        adapter.movingKey = key
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show()
    }

    private fun endMove() {
        val (adapter, _) = moving ?: return
        adapter.movingKey = null
        moving = null
        if (adapter === favAdapter) {
            prefs.favorites = favAdapter.items.map { it.key }
        } else {
            // Persist the full visible order; hidden apps keep their old relative spot at the end.
            prefs.appOrder = allAdapter.items.map { it.key }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val (adapter, key) = moving ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val from = adapter.items.indexOfFirst { it.key == key }
        val step = if (adapter === allAdapter) GRID_COLUMNS else 0
        val to = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> from - 1
            KeyEvent.KEYCODE_DPAD_RIGHT -> from + 1
            KeyEvent.KEYCODE_DPAD_UP -> if (step > 0) from - step else from
            KeyEvent.KEYCODE_DPAD_DOWN -> if (step > 0) from + step else from
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU -> { endMove(); return true }
            else -> return true
        }.coerceIn(0, adapter.items.lastIndex)
        if (to != from) {
            adapter.move(from, to)
            val rv = if (adapter === favAdapter) favRow else grid
            rv.scrollToPosition(to)
            rv.post { rv.findViewHolderForAdapterPosition(to)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
        }
        return true
    }

    companion object {
        const val GRID_COLUMNS = 5
    }
}
