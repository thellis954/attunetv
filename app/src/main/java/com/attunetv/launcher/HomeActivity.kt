package com.attunetv.launcher

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

class HomeActivity : TvActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var favAdapter: AppAdapter
    private lateinit var peekAdapter: AppAdapter
    private lateinit var allAdapter: AppAdapter
    private lateinit var favRow: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var gridLayout: GridLayoutManager
    private lateinit var homeLayer: View
    private lateinit var appsLayer: View
    private lateinit var wall: ImageView
    private lateinit var wallBlur: ImageView
    private lateinit var glow: View
    private lateinit var focusTitle: TextView
    private lateinit var focusSub: TextView
    private lateinit var appsTitle: TextView
    private lateinit var banner: TextView
    private val glassViews = mutableListOf<View>()
    private val menu by lazy { GlassMenu(this) }

    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var inApps = false
    private var lastDockIndex = 0
    private var glowColor = 0x003A4660
    private var glowAnim: ValueAnimator? = null

    /** Reorder mode: which list and which tile is being moved. */
    private var moving: Pair<AppAdapter, String>? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reload()
    }
    private val widgetsChanged: () -> Unit = { renderWidgets() }
    private val wallpaperChanged: () -> Unit = { applyBackground() }
    private val goAmbient = Runnable {
        if (resumed && moving == null && !menu.isOpen) startActivity(Intent(this, AmbientActivity::class.java))
    }
    private val tick = object : Runnable {
        override fun run() {
            Widgets.refresh(this@HomeActivity)
            if (prefs.backgroundMode == Prefs.BG_PHOTO) Wallpaper.ensure(this@HomeActivity)
            renderWidgets()
            renderGreeting()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        repo = AppRepository(this)

        homeLayer = findViewById(R.id.home_layer)
        appsLayer = findViewById(R.id.apps_layer)
        wall = findViewById(R.id.wall)
        wallBlur = findViewById(R.id.wall_blur)
        glow = findViewById(R.id.glow)
        favRow = findViewById(R.id.favorites)
        grid = findViewById(R.id.grid)
        focusTitle = findViewById(R.id.focus_title)
        focusSub = findViewById(R.id.focus_sub)
        appsTitle = findViewById(R.id.apps_title)
        banner = findViewById(R.id.setup_banner)

        favAdapter = AppAdapter(::launch, { showMenu(it, Section.FAVORITES) }, ::onTileFocus)
        allAdapter = AppAdapter(::launch, { showMenu(it, Section.ALL) }, ::onTileFocus)
        peekAdapter = AppAdapter({}, {}, {})

        favRow.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        favRow.adapter = favAdapter
        findViewById<RecyclerView>(R.id.peek).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, RecyclerView.HORIZONTAL, false)
            adapter = peekAdapter
        }
        gridLayout = GridLayoutManager(this, columnsFor())
        grid.layoutManager = gridLayout
        grid.adapter = allAdapter
        listOf(favRow, grid).forEach { it.itemAnimator?.changeDuration = 0 }

        // Glass surfaces
        val d = resources.displayMetrics.density
        glass(findViewById(R.id.dock), 28 * d)
        glass(findViewById(R.id.chip_weather), 14 * d)
        glass(findViewById(R.id.chip_cal), 14 * d)
        listOf(R.id.btn_inputs, R.id.btn_ambient, R.id.btn_settings).forEach { id ->
            val b = findViewById<ImageButton>(id)
            val g = glass(b, 20 * d)
            b.setOnFocusChangeListener { _, f ->
                g.focused = f
                b.imageTintList = android.content.res.ColorStateList.valueOf(if (f) 0xFF0B0E14.toInt() else 0xFFFFFFFF.toInt())
                b.animate().scaleX(if (f) 1.1f else 1f).scaleY(if (f) 1.1f else 1f).setDuration(160).start()
                if (f) { focusTitle.text = b.contentDescription; focusSub.text = "" }
            }
        }

        findViewById<View>(R.id.btn_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.btn_ambient).setOnClickListener { startActivity(Intent(this, AmbientActivity::class.java)) }
        findViewById<View>(R.id.btn_inputs).setOnClickListener { showInputs() }
        findViewById<View>(R.id.btn_search).apply {
            setOnClickListener { startActivity(Intent(this@HomeActivity, SearchActivity::class.java)) }
            setOnFocusChangeListener { _, focused ->
                if (focused) { focusTitle.setText(R.string.search); focusSub.setText(R.string.search_scope) }
            }
        }
        findViewById<View>(R.id.btn_chat).apply {
            setOnClickListener { startActivity(Intent(this@HomeActivity,ChatSettingsActivity::class.java)) }
            setOnFocusChangeListener { _,focused -> if(focused) {focusTitle.text="Ask with AttuneTV";focusSub.text="ChatGPT on your TV"} }
        }
        banner.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        registerReceiver(packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        })
    }

    private fun glass(v: View, radius: Float): GlassDrawable =
        GlassDrawable(v, radius).also { v.background = it; glassViews += v }

    override fun onResume() {
        super.onResume()
        resumed = true
        banner.visibility = if (HomeButtonService.isEnabled(this)) View.GONE else View.VISIBLE
        val fmt = if (prefs.clock24) "HH:mm" else "h:mm"
        findViewById<TextClock>(R.id.clock).apply { format12Hour = fmt; format24Hour = fmt }
        findViewById<View>(R.id.ampm).visibility = if (prefs.clock24) View.GONE else View.VISIBLE
        Widgets.addListener(widgetsChanged)
        Wallpaper.addListener(wallpaperChanged)
        applyBackground()
        handler.post(tick)
        reload()
        resetIdle()
    }

    override fun onPause() {
        resumed = false
        Widgets.removeListener(widgetsChanged)
        Wallpaper.removeListener(wallpaperChanged)
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            menu.isOpen -> menu.dismiss()
            moving != null -> endMove()
            inApps -> exitApps()
            else -> focusDock(0)
        }
    }

    /** Home pressed while already home: return to the dock. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (menu.isOpen) menu.dismiss(animate = false)
        if (moving != null) endMove()
        if (inApps) exitApps(animate = false)
        focusDock(0)
    }

    private fun columnsFor(): Int {
        val r = resources
        val avail = r.displayMetrics.widthPixels - (72 * r.displayMetrics.density).toInt()
        val tile = r.getDimensionPixelSize(R.dimen.card_w) + 2 * r.getDimensionPixelSize(R.dimen.tile_pad)
        return (avail / tile).coerceIn(3, 8)
    }

    // ---- Background --------------------------------------------------------

    private fun applyBackground() {
        val mode = prefs.backgroundMode
        val photo = Wallpaper.current
        if (mode == Prefs.BG_PHOTO) Wallpaper.ensure(this)
        if (mode == Prefs.BG_PHOTO && photo != null) {
            wall.setImageBitmap(photo.sharp)
            wallBlur.setImageDrawable(BitmapDrawable(resources, photo.blurred).apply { isFilterBitmap = true })
            wallBlur.setColorFilter(0x73000000, android.graphics.PorterDuff.Mode.SRC_ATOP)
            wall.background = null
            glow.visibility = View.GONE
        } else {
            wall.setImageDrawable(null)
            wallBlur.setImageDrawable(null)
            wall.background = Themes.background(prefs.themeIndex)
            glow.visibility = if (mode == Prefs.BG_THEME) View.GONE else View.VISIBLE
        }
        glassViews.forEach { it.invalidate() }
    }

    private fun setGlow(color: Int) {
        if (glow.visibility != View.VISIBLE) return
        val from = glowColor
        val to = Colors.withAlpha(color, 0x99)
        glowColor = to
        glowAnim?.cancel()
        glowAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 600
            addUpdateListener {
                val c = it.animatedValue as Int
                glow.background = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = resources.displayMetrics.widthPixels * 0.75f
                    setGradientCenter(0.28f, 0.62f)
                    colors = intArrayOf(c, Colors.withAlpha(c, 0x22), 0x00000000)
                }
            }
            start()
        }
    }

    // ---- Data ---------------------------------------------------------------

    private fun reload() {
        val apps = repo.loadApps(prefs)
        seedFavoritesIfFirstRun(apps)

        val byKey = apps.associateBy { it.key }
        val favs = prefs.favorites.mapNotNull { byKey[it] }
        val favKeys = favs.map { it.key }.toSet()
        val rest = apps.filter { it.key !in favKeys && (prefs.showHidden || !it.hidden) }

        favAdapter.submit(favs)
        allAdapter.submit(rest)
        peekAdapter.submit(rest.take(8))
        findViewById<View>(R.id.dock).visibility = if (favs.isEmpty()) View.INVISIBLE else View.VISIBLE
        findViewById<TextView>(R.id.apps_label).text = getString(R.string.all_apps_count, rest.size)

        if (currentFocus == null || currentFocus?.isAttachedToWindow != true) {
            if (inApps) focusGrid(0) else focusDock(lastDockIndex)
        }
    }

    private fun seedFavoritesIfFirstRun(apps: List<AppEntry>) {
        if (prefs.seededDefaults) return
        val byPkg = apps.associateBy { it.packageName }
        prefs.favorites = AppRepository.DEFAULT_FAVORITE_PACKAGES.mapNotNull { byPkg[it]?.key }.distinct().take(8)
        prefs.seededDefaults = true
    }

    private fun renderGreeting() {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val g = getString(when (h) { in 4..11 -> R.string.greet_morning; in 12..16 -> R.string.greet_afternoon; else -> R.string.greet_evening })
        findViewById<TextView>(R.id.greeting).text =
            (if (prefs.userName.isNotBlank()) "$g, ${prefs.userName}" else g).uppercase()
    }

    private fun renderWidgets() {
        val chipWx = findViewById<View>(R.id.chip_weather)
        val w = Widgets.weather
        val clockTime = Format.time(System.currentTimeMillis(), prefs.clock24)
        if (prefs.showWeather && w != null) {
            val (emoji, _) = WeatherRepository.describe(w.code, w.isDay)
            findViewById<TextView>(R.id.wx_icon).text = emoji
            findViewById<TextView>(R.id.wx_temp).text = "${w.temp}${w.unit}"
            val today = w.days.firstOrNull()
            val hiLo = today?.let { " · H ${it.hi}° L ${it.lo}°" }.orEmpty()
            findViewById<TextView>(R.id.wx_detail).text = "${w.place}$hiLo"
            chipWx.visibility = View.VISIBLE
            findViewById<TextView>(R.id.apps_status).text = "$clockTime · ${w.temp}${w.unit}"
        } else {
            chipWx.visibility = View.GONE
            findViewById<TextView>(R.id.apps_status).text = clockTime
        }

        val chipCal = findViewById<View>(R.id.chip_cal)
        val ev = if (prefs.showCalendar) Widgets.upcoming(1).firstOrNull() else null
        if (ev != null) {
            findViewById<TextView>(R.id.cal_title).text = ev.title
            findViewById<TextView>(R.id.cal_when).text = "· " + Format.eventWhen(ev, prefs.clock24)
            chipCal.visibility = View.VISIBLE
        } else chipCal.visibility = View.GONE
        glassViews.forEach { it.invalidate() }
    }

    private fun onTileFocus(app: AppEntry) {
        if (inApps) {
            appsTitle.text = app.label
        } else {
            focusTitle.text = app.label
            val t = prefs.lastLaunched(app.key)
            focusSub.text = if (t > 0) getString(R.string.sub_opened,
                DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase())
            else getString(R.string.sub_new)
            lastDockIndex = favAdapter.items.indexOfFirst { it.key == app.key }.coerceAtLeast(0)
        }
        setGlow(app.color)
    }

    // ---- Home <-> All apps --------------------------------------------------

    private fun enterApps() {
        if (inApps) return
        inApps = true
        val d = resources.displayMetrics.density
        val ease = DecelerateInterpolator(2f)
        homeLayer.animate().translationY(-70 * d).alpha(0f).setDuration(260).setInterpolator(ease)
            .withEndAction { if (inApps) homeLayer.visibility = View.INVISIBLE }.start()
        appsLayer.visibility = View.VISIBLE
        appsLayer.translationY = 90 * d
        appsLayer.animate().translationY(0f).alpha(1f).setDuration(320).setInterpolator(ease).start()
        wallBlur.animate().alpha(1f).setDuration(320).start()
        grid.scrollToPosition(0)
        focusGrid(0)
    }

    private fun exitApps(animate: Boolean = true) {
        if (!inApps) return
        inApps = false
        val d = resources.displayMetrics.density
        val dur = if (animate) 260L else 0L
        val ease = DecelerateInterpolator(2f)
        homeLayer.visibility = View.VISIBLE
        homeLayer.animate().translationY(0f).alpha(1f).setDuration(dur).setInterpolator(ease).start()
        appsLayer.animate().translationY(90 * d).alpha(0f).setDuration(dur).setInterpolator(ease)
            .withEndAction { if (!inApps) appsLayer.visibility = View.GONE }.start()
        wallBlur.animate().alpha(0f).setDuration(dur).start()
        focusDock(lastDockIndex)
    }

    private fun focusDock(index: Int) {
        if (favAdapter.itemCount == 0) { findViewById<View>(R.id.btn_settings).requestFocus(); return }
        val i = index.coerceIn(0, favAdapter.itemCount - 1)
        favRow.scrollToPosition(i)
        favRow.post { favRow.findViewHolderForAdapterPosition(i)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
    }

    private fun focusGrid(index: Int) {
        grid.post { grid.findViewHolderForAdapterPosition(index)?.itemView?.findViewById<View>(R.id.card)?.requestFocus() }
    }

    private fun gridPositionOf(v: View?): Int {
        v ?: return -1
        val item = grid.findContainingItemView(v) ?: return -1
        return grid.getChildAdapterPosition(item)
    }

    // ---- Actions --------------------------------------------------------------

    private fun launch(app: AppEntry) {
        if (moving != null) { endMove(); return }
        prefs.markLaunched(app.key)
        prefs.recent = listOf(app.key) + (prefs.recent - app.key)
        try {
            startActivity(app.launchIntent)
        } catch (e: Exception) {
            packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
                ?: Toast.makeText(this, getString(R.string.cant_open, app.label), Toast.LENGTH_SHORT).show()
        }
    }

    private enum class Section { FAVORITES, ALL }

    private fun icon(id: Int) = getDrawable(id)

    private fun showMenu(app: AppEntry, section: Section) {
        val isFav = app.key in prefs.favorites
        val items = mutableListOf<GlassMenu.Item>()
        items += GlassMenu.Item(getString(R.string.action_open), icon(R.drawable.ic_open)) { launch(app) }
        items += if (isFav) {
            GlassMenu.Item(getString(R.string.action_unpin), icon(R.drawable.ic_unpin)) {
                prefs.favorites = prefs.favorites - app.key; reload()
            }
        } else {
            GlassMenu.Item(getString(R.string.action_pin), icon(R.drawable.ic_pin)) {
                prefs.favorites = prefs.favorites + app.key
                prefs.hidden = prefs.hidden - app.key
                reload()
            }
        }
        items += GlassMenu.Item(getString(R.string.action_move), icon(R.drawable.ic_move)) {
            startMove(if (section == Section.FAVORITES) favAdapter else allAdapter, app.key)
        }
        if (!isFav) {
            items += GlassMenu.Item(
                getString(if (app.hidden) R.string.action_unhide else R.string.action_hide),
                icon(if (app.hidden) R.drawable.ic_show else R.drawable.ic_hide),
            ) {
                if (app.hidden) { prefs.hidden = prefs.hidden - app.key; prefs.unhidden = prefs.unhidden + app.key }
                else { prefs.hidden = prefs.hidden + app.key; prefs.unhidden = prefs.unhidden - app.key }
                reload()
            }
        }
        items += GlassMenu.Item(getString(R.string.action_info), icon(R.drawable.ic_info)) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        menu.show(app.label, if (isFav) getString(R.string.sub_favorite) else null, items)
    }

    private fun showInputs() {
        val items = Inputs.list(this).map { input ->
            GlassMenu.Item(input.label, icon(R.drawable.ic_hdmi)) {
                try { startActivity(input.intent) }
                catch (_: Exception) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show() }
            }
        } + GlassMenu.Item(getString(R.string.inputs_amazon), icon(R.drawable.ic_live)) { openAmazonHome(this) }
        menu.show(getString(R.string.inputs), null, items)
    }

    // ---- Keys: Down from the dock opens All apps; Up from the top row closes it ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (moving != null) return handleMoveKey(event)
        if (menu.isOpen) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focus = currentFocus
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> if (!inApps) {
                    val inDock = focus != null && favRow.findContainingItemView(focus) != null
                    val onTopRow = focus != null && focus.id in listOf(R.id.btn_search, R.id.btn_chat, R.id.btn_inputs, R.id.btn_ambient, R.id.btn_settings)
                    if (inDock || (onTopRow && favAdapter.itemCount == 0)) { enterApps(); return true }
                    if (onTopRow) { focusDock(lastDockIndex); return true }
                }
                KeyEvent.KEYCODE_DPAD_UP -> if (inApps) {
                    val pos = gridPositionOf(focus)
                    if (pos in 0 until gridLayout.spanCount) { exitApps(); return true }
                } else if (focus != null && favRow.findContainingItemView(focus) != null) {
                    findViewById<View>(R.id.btn_search).requestFocus()
                    return true
                }
                KeyEvent.KEYCODE_SEARCH -> {
                    startActivity(Intent(this, SearchActivity::class.java)); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

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

    private fun handleMoveKey(event: KeyEvent): Boolean {
        val (adapter, key) = moving ?: return false
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
