package com.attunetv.launcher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import kotlin.random.Random

/**
 * Calm full-screen clock + weather + agenda. Opens after idling on the home screen;
 * any button returns. Content drifts every minute to protect against burn-in.
 */
class AmbientActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var content: View
    private val update: () -> Unit = { render() }

    private val drift = object : Runnable {
        override fun run() {
            val maxX = (content.rootView.width - content.width).coerceAtLeast(0) / 2f
            val maxY = (content.rootView.height - content.height).coerceAtLeast(0) / 2f
            content.animate()
                .translationX(Random.nextFloat() * 2 * maxX - maxX)
                .translationY(Random.nextFloat() * 2 * maxY - maxY)
                .setDuration(4000).start()
            render()
            handler.postDelayed(this, 60_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ambient)
        content = findViewById(R.id.ambient_content)
        val p = Prefs(this)
        findViewById<TextClock>(R.id.ambient_clock).apply {
            format12Hour = if (p.clock24) "HH:mm" else "h:mm"
            format24Hour = format12Hour
        }
    }

    override fun onResume() {
        super.onResume()
        Widgets.addListener(update)
        Widgets.refresh(this)
        render()
        handler.postDelayed(drift, 60_000)
    }

    override fun onPause() {
        Widgets.removeListener(update)
        handler.removeCallbacks(drift)
        super.onPause()
    }

    private fun render() {
        val p = Prefs(this)
        val wxView = findViewById<TextView>(R.id.ambient_weather)
        val fcView = findViewById<LinearLayout>(R.id.ambient_forecast)
        val w = Widgets.weather
        if (p.showWeather && w != null) {
            val (emoji, label) = WeatherRepository.describe(w.code, w.isDay)
            wxView.text = "$emoji  ${w.temp}${w.unit}  ·  $label  ·  ${w.place}"
            wxView.visibility = View.VISIBLE
            fcView.removeAllViews()
            w.days.take(5).forEach { d ->
                val t = layoutInflater.inflate(R.layout.item_forecast, fcView, false) as TextView
                t.text = "${Format.shortDay(d.dateIso)}\n${WeatherRepository.describe(d.code).first}\n${d.hi}° / ${d.lo}°"
                fcView.addView(t)
            }
            fcView.visibility = View.VISIBLE
        } else {
            wxView.visibility = View.GONE; fcView.visibility = View.GONE
        }

        val agenda = findViewById<TextView>(R.id.ambient_agenda)
        val ev = if (p.showCalendar) Widgets.upcoming(4) else emptyList()
        agenda.visibility = if (ev.isEmpty()) View.GONE else View.VISIBLE
        agenda.text = ev.joinToString("\n") { "${Format.eventWhen(it, p.clock24)}   ${it.title}" }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean { finish(); return true }
}
