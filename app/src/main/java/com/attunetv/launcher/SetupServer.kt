package com.attunetv.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Tiny LAN web server for configuring AttuneTV from a phone (typing ZIP codes and
 * calendar URLs with a remote is miserable). Runs only while the Phone Setup screen
 * is open, and every request must carry the random PIN shown on the TV.
 */
class SetupServer(private val context: Context, private val onSaved: () -> Unit) {

    val pin: String = (1000..9999).random().toString()
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())

    val port get() = server?.localPort ?: PORT

    fun start() {
        if (server != null) return
        val s = try { ServerSocket(PORT) } catch (_: Exception) { ServerSocket(0) }
        server = s
        thread(name = "attunetv-setup", isDaemon = true) {
            while (!s.isClosed) {
                val sock = try { s.accept() } catch (_: Exception) { break }
                thread(isDaemon = true) { handle(sock) }
            }
        }
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun handle(sock: Socket) = sock.use {
        sock.soTimeout = 10_000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: break
            if (l.isEmpty()) break
            headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim()
        }
        val (method, target) = requestLine.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        val path = target.substringBefore('?')
        val query = parseForm(target.substringAfter('?', ""))
        val len = headers["content-length"]?.toIntOrNull()?.coerceAtMost(256_000) ?: 0
        val body = if (len > 0) ByteArray(len).also { b ->
            var off = 0
            while (off < len) { val r = input.read(b, off, len - off); if (r < 0) break; off += r }
        }.toString(Charsets.UTF_8) else ""

        val out = sock.getOutputStream()
        fun respond(code: String, type: String, content: String, extra: String = "") {
            val bytes = content.toByteArray(Charsets.UTF_8)
            out.write(("HTTP/1.1 $code\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n$extra\r\n").toByteArray())
            out.write(bytes); out.flush()
        }

        val form = parseForm(body)
        val suppliedPin = query["pin"] ?: form["pin"]
        if (suppliedPin != pin) {
            respond("403 Forbidden", "text/html", page("<h1>AttuneTV</h1><p>Scan the QR code on your TV (Settings → Phone setup) to open this page.</p>"))
            return
        }
        when {
            method == "POST" && path == "/save" -> {
                apply(form)
                main.post(onSaved)
                respond("303 See Other", "text/plain", "", "Location: /?pin=$pin&saved=1\r\n")
            }
            path == "/" -> respond("200 OK", "text/html", settingsPage(query["saved"] == "1"))
            else -> respond("404 Not Found", "text/plain", "Not found")
        }
    }

    private fun apply(f: Map<String, String>) {
        val prefs = Prefs(context)
        prefs.weatherLocation = f["location"].orEmpty()
        prefs.fahrenheit = f["units"] != "c"
        prefs.showWeather = f.containsKey("show_weather")
        prefs.showCalendar = f.containsKey("show_calendar")
        prefs.calendarUrls = f["calendars"].orEmpty().lines()
        prefs.clock24 = f.containsKey("clock24")
        prefs.ambientMinutes = f["ambient"]?.toIntOrNull() ?: prefs.ambientMinutes
        prefs.themeIndex = f["theme"]?.toIntOrNull() ?: prefs.themeIndex
        prefs.dynamicBackdrop = f.containsKey("backdrop")
        prefs.openOnBoot = f.containsKey("boot")

        // Favorites: keep existing order for ones still checked, append newly checked.
        val checkedFav = f.filterKeys { it.startsWith("fav_") }.values.toSet()
        val keptFav = prefs.favorites.filter { it in checkedFav }
        prefs.favorites = keptFav + (checkedFav - keptFav.toSet()).sorted()
        prefs.hidden = f.filterKeys { it.startsWith("hide_") }.values.toSet()
        Widgets.refresh(context, force = true)
    }

    private fun settingsPage(saved: Boolean): String {
        val p = Prefs(context)
        val apps = AppRepository(context).loadApps(p)
        val favs = p.favorites.toSet()
        val hidden = p.hidden
        fun chk(b: Boolean) = if (b) "checked" else ""
        val themeOpts = Themes.all.withIndex().joinToString("") { (i, t) ->
            "<option value='$i' ${if (i == p.themeIndex.mod(Themes.all.size)) "selected" else ""}>${esc(t.name)}</option>"
        }
        val ambientOpts = listOf(0 to "Never", 5 to "5 minutes", 10 to "10 minutes", 15 to "15 minutes", 30 to "30 minutes")
            .joinToString("") { (v, l) -> "<option value='$v' ${if (v == p.ambientMinutes) "selected" else ""}>$l</option>" }
        var idx = 0
        val appRows = apps.joinToString("") { a ->
            idx++
            "<tr><td>${esc(a.label)}</td>" +
                "<td><input type='checkbox' name='fav_$idx' value='${esc(a.key)}' ${chk(a.key in favs)}></td>" +
                "<td><input type='checkbox' name='hide_$idx' value='${esc(a.key)}' ${chk(a.key in hidden)}></td></tr>"
        }
        val wxStatus = Widgets.weatherError?.let { "<p class='err'>Weather: ${esc(it)}</p>" }
            ?: Widgets.weather?.let { "<p class='ok'>Now: ${it.temp}${it.unit} in ${esc(it.place)}</p>" }.orEmpty()
        val calStatus = Widgets.calendarError?.let { "<p class='err'>${esc(it)}</p>" }
            ?: if (p.calendarUrls.isNotEmpty()) "<p class='ok'>${Widgets.events.size} upcoming events loaded</p>" else ""

        return page("""
<h1>AttuneTV</h1>
${if (saved) "<div class='saved'>Saved ✓ The TV updates right away.</div>" else ""}
<form method='post' action='/save'>
<input type='hidden' name='pin' value='$pin'>

<h2>Weather</h2>
<label>Location <small>(ZIP, or City, State. Leave blank to auto-detect.)</small>
<input name='location' value='${esc(p.weatherLocation)}' placeholder='e.g. 33301 or Austin, TX'></label>
<label class='row'><input type='radio' name='units' value='f' ${chk(p.fahrenheit)}> °F
<input type='radio' name='units' value='c' ${chk(!p.fahrenheit)}> °C</label>
<label class='row'><input type='checkbox' name='show_weather' ${chk(p.showWeather)}> Show weather</label>
$wxStatus

<h2>Calendar</h2>
<label>iCal feed URLs, one per line
<textarea name='calendars' rows='4' placeholder='https://calendar.google.com/calendar/ical/…/basic.ics'>${esc(p.calendarUrls.joinToString("\n"))}</textarea></label>
<details><summary>Where do I find this?</summary>
<p><b>Google:</b> calendar.google.com → ⚙ Settings → pick a calendar → "Integrate calendar" → copy <i>Secret address in iCal format</i>.<br>
<b>iCloud:</b> Calendar app → (i) next to a calendar → Public Calendar → Share Link.<br>
<b>Outlook:</b> Settings → Calendar → Shared calendars → Publish → copy the ICS link.</p></details>
<label class='row'><input type='checkbox' name='show_calendar' ${chk(p.showCalendar)}> Show calendar</label>
$calStatus

<h2>Look &amp; feel</h2>
<label>Background <select name='theme'>$themeOpts</select></label>
<label class='row'><input type='checkbox' name='backdrop' ${chk(p.dynamicBackdrop)}> Tint background with the selected app's art</label>
<label class='row'><input type='checkbox' name='clock24' ${chk(p.clock24)}> 24-hour clock</label>
<label>Ambient mode after <select name='ambient'>$ambientOpts</select></label>
<label class='row'><input type='checkbox' name='boot' ${chk(p.openOnBoot)}> Open AttuneTV when the TV starts</label>

<h2>Apps</h2>
<table><tr><th>App</th><th>★ Favorite</th><th>Hide</th></tr>$appRows</table>

<button type='submit'>Save to TV</button>
</form>""")
    }

    private fun page(body: String) = """<!doctype html><html><head><meta charset='utf-8'>
<meta name='viewport' content='width=device-width,initial-scale=1'><title>AttuneTV setup</title>
<style>
:root{color-scheme:dark}
body{font:16px/1.45 -apple-system,system-ui,sans-serif;background:#0b1020;color:#f2f4f8;margin:0;padding:16px;max-width:640px;margin:auto}
h1{font-weight:300;font-size:32px;margin:8px 0}h2{font-size:14px;letter-spacing:.08em;text-transform:uppercase;color:#9aa3b5;margin-top:28px}
label{display:block;margin:12px 0}label.row{display:flex;gap:10px;align-items:center}
input:not([type=checkbox]):not([type=radio]),textarea,select{width:100%;box-sizing:border-box;margin-top:6px;padding:12px;border-radius:12px;border:1px solid #2a3454;background:#141b33;color:inherit;font:inherit}
input[type=checkbox],input[type=radio]{width:22px;height:22px;accent-color:#7aa2ff}
table{width:100%;border-collapse:collapse}td,th{padding:10px 6px;border-bottom:1px solid #1e2640;text-align:left}td+td,th+th{text-align:center;width:80px}
button{position:sticky;bottom:12px;width:100%;margin-top:24px;padding:16px;border:0;border-radius:16px;background:#7aa2ff;color:#0b1020;font:600 17px system-ui}
.saved{background:#163a2a;border-radius:12px;padding:12px}.err{color:#ff9a9a}.ok{color:#9be3b5}small{color:#9aa3b5}
details{color:#9aa3b5}summary{cursor:pointer}
</style></head><body>$body</body></html>"""

    companion object {
        const val PORT = 8765

        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "&#39;").replace("\"", "&quot;")

        fun parseForm(s: String): Map<String, String> = s.split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }

        private fun readLine(input: BufferedInputStream): String? {
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
                if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
                if (buf.size() > 8192) return null
                buf.write(b)
            }
        }

        /** The TV's LAN IPv4 address (Wi-Fi or Ethernet). */
        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        } catch (_: Exception) { null }
    }
}
