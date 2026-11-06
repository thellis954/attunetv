package com.attunetv.launcher

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Photo screensaver: recent daily photos with a slow drift-and-zoom, a large thin
 * clock, weather, forecast and agenda. Any button returns. Opens after idling on Home.
 */
class AmbientActivity : TvActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var content: View
    private lateinit var photoA: ImageView
    private lateinit var photoB: ImageView
    private var front: ImageView? = null
    private var gallery: List<Pair<String, String>> = emptyList()
    private var index = 0
    private val update: () -> Unit = { render() }

    private val nextPhoto = object : Runnable {
        override fun run() { showNext(); handler.postDelayed(this, PHOTO_MS) }
    }

    private val drift = object : Runnable {
        override fun run() {
            // Nudge the text block around the lower-left quadrant to avoid burn-in.
            val d = resources.displayMetrics.density
            content.animate().translationX(Random.nextFloat() * 60 * d).translationY(-Random.nextFloat() * 40 * d)
                .setDuration(3000).start()
            render()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ambient)
        content = findViewById(R.id.ambient_content)
        photoA = findViewById(R.id.photo_a)
        photoB = findViewById(R.id.photo_b)
        val p = Prefs(this)
        findViewById<TextClock>(R.id.ambient_clock).apply {
            format12Hour = if (p.clock24) "HH:mm" else "h:mm"
            format24Hour = format12Hour
        }
        // Start on today's photo immediately if we have it.
        Wallpaper.current?.let { show(it.sharp, it.caption) }
        gallery = Wallpaper.gallery(this)
    }

    override fun onResume() {
        super.onResume()
        Widgets.addListener(update)
        Widgets.refresh(this)
        render()
        handler.postDelayed(drift, 60_000)
        handler.postDelayed(nextPhoto, if (front == null) 0 else PHOTO_MS)
    }

    override fun onPause() {
        Widgets.removeListener(update)
        handler.removeCallbacks(drift)
        handler.removeCallbacks(nextPhoto)
        super.onPause()
    }

    private fun showNext() {
        if (gallery.isEmpty()) return
        index = (index + 1) % gallery.size
        val (url, caption) = gallery[index]
        io.execute {
            val bmp = Wallpaper.loadGalleryImage(this, url) ?: return@execute
            handler.post { if (!isFinishing) show(bmp, caption) }
        }
    }

    private fun show(bmp: Bitmap, caption: String) {
        val next = if (front === photoA) photoB else photoA
        next.setImageBitmap(bmp)
        next.scaleX = 1.0f; next.scaleY = 1.0f; next.translationX = 0f
        next.animate().alpha(1f).setDuration(1500).start()
        // Ken Burns: slow zoom + pan for the life of the photo.
        val dir = if (Random.nextBoolean()) 1 else -1
        next.animate().scaleX(1.08f).scaleY(1.08f).translationX(dir * 24 * resources.displayMetrics.density)
            .setDuration(PHOTO_MS + 2000).setStartDelay(0).start()
        next.animate().alpha(1f).setDuration(1500)
        front?.animate()?.alpha(0f)?.setDuration(1500)?.start()
        front = next
        findViewById<TextView>(R.id.ambient_caption).text = caption.substringBefore(" (©").trim()
    }

    private fun render() {
        val p = Prefs(this)
        val wxView = findViewById<TextView>(R.id.ambient_weather)
        val fcView = findViewById<LinearLayout>(R.id.ambient_forecast)
        val w = Widgets.weather
        if (p.showWeather && w != null) {
            val (emoji, label) = WeatherRepository.describe(w.code, w.isDay)
            wxView.text = "$emoji  ${w.temp}${w.unit}  $label · ${w.place}"
            wxView.visibility = View.VISIBLE
            fcView.removeAllViews()
            w.days.drop(1).take(4).forEach { d ->
                val t = layoutInflater.inflate(R.layout.item_forecast, fcView, false) as TextView
                t.text = "${Format.shortDay(d.dateIso)}  ${WeatherRepository.describe(d.code).first}  ${d.hi}°/${d.lo}°"
                fcView.addView(t)
            }
            fcView.visibility = View.VISIBLE
        } else {
            wxView.visibility = View.GONE; fcView.visibility = View.GONE
        }
        val agenda = findViewById<TextView>(R.id.ambient_agenda)
        val ev = if (p.showCalendar) Widgets.upcoming(3) else emptyList()
        agenda.visibility = if (ev.isEmpty()) View.GONE else View.VISIBLE
        agenda.text = ev.joinToString("\n") { "${it.title}  ·  ${Format.eventWhen(it, p.clock24)}" }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { handler.removeCallbacks(nextPhoto); handler.post(nextPhoto) }
            else finish()
        }
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean { finish(); return true }

    companion object { private const val PHOTO_MS = 45_000L }
}
