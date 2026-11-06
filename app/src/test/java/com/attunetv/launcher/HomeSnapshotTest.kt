package com.attunetv.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test

/**
 * Renders the real layouts + adapters at Fire TV's 1080p/xhdpi canvas so the design can be
 * reviewed without a TV. Run: ./gradlew recordPaparazziDebug  → app/src/test/snapshots/images
 */
class HomeSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1920, screenHeight = 1080, density = Density.XHIGH, xdpi = 320, ydpi = 320,
            orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
        ),
        theme = "Theme.AttuneTV",
        maxPercentDifference = 1.0,
    )

    private fun wallpaper() {
        val sharp = javaClass.classLoader!!.getResourceAsStream("wall.jpg").use { BitmapFactory.decodeStream(it) }
        Wallpaper.current = Wallpaper.Photo(sharp, Blur.box(Bitmap.createScaledBitmap(sharp, 192, 108, true), 4, 3), "")
    }

    private fun wordmark(bg: Int, fg: Int, text: String, size: Float = 60f): Drawable {
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(b); c.drawColor(bg)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; textSize = size; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
        }
        c.drawText(text, 160f, 90f - (p.descent() + p.ascent()) / 2, p)
        return BitmapDrawable(paparazzi.resources, b)
    }

    private fun app(label: String, banner: Drawable?, iconColor: Int = Color.GRAY): AppEntry {
        val icon = banner ?: paparazzi.context.getDrawable(R.drawable.ic_mark)!!.mutate().also { it.setTint(iconColor) }
        return AppEntry("pkg/$label", "pkg.$label", label, banner, icon, Intent(), false,
            if (banner != null) Colors.dominant(banner) else iconColor)
    }

    private val favs by lazy {
        listOf(
            app("Netflix", wordmark(Color.BLACK, 0xFFE50914.toInt(), "NETFLIX")),
            app("Prime Video", wordmark(0xFF0F79F2.toInt(), Color.WHITE, "prime video", 46f)),
            app("YouTube", wordmark(Color.WHITE, 0xFF111111.toInt(), "YouTube", 54f)),
            app("Disney+", wordmark(0xFF10287A.toInt(), Color.WHITE, "Disney+", 54f)),
            app("Max", wordmark(0xFF2B17B8.toInt(), Color.WHITE, "max", 64f)),
        )
    }
    private val others by lazy {
        listOf(
            app("Plex", wordmark(0xFF1F1F1F.toInt(), 0xFFE5A00D.toInt(), "plex", 60f)),
            app("Spotify", null, 0xFF1ED760.toInt()),
            app("Downloader", null, 0xFFF57C00.toInt()),
            app("Silk Browser", null, 0xFF3B82F6.toInt()),
            app("Photos", null, 0xFFEC4899.toInt()),
            app("Peacock", null, 0xFF9CA3AF.toInt()),
            app("Paramount+", null, 0xFF0064FF.toInt()),
            app("Hulu", null, 0xFF1CE783.toInt()),
            app("Pluto TV", null, 0xFFFFD60A.toInt()),
            app("Fire TV Settings", null, 0xFF94A3B8.toInt()),
        )
    }

    private fun home(): View {
        wallpaper()
        Widgets.weather = WeatherData("Fort Lauderdale", 78, 2, true,
            listOf(DayForecast("2026-11-06", 2, 81, 70, 10)), true, 0)
        val v = paparazzi.inflate<View>(R.layout.activity_home)
        val d = v.resources.displayMetrics.density
        v.findViewById<ImageView>(R.id.wall).setImageBitmap(Wallpaper.current!!.sharp)
        v.findViewById<View>(R.id.glow).visibility = View.GONE
        v.findViewById<TextView>(R.id.greeting).text = "GOOD AFTERNOON, TOM"
        v.findViewById<TextView>(R.id.focus_title).text = "Netflix"
        v.findViewById<TextView>(R.id.focus_sub).text = "Opened yesterday"
        v.findViewById<View>(R.id.dock).background = GlassDrawable(v.findViewById(R.id.dock), 28 * d)
        listOf(R.id.chip_weather, R.id.chip_cal).forEach { id ->
            v.findViewById<View>(id).apply { background = GlassDrawable(this, 14 * d); visibility = View.VISIBLE }
        }
        v.findViewById<TextView>(R.id.wx_icon).text = "⛅"
        v.findViewById<TextView>(R.id.wx_temp).text = "78°"
        v.findViewById<TextView>(R.id.wx_detail).text = "Fort Lauderdale · H 81° L 70°"
        v.findViewById<TextView>(R.id.cal_title).text = "Soccer practice"
        v.findViewById<TextView>(R.id.cal_when).text = "· Today · 6:00 PM"
        listOf(R.id.btn_inputs, R.id.btn_ambient, R.id.btn_settings).forEach { id ->
            v.findViewById<ImageButton>(id).apply { background = GlassDrawable(this, 20 * d) }
        }
        v.findViewById<RecyclerView>(R.id.favorites).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = AppAdapter({}, {}, {}).also { it.submit(favs) }
        }
        v.findViewById<RecyclerView>(R.id.peek).apply {
            layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            adapter = AppAdapter({}, {}, {}).also { it.submit(others) }
        }
        return v
    }

    @Test fun home_photo() {
        val v = home()
        val d = v.resources.displayMetrics.density
        v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                v.viewTreeObserver.removeOnPreDrawListener(this)
                val first = v.findViewById<RecyclerView>(R.id.favorites).getChildAt(0) ?: return true
                first.scaleX = 1.08f; first.scaleY = 1.08f; first.translationZ = 22 * d
                first.findViewById<RoundedCard>(R.id.card).forceRing = true
                return true
            }
        })
        paparazzi.snapshot(v)
    }

    @Test fun all_apps() {
        val v = home()
        val d = v.resources.displayMetrics.density
        v.findViewById<View>(R.id.home_layer).visibility = View.INVISIBLE
        v.findViewById<ImageView>(R.id.wall_blur).apply {
            setImageDrawable(BitmapDrawable(resources, Wallpaper.current!!.blurred).apply { isFilterBitmap = true })
            setColorFilter(0x73000000, android.graphics.PorterDuff.Mode.SRC_ATOP)
            alpha = 1f
        }
        v.findViewById<View>(R.id.apps_layer).apply { visibility = View.VISIBLE; alpha = 1f }
        v.findViewById<TextView>(R.id.apps_title).text = "Downloader"
        v.findViewById<TextView>(R.id.apps_status).text = "3:42 PM · 78°"
        v.findViewById<TextView>(R.id.apps_label).text = "ALL APPS · 15"
        v.findViewById<RecyclerView>(R.id.grid).apply {
            layoutManager = GridLayoutManager(context, 5)
            val wide = run {
                val b = Bitmap.createBitmap(400, 180, Bitmap.Config.ARGB_8888)
                val c = Canvas(b); c.drawColor(0xFFE6177A.toInt())
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 70f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
                c.drawText("miniTV", 200f, 115f, p); BitmapDrawable(paparazzi.resources, b)
            }
            adapter = AppAdapter({}, {}, {}).also {
                it.submit(listOf(app("miniTV", wide)) + favs.drop(1) + others)
            }
        }
        // Simulate focus on the first tile: ring visible + lifted.
        v.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                v.viewTreeObserver.removeOnPreDrawListener(this)
                val first = v.findViewById<RecyclerView>(R.id.grid).getChildAt(0) ?: return true
                first.scaleX = 1.08f; first.scaleY = 1.08f; first.translationZ = 22 * d
                first.findViewById<RoundedCard>(R.id.card).forceRing = true
                return true
            }
        })
        paparazzi.snapshot(v)
    }

    @Test fun app_menu() {
        val v = home() as android.widget.FrameLayout
        val d = v.resources.displayMetrics.density
        val ctx = v.context
        v.addView(View(ctx).apply { setBackgroundColor(0x66000000) })
        val panel = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding((10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt(), (10 * d).toInt())
            minimumWidth = (320 * d).toInt()
        }
        panel.background = GlassDrawable(panel, 22 * d, tintAlpha = 0x99, tintColor = 0x12161E)
        panel.addView(TextView(ctx).apply {
            text = "Netflix"; textSize = 20f; setTextColor(Color.WHITE); fontVariationSettings = "'wght' 620"
            setPadding((14 * d).toInt(), 0, 0, 0)
        })
        panel.addView(TextView(ctx).apply {
            text = "In your dock"; textSize = 12f; setTextColor(0xB3FFFFFF.toInt())
            setPadding((14 * d).toInt(), (2 * d).toInt(), 0, (10 * d).toInt())
        })
        listOf("Open" to R.drawable.ic_open, "Remove from Favorites" to R.drawable.ic_unpin,
            "Move" to R.drawable.ic_move, "App info" to R.drawable.ic_info).forEachIndexed { i, (label, ic) ->
            val row = android.widget.Button(ctx).apply {
                text = label; isAllCaps = false; textSize = 15f; fontVariationSettings = "'wght' 560"
                gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
                stateListAnimator = null; minHeight = 0; minimumHeight = 0
                setPadding((14 * d).toInt(), 0, (14 * d).toInt(), 0)
                val focused = i == 0
                setTextColor(if (focused) 0xFF0B1020.toInt() else Color.WHITE)
                background = if (focused) android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 12 * d; setColor(0xF2FFFFFF.toInt()) } else null
                compoundDrawablePadding = (12 * d).toInt()
                ctx.getDrawable(ic)!!.mutate().let { dr ->
                    val sz = (18 * d).toInt(); dr.setBounds(0, 0, sz, sz)
                    dr.setTint(if (focused) 0xFF0B1020.toInt() else Color.WHITE)
                    setCompoundDrawablesRelative(dr, null, null, null)
                }
            }
            panel.addView(row, android.widget.LinearLayout.LayoutParams(-1, (44 * d).toInt()).apply { topMargin = (2 * d).toInt() })
        }
        v.addView(panel, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        paparazzi.snapshot(v)
    }
}
