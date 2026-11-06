package com.attunetv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var favAdapter: AppAdapter
    private lateinit var recentAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
    private lateinit var favRow: RecyclerView
    private lateinit var recentRow: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var gridLayout: GridLayoutManager
    private lateinit var scroller: ScrollView
    private lateinit var focusTitle: TextView
    private lateinit var banner: TextView
    private lateinit var backdrop: Backdrop

    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false

    /** Reorder mode: which list and which tile is being moved. */
    private var moving: Pair<AppAdapter, String>? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reload()
    }
    private val widgetsChanged: () -> Unit = { renderWidgets() }
    private val goAmbient = Runnable {
        if (resumed && moving == null) startActivity(Intent(this, AmbientActivity::class.java))
    }
    private val tick = object : Runnable {
        override fun run() {
            Widgets.refresh(this@HomeActivity)
            renderWidgets() // keeps "In 5 min" labels fresh
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        repo = AppRepository(this)

        favRow = findViewById(R.id.favorites)
        recentRow = findViewById(R.id.recent)
        grid = findViewById(R.id.grid)
        scroller = findViewById(R.id.scroller)
        focusTitle = findViewById(R.id.focus_title)
        banner = findViewById(R.id.setup_banner)
        backdrop = Backdrop(findViewById(R.id.backdrop_a), findViewById(R.id.backdrop_b), resources)

        favAdapter = AppAdapter(true, ::launch, { app, _ -> showMenu(app, Section.FAVORITES) }, ::onTileFocus)
        recentAdapter = AppAdapter(false, ::launch, { app, _ -> showMenu(app, Section.RECENT) }, ::onTileFocus)
        allAdapter = AppAdapter(false, ::launch, { app, _ -> showMenu(app, Section.ALL) }, ::onTileFocus)

        favRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favRow.adapter = favAdapter
        recentRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        recentRow.adapter = recentAdapter
        gridLayout = GridLayoutManager(this, columnsFor())
        grid.layoutManager = gridLayout
        grid.adapter = allAdapter
        grid.isNestedScrollingEnabled = false
        listOf(favRow, recentRow, grid).forEach { it.itemAnimator?.changeDuration = 0 }

        findViewById<View>(R.id.btn_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btn_ambient).setOnClickListener { startActivity(Intent(this, AmbientActivity::class.java)) }
        findViewById<View>(R.id.btn_inputs).setOnClickListener { showInputs() }
        banner.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        listOf(R.id.btn_settings, R.id.btn_ambient, R.id.btn_inputs).forEach { id ->
            findViewById<View>(id).setOnFocusChangeListener { _, f -> if (f) { focusTitle.text = ""; backdrop.clear() } }
        }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        window.decorView.background = Themes.background(prefs.themeIndex)
        banner.visibility = if (HomeButtonService.isEnabled(this)) View.GONE else View.VISIBLE
        findViewById<TextClock>(R.id.clock).apply {
            format12Hour = if (prefs.clock24) "HH:mm" else "h:mm"
            format24Hour = format12Hour
        }
        Widgets.addListener(widgetsChanged)
        handler.post(tick)
        renderWidgets()
        reload()
        resetIdle()
    }

    override fun onPause() {
        resumed = false
        Widgets.removeListener(widgetsChanged)
        handler.removeCallbacks(tick)
        handler.removeCallbacks(goAmbient)
        if (moving != null) endMove()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        resetIdle()
    }

    private fun resetIdle() {
        handler.removeCallbacks(goAmbient)
        val m = prefs.ambientMinutes
        if (m > 0) handler.postDelayed(goAmbient, m * 60_000L)
    }

    /** Back on the home screen: leave move mode, else scroll to top. Never exits. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (moving != null) endMove() else focusFirst()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (moving == null) focusFirst()
    }

    private fun focusFirst() {
        scroller.smoothScrollTo(0, 0)
        favRow.scrollToPosition(0)
        val rv = if (favAdapter.itemCount > 0) favRow else grid
        rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
    }

    private fun columnsFor(): Int {
        val r = resources
        val avail = r.displayMetrics.widthPixels - 2 * r.getDimensionPixelSize(R.dimen.gutter)
        val tile = r.getDimensionPixelSize(R.dimen.card_w) + 2 * r.getDimensionPixelSize(R.dimen.tile_pad)
        return (avail / tile).coerceIn(3, 8)
    }

    // ---- Data ---------------------------------------------------------------

    private fun reload() {
        val apps = repo.loadApps(prefs)
        seedFavoritesIfFirstRun(apps)

        val byKey = apps.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }
        val favKeys = favs.map { it.key }.toSet()
        val recent = prefs.recent.mapNotNull { byKey[it] }.filter { it.key !in favKeys && !it.hidden }.take(8)
        val rest = apps.filter { it.key !in favKeys && (prefs.showHidden || !it.hidden) }

        favAdapter.submit(favs)
        recentAdapter.submit(recent)
        allAdapter.submit(rest)
        findViewById<View>(R.id.dock).visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE
        val rv = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recentRow.visibility = rv
        findViewById<View>(R.id.recent_label).visibility = rv

        if (currentFocus == null || currentFocus?.isAttachedToWindow != true) focusFirst()
    }

    private fun seedFavoritesIfFirstRun(apps: List<AppEntry>) {
        if (prefs.seededDefaults) return
        val byPkg = apps.associateBy { it.packageName }
        prefs.favorites = AppRepository.DEFAULT_FAVORITE_PACKAGES.mapNotNull { byPkg[it]?.key }.distinct().take(8)
        prefs.seededDefaults = true
    }

    private fun renderWidgets() {
        val wxBox = findViewById<View>(R.id.weather_widget)
        val w = Widgets.weather
        if (prefs.showWeather && w != null) {
            val (emoji, label) = WeatherRepository.describe(w.code, w.isDay)
            findViewById<TextView>(R.id.wx_icon).text = emoji
            findViewById<TextView>(R.id.wx_temp).text = "${w.temp}${w.unit}"
            val today = w.days.firstOrNull()
            val hiLo = today?.let { " · H ${it.hi}° L ${it.lo}°" }.orEmpty()
            val rain = today?.takeIf { it.precipPct >= 30 }?.let { " · ☔ ${it.precipPct}%" }.orEmpty()
            findViewById<TextView>(R.id.wx_detail).text = "$label$hiLo$rain\n${w.place}"
            wxBox.visibility = View.VISIBLE
        } else wxBox.visibility = View.GONE

        val calBox = findViewById<View>(R.id.calendar_widget)
        val ev = if (prefs.showCalendar) Widgets.upcoming(2) else emptyList()
        if (ev.isNotEmpty()) {
            findViewById<TextView>(R.id.cal_1).text = "${ev[0].title}  ·  ${Format.eventWhen(ev[0], prefs.clock24)}"
            findViewById<TextView>(R.id.cal_2).apply {
                text = ev.getOrNull(1)?.let { "${it.title}  ·  ${Format.eventWhen(it, prefs.clock24)}" }.orEmpty()
                visibility = if (ev.size > 1) View.VISIBLE else View.GONE
            }
            calBox.visibility = View.VISIBLE
        } else if (prefs.showCalendar && prefs.calendarUrls.isNotEmpty()) {
            findViewById<TextView>(R.id.cal_1).text = getString(R.string.cal_clear)
            findViewById<TextView>(R.id.cal_2).visibility = View.GONE
            calBox.visibility = View.VISIBLE
        } else calBox.visibility = View.GONE
    }

    private fun onTileFocus(app: AppEntry?) {
        focusTitle.text = app?.label.orEmpty()
        if (prefs.dynamicBackdrop) backdrop.show(app) else backdrop.clear()
    }

    // ---- Actions --------------------------------------------------------------

    private fun launch(app: AppEntry) {
        if (moving != null) { endMove(); return }
        prefs.recent = listOf(app.key) + (prefs.recent - app.key)
        try {
            startActivity(app.launchIntent)
        } catch (e: Exception) {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                ?: Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    private enum class Section { FAVORITES, RECENT, ALL }

    private fun showMenu(app: AppEntry, section: Section) {
        val isFav = app.key in prefs.favorites
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += getString(R.string.action_open) to { launch(app) }
        if (isFav) {
            actions += getString(R.string.action_unpin) to { prefs.favorites = prefs.favorites - app.key; reload() }
        } else {
            actions += getString(R.string.action_pin) to {
                prefs.favorites = prefs.favorites + app.key
                prefs.hidden = prefs.hidden - app.key
                reload()
            }
        }
        when (section) {
            Section.FAVORITES -> actions += getString(R.string.action_move) to { startMove(favAdapter, app.key) }
            Section.ALL -> actions += getString(R.string.action_move) to { startMove(allAdapter, app.key) }
            Section.RECENT -> actions += getString(R.string.action_forget) to {
                prefs.recent = prefs.recent - app.key; reload()
            }
        }
        if (!isFav) {
            actions += (if (app.hidden) getString(R.string.action_unhide) else getString(R.string.action_hide)) to {
                prefs.hidden = if (app.hidden) prefs.hidden - app.key else prefs.hidden + app.key
                prefs.recent = prefs.recent - app.key
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

    private fun showInputs() {
        val inputs = Inputs.list(this)
        val labels = inputs.map { it.label } + getString(R.string.inputs_amazon)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.inputs)
            .setItems(labels.toTypedArray()) { _, i ->
                if (i < inputs.size) {
                    try { startActivity(inputs[i].intent) }
                    catch (_: Exception) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show() }
                } else openAmazonHome(this)
            }
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
        if (adapter === favAdapter) prefs.favorites = favAdapter.items.map { it.key }
        else prefs.appOrder = allAdapter.items.map { it.key }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val (adapter, key) = moving ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        val from = adapter.items.indexOfFirst { it.key == key }
        val step = if (adapter === allAdapter) gridLayout.spanCount else 0
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
        fun openAmazonHome(context: Context) {
            HomeButtonService.allowAmazonHome()
            val i = context.packageManager.getLaunchIntentForPackage("com.amazon.tv.launcher")
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage("com.amazon.tv.launcher")
            try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: Exception) { Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show() }
        }
    }
}
