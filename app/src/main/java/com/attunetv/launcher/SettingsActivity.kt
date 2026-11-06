package com.attunetv.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)
        list = findViewById(R.id.settings_list)
    }

    override fun onResume() {
        super.onResume()
        window.decorView.background = Themes.background(prefs.themeIndex)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val serviceOn = HomeButtonService.isEnabled(this)

        status(
            if (serviceOn) getString(R.string.service_on) else getString(R.string.service_off)
        )

        row(getString(R.string.set_redirect, onOff(prefs.redirectEnabled))) {
            prefs.redirectEnabled = !prefs.redirectEnabled; render()
        }
        row(getString(R.string.set_accessibility)) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        row(getString(R.string.set_show_hidden, onOff(prefs.showHidden))) {
            prefs.showHidden = !prefs.showHidden; render()
        }
        row(getString(R.string.set_theme, Themes.all[prefs.themeIndex.mod(Themes.all.size)].name)) {
            prefs.themeIndex = (prefs.themeIndex + 1).mod(Themes.all.size)
            window.decorView.background = Themes.background(prefs.themeIndex)
            render()
        }
        row(getString(R.string.set_firetv_settings)) {
            val i = packageManager.getLaunchIntentForPackage("com.amazon.tv.settings.v2")
                ?: Intent(Settings.ACTION_SETTINGS)
            open(i)
        }
        row(getString(R.string.set_amazon_home)) {
            HomeButtonService.allowAmazonHome()
            val i = packageManager.getLaunchIntentForPackage("com.amazon.tv.launcher")
                ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setPackage("com.amazon.tv.launcher")
            open(i)
        }
        row(getString(R.string.set_reset)) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage(R.string.reset_confirm)
                .setPositiveButton(R.string.reset) { _, _ ->
                    getSharedPreferences("attunetv", MODE_PRIVATE).edit().clear().apply()
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show()
                    render()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        val ver = packageManager.getPackageInfo(packageName, 0).versionName
        val seen = HomeButtonService.lastSeenClass?.let { "\n" + getString(R.string.last_seen, it) }.orEmpty()
        status(getString(R.string.about, ver) + seen)

        list.getChildAt(1)?.requestFocus()
    }

    private fun onOff(b: Boolean) = getString(if (b) R.string.on else R.string.off)

    private fun open(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show() }
    }

    private fun row(text: String, onClick: () -> Unit) {
        val b = layoutInflater.inflate(R.layout.item_setting, list, false) as Button
        b.text = text
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
