package com.attunetv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : TvActivity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout
    private val menu by lazy { GlassMenu(this) }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (menu.isOpen) menu.dismiss() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        list = findViewById(R.id.settings_list)
    }

    override fun onResume() {
        super.onResume()
        applyWindowBackground()
        Widgets.addListener(onWidgets)
        render()
    }

    override fun onPause() {
        Widgets.removeListener(onWidgets)
        super.onPause()
    }

    private val onWidgets: () -> Unit = { render() }

    private fun render() {
        if (menu.isOpen) return
        val focusedIndex = list.indexOfChild(list.focusedChild).takeIf { it >= 0 } ?: 1
        list.removeAllViews()
        val serviceOn = HomeButtonService.isEnabled(this)

        status(
            if (serviceOn) getString(R.string.service_on) else getString(R.string.service_off)
        )

        row(getString(R.string.set_phone)) { startActivity(Intent(this, PhoneSetupActivity::class.java)) }
        row("Ask with AttuneTV • ChatGPT setup") {startActivity(Intent(this,ChatSettingsActivity::class.java))}

        header(getString(R.string.hdr_widgets))
        row(getString(R.string.set_location, prefs.weatherLocation.ifBlank { getString(R.string.auto) })) { editLocation() }
        row(getString(R.string.set_units, if (prefs.fahrenheit) "°F" else "°C")) {
            prefs.fahrenheit = !prefs.fahrenheit; Widgets.refresh(this, force = true); render()
        }
        row(getString(R.string.set_show_weather, onOff(prefs.showWeather))) { prefs.showWeather = !prefs.showWeather; render() }
        row(getString(R.string.set_show_calendar, onOff(prefs.showCalendar), prefs.calendarUrls.size)) {
            if (prefs.calendarUrls.isEmpty()) startActivity(Intent(this, PhoneSetupActivity::class.java))
            else { prefs.showCalendar = !prefs.showCalendar; render() }
        }
        row(getString(R.string.set_refresh)) {
            Widgets.refresh(this, force = true)
            Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show()
        }
        Widgets.weatherError?.let { status(getString(R.string.weather_error, it)) }
        Widgets.calendarError?.let { status(it) }

        header(getString(R.string.hdr_look))
        if (prefs.backgroundMode != Prefs.BG_PHOTO) row(getString(R.string.set_theme, Themes.all[prefs.themeIndex.mod(Themes.all.size)].name)) {
            prefs.themeIndex = (prefs.themeIndex + 1).mod(Themes.all.size)
            applyWindowBackground()
            render()
        }
        row(getString(R.string.set_background, bgLabel(prefs.backgroundMode))) {
            prefs.backgroundMode = (prefs.backgroundMode + 1) % 3
            applyWindowBackground(); render()
        }
        row(getString(R.string.set_clock24, onOff(prefs.clock24))) { prefs.clock24 = !prefs.clock24; render() }
        row(getString(R.string.set_ambient, ambientLabel(prefs.ambientMinutes))) {
            val opts = listOf(0, 5, 10, 15, 30)
            prefs.ambientMinutes = opts[(opts.indexOf(prefs.ambientMinutes) + 1).mod(opts.size)]
            render()
        }
        row(getString(R.string.set_show_hidden, onOff(prefs.showHidden))) { prefs.showHidden = !prefs.showHidden; render() }

        header(getString(R.string.hdr_system))
        row(getString(R.string.set_redirect, onOff(prefs.redirectEnabled))) {
            prefs.redirectEnabled = !prefs.redirectEnabled; render()
        }
        row(getString(R.string.set_boot, onOff(prefs.openOnBoot))) { prefs.openOnBoot = !prefs.openOnBoot; render() }
        row(getString(R.string.set_accessibility)) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        row(getString(R.string.set_firetv_settings)) {
            val i = packageManager.getLaunchIntentForPackage("com.amazon.tv.settings.v2") ?: Intent(Settings.ACTION_SETTINGS)
            open(i)
        }
        row(getString(R.string.set_amazon_home)) { HomeActivity.openAmazonHome(this) }
        row(getString(R.string.set_reset)) {
            menu.show(getString(R.string.set_reset), getString(R.string.reset_confirm), listOf(
                GlassMenu.Item(getString(android.R.string.cancel)) {},
                GlassMenu.Item(getString(R.string.reset)) {
                    getSharedPreferences("attunetv", MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
                    render()
                },
            ))
        }

        val ver = packageManager.getPackageInfo(packageName, 0).versionName
        val seen = HomeButtonService.lastSeenClass?.let { "\n" + getString(R.string.last_seen, it) }.orEmpty()
        status(getString(R.string.about, ver) + seen)

        (list.getChildAt(focusedIndex)?.takeIf { it is Button } ?: list.getChildAt(1))?.requestFocus()
    }

    private fun bgLabel(m: Int) = getString(when (m) { Prefs.BG_PHOTO -> R.string.bg_photo; Prefs.BG_GLOW -> R.string.bg_glow; else -> R.string.bg_theme })

    private fun applyWindowBackground() = Backgrounds.applyDimmed(this, prefs)

    private fun ambientLabel(m: Int) = if (m == 0) getString(R.string.never) else getString(R.string.minutes, m)

    private fun editLocation() {
        val input = EditText(this).apply {
            setText(prefs.weatherLocation)
            hint = getString(R.string.location_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.location_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.weatherLocation = input.text.toString()
                Widgets.refresh(this, force = true)
                render()
            }
            .setNeutralButton(R.string.auto) { _, _ ->
                prefs.weatherLocation = ""; Widgets.refresh(this, force = true); render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun header(text: String) {
        val t = layoutInflater.inflate(R.layout.item_status, list, false) as TextView
        t.text = text.uppercase()
        t.textSize = 11f
        t.letterSpacing = 0.1f
        t.setPadding(t.paddingLeft, 24, t.paddingRight, 0)
        list.addView(t)
    }

    private fun onOff(b: Boolean) = getString(if (b) R.string.on else R.string.off)

    private fun open(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show() }
    }

    private fun row(text: String, onClick: () -> Unit) {
        val b = layoutInflater.inflate(R.layout.item_setting, list, false) as Button
        b.text = text
        val glass = GlassDrawable(b, 14 * resources.displayMetrics.density)
        b.background = glass
        b.setOnFocusChangeListener { _, f -> glass.focused = f }
        b.setOnClickListener { onClick() }
        list.addView(b)
    }

    private fun status(text: String) {
        val t = layoutInflater.inflate(R.layout.item_status, list, false) as TextView
        t.text = text
        t.visibility = View.VISIBLE
        list.addView(t)
    }
}
