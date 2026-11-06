package com.attunetv.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Shared weather + calendar state. Cached in prefs so the home screen paints instantly,
 * refreshed in the background (weather every 30 min, calendar every 15 min).
 */
object Widgets {
    var weather: WeatherData? = null
        internal set
    var events: List<CalEvent> = emptyList()
        internal set
    var weatherError: String? = null; private set
    var calendarError: String? = null; private set

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var lastWeather = 0L
    private var lastCalendar = 0L
    private var loadedCache = false

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (!loadedCache) {
            loadedCache = true
            weather = WeatherData.fromJson(prefs.cache("weather"))
            events = CalEvent.listFromJson(prefs.cache("events"))
        }
        val now = System.currentTimeMillis()
        val wantWx = prefs.showWeather && (force || now - lastWeather > 30 * MIN ||
            weather?.fahrenheit != prefs.fahrenheit)
        val wantCal = prefs.showCalendar && prefs.calendarUrls.isNotEmpty() && (force || now - lastCalendar > 15 * MIN)
        if (prefs.calendarUrls.isEmpty() && events.isNotEmpty()) { events = emptyList(); notifyListeners() }
        if (!wantWx && !wantCal) return
        if (wantWx) lastWeather = now
        if (wantCal) lastCalendar = now

        io.execute {
            if (wantWx) {
                try {
                    val w = WeatherRepository.fetch(prefs.weatherLocation, prefs.fahrenheit)
                    weather = w; weatherError = null
                    prefs.putCache("weather", w.toJson())
                } catch (e: Exception) {
                    weatherError = e.message ?: "Weather unavailable"
                    lastWeather = now - 25 * MIN // retry in ~5 min
                }
            }
            if (wantCal) {
                val from = startOfToday()
                val to = from + 8 * DAY
                val all = mutableListOf<CalEvent>()
                var err: String? = null
                for (url in prefs.calendarUrls) {
                    try { all += Ics.events(Http.get(url, 20_000), from, to) }
                    catch (e: Exception) { err = "Couldn't load a calendar (${e.message})" }
                }
                val now2 = System.currentTimeMillis()
                events = all.filter { it.end > now2 }.distinctBy { it.title to it.start }.sortedBy { it.start }
                calendarError = err
                prefs.putCache("events", CalEvent.listToJson(events))
            }
            main.post { notifyListeners() }
        }
    }

    private fun notifyListeners() = listeners.toList().forEach { it() }

    fun upcoming(limit: Int): List<CalEvent> {
        val now = System.currentTimeMillis()
        return events.filter { it.end > now }.take(limit)
    }

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN
}
