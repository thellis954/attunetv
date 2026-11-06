package com.attunetv.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Daily photo background (Bing's image of the day; no key needed) plus a pre-blurred
 * copy used to draw real frosted glass on Android 11, which has no RenderEffect.
 */
object Wallpaper {
    data class Photo(val sharp: Bitmap, val blurred: Bitmap, val caption: String)

    var current: Photo? = null
        internal set
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private var loadingDay: String? = null

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }

    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Loads today's photo (from disk if already fetched today) and notifies listeners. */
    fun ensure(context: Context) {
        val app = context.applicationContext
        val day = today()
        if (loadingDay == day) return
        loadingDay = day
        io.execute {
            try {
                val dir = File(app.filesDir, "wallpaper").apply { mkdirs() }
                val img = File(dir, "today.jpg")
                val meta = File(dir, "today.json")
                val fresh = meta.exists() && JSONObject(meta.readText()).optString("day") == day && img.exists()
                if (!fresh) {
                    val o = JSONObject(Http.get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US"))
                    val imgs = o.getJSONArray("images")
                    val first = imgs.getJSONObject(0)
                    download("https://www.bing.com${first.getString("urlbase")}_1920x1080.jpg", img)
                    val more = org.json.JSONArray()
                    for (i in 0 until imgs.length()) {
                        val it = imgs.getJSONObject(i)
                        more.put(JSONObject().put("url", "https://www.bing.com${it.getString("urlbase")}_1920x1080.jpg")
                            .put("caption", it.optString("copyright")))
                    }
                    meta.writeText(JSONObject().put("day", day).put("caption", first.optString("copyright"))
                        .put("all", more).toString())
                }
                val caption = JSONObject(meta.readText()).optString("caption")
                val photo = decode(img, caption)
                main.post { current = photo; listeners.toList().forEach { it() } }
            } catch (_: Exception) {
                // Fall back to yesterday's file if present; otherwise keep the gradient.
                val img = File(app.filesDir, "wallpaper/today.jpg")
                if (img.exists() && current == null) {
                    try { val p = decode(img, ""); main.post { current = p; listeners.toList().forEach { it() } } }
                    catch (_: Exception) {}
                }
                loadingDay = null
            }
        }
    }

    /** Recent daily photos for Ambient mode: list of (url, caption). */
    fun gallery(context: Context): List<Pair<String, String>> = try {
        val meta = File(context.filesDir, "wallpaper/today.json")
        val a = JSONObject(meta.readText()).getJSONArray("all")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("url") to o.optString("caption") } }
    } catch (_: Exception) { emptyList() }

    /** Fetches a gallery photo to disk (cached) and decodes it. Call off the main thread. */
    fun loadGalleryImage(context: Context, url: String): Bitmap? = try {
        val f = File(context.cacheDir, "gallery-" + url.hashCode() + ".jpg")
        if (!f.exists()) download(url, f)
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 1 })
    } catch (_: Exception) { null }

    private fun download(url: String, to: File) {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            val tmp = File(to.path + ".part")
            conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(to)
        } finally { conn.disconnect() }
    }

    private fun decode(f: File, caption: String): Photo {
        val sharp = BitmapFactory.decodeFile(f.path) ?: throw IllegalStateException("bad image")
        // Blur a small copy; drawing it scaled up with filtering gives a smooth frost.
        val small = Bitmap.createScaledBitmap(sharp, 192, 108, true)
        return Photo(sharp, Blur.box(small, 4, 3), caption)
    }
}

/** Fast CPU box blur (3 passes ≈ gaussian). Fine for small bitmaps. */
object Blur {
    fun box(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width; val h = src.height
        var px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var tmp = IntArray(w * h)
        repeat(passes) {
            pass(px, tmp, w, h, radius, true)
            pass(tmp, px, w, h, radius, false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun pass(input: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) input[line * w + c] else input[c * w + line]
            }
            for (i in -r..r) { val p = at(i); sr += p shr 16 and 0xFF; sg += p shr 8 and 0xFF; sb += p and 0xFF }
            for (i in 0 until len) {
                val idx = if (horizontal) line * w + i else i * w + line
                out[idx] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = at(i + r + 1); val rem = at(i - r)
                sr += (add shr 16 and 0xFF) - (rem shr 16 and 0xFF)
                sg += (add shr 8 and 0xFF) - (rem shr 8 and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
