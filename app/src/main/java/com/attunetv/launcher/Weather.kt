package com.attunetv.launcher

import org.json.JSONObject
import java.net.URLEncoder

data class DayForecast(val dateIso: String, val code: Int, val hi: Int, val lo: Int, val precipPct: Int)

data class WeatherData(
    val place: String,
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val days: List<DayForecast>,
    val fahrenheit: Boolean,
    val fetchedAt: Long,
) {
    val unit get() = if (fahrenheit) "°" else "°C"

    fun toJson(): String = JSONObject().apply {
        put("place", place); put("temp", temp); put("code", code); put("isDay", isDay)
        put("f", fahrenheit); put("at", fetchedAt)
        put("days", org.json.JSONArray().apply {
            days.forEach { d ->
                put(JSONObject().put("d", d.dateIso).put("c", d.code).put("h", d.hi).put("l", d.lo).put("p", d.precipPct))
            }
        })
    }.toString()

    companion object {
        fun fromJson(s: String?): WeatherData? = try {
            val o = JSONObject(s ?: return null)
            val arr = o.getJSONArray("days")
            WeatherData(
                o.getString("place"), o.getInt("temp"), o.getInt("code"), o.getBoolean("isDay"),
                (0 until arr.length()).map {
                    val d = arr.getJSONObject(it)
                    DayForecast(d.getString("d"), d.getInt("c"), d.getInt("h"), d.getInt("l"), d.optInt("p"))
                },
                o.getBoolean("f"), o.getLong("at"),
            )
        } catch (_: Exception) { null }
    }
}

/** Open-Meteo (no API key). Location from ZIP / city name, or IP lookup when blank. */
object WeatherRepository {

    private data class Place(val lat: Double, val lon: Double, val name: String)

    fun fetch(location: String, fahrenheit: Boolean): WeatherData {
        val place = resolve(location)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.lat}&longitude=${place.lon}" +
            "&current=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&temperature_unit=${if (fahrenheit) "fahrenheit" else "celsius"}&timezone=auto&forecast_days=5"
        val o = JSONObject(Http.get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            DayForecast(
                dates.getString(i),
                daily.getJSONArray("weather_code").optInt(i),
                Math.round(daily.getJSONArray("temperature_2m_max").optDouble(i)).toInt(),
                Math.round(daily.getJSONArray("temperature_2m_min").optDouble(i)).toInt(),
                daily.getJSONArray("precipitation_probability_max").optInt(i, 0),
            )
        }
        return WeatherData(
            place.name,
            Math.round(cur.getDouble("temperature_2m")).toInt(),
            cur.getInt("weather_code"),
            cur.optInt("is_day", 1) == 1,
            days, fahrenheit, System.currentTimeMillis(),
        )
    }

    private fun resolve(location: String): Place {
        val loc = location.trim()
        if (loc.isEmpty()) return byIp()
        if (Regex("^\\d{5}$").matches(loc)) {
            // US ZIP code
            val o = JSONObject(Http.get("https://api.zippopotam.us/us/$loc"))
            val p = o.getJSONArray("places").getJSONObject(0)
            return Place(p.getString("latitude").toDouble(), p.getString("longitude").toDouble(),
                "${p.getString("place name")}, ${p.getString("state abbreviation")}")
        }
        val name = loc.substringBefore(',').trim()
        val q = URLEncoder.encode(name, "UTF-8")
        val o = JSONObject(Http.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=en&format=json"))
        val results = o.optJSONArray("results") ?: throw IllegalArgumentException("Location not found: $loc")
        val hint = loc.substringAfter(',', "").trim().lowercase()
        var best = results.getJSONObject(0)
        if (hint.isNotEmpty()) {
            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val admin = (r.optString("admin1") + " " + r.optString("country_code") + " " + r.optString("country")).lowercase()
                if (admin.contains(hint) || stateAbbrev(r.optString("admin1")) == hint) { best = r; break }
            }
        }
        return Place(best.getDouble("latitude"), best.getDouble("longitude"), best.getString("name"))
    }

    private fun byIp(): Place {
        return try {
            val o = JSONObject(Http.get("https://ipwho.is/"))
            Place(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city", "Your area"))
        } catch (_: Exception) {
            val o = JSONObject(Http.get("https://ipapi.co/json/"))
            Place(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city", "Your area"))
        }
    }

    private fun stateAbbrev(state: String): String = US_STATES[state.lowercase()] ?: ""

    private val US_STATES = mapOf(
        "alabama" to "al", "alaska" to "ak", "arizona" to "az", "arkansas" to "ar", "california" to "ca",
        "colorado" to "co", "connecticut" to "ct", "delaware" to "de", "florida" to "fl", "georgia" to "ga",
        "hawaii" to "hi", "idaho" to "id", "illinois" to "il", "indiana" to "in", "iowa" to "ia", "kansas" to "ks",
        "kentucky" to "ky", "louisiana" to "la", "maine" to "me", "maryland" to "md", "massachusetts" to "ma",
        "michigan" to "mi", "minnesota" to "mn", "mississippi" to "ms", "missouri" to "mo", "montana" to "mt",
        "nebraska" to "ne", "nevada" to "nv", "new hampshire" to "nh", "new jersey" to "nj", "new mexico" to "nm",
        "new york" to "ny", "north carolina" to "nc", "north dakota" to "nd", "ohio" to "oh", "oklahoma" to "ok",
        "oregon" to "or", "pennsylvania" to "pa", "rhode island" to "ri", "south carolina" to "sc",
        "south dakota" to "sd", "tennessee" to "tn", "texas" to "tx", "utah" to "ut", "vermont" to "vt",
        "virginia" to "va", "washington" to "wa", "west virginia" to "wv", "wisconsin" to "wi", "wyoming" to "wy",
        "district of columbia" to "dc",
    )

    /** WMO weather code → (emoji, label). */
    fun describe(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
        0 -> (if (isDay) "☀️" else "🌙") to "Clear"
        1 -> (if (isDay) "🌤️" else "🌙") to "Mostly clear"
        2 -> "⛅" to "Partly cloudy"
        3 -> "☁️" to "Cloudy"
        45, 48 -> "🌫️" to "Fog"
        51, 53, 55, 56, 57 -> "🌦️" to "Drizzle"
        61, 63, 80, 81 -> "🌧️" to "Rain"
        65, 82 -> "🌧️" to "Heavy rain"
        66, 67 -> "🌧️" to "Freezing rain"
        71, 73, 75, 77, 85, 86 -> "🌨️" to "Snow"
        95 -> "⛈️" to "Thunderstorms"
        96, 99 -> "⛈️" to "Storms & hail"
        else -> "🌡️" to "—"
    }
}
