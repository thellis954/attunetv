package com.attunetv.launcher

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Stable remote focus: polling updates labels, never rebuilds the focused controls. */
class ChatSettingsActivity : TvActivity() {
    private val chat by lazy { NativeChat.get(this) }
    private val main = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var pairing: TextView
    private lateinit var qr: ImageView
    private lateinit var pair: Button
    private lateinit var approve: Button
    private lateinit var unlink: Button
    private lateinit var shortcut: Button
    private var renderedLink = ""
    private val changed: () -> Unit = { render() }
    private val poll = object : Runnable { override fun run() { chat.refresh(); render(); main.postDelayed(this, 2500) } }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; if (android.os.Build.VERSION.SDK_INT >= 26) fontVariationSettings = if (size >= 26f) "'wght' 600" else "'wght' 450"; setTextColor(Color.WHITE); setPadding(0, dp(6), 0, dp(8))
    }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) = ChatUi.style(Button(this)).apply {
        text = value; textSize = 14f; setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(8) })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); Backgrounds.applyDimmed(this, Prefs(this))
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(32), dp(24), dp(32), dp(24)) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(28), 0) }
        root.addView(left, LinearLayout.LayoutParams(dp(330), -1))
        left.addView(text("Ask with AttuneTV", 28f))
        left.addView(text("One conversation. TV and phone.", 17f))
        qr = ImageView(this).apply { contentDescription = "Scan to pair your phone"; visibility = View.GONE }
        left.addView(qr, LinearLayout.LayoutParams(dp(230), dp(230)))
        pairing = text("Pair a phone to get started. Your key is entered privately on your phone.", 14f); left.addView(pairing)
        val scroll = ScrollView(this); val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(right); root.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f)); setContentView(root)
        status = text("Connecting…", 18f); right.addView(status)
        right.addView(text("Choose OpenAI, Claude, Grok or Kimi. The phone guide helps you create a key, review billing and choose a model. API usage is billed separately from chat subscriptions.", 15f))
        pair = button(right, "Pair phone") { chat.pair() }
        approve = button(right, "Approve phone") { chat.state?.optJSONObject("pair")?.optString("code")?.let(chat::approve) }
        approve.visibility = View.GONE
        button(right, "Open conversation") {
            val service = HomeButtonService.instance
            if (service != null) service.nativePanel.show(packageName, preview = true)
            else Toast.makeText(this, "Enable AttuneTV Home redirection to open the panel.", Toast.LENGTH_LONG).show()
        }
        shortcut = button(right, "Hold Menu shortcut") {
            val prefs = getSharedPreferences("native-chat", MODE_PRIVATE)
            prefs.edit().putBoolean("enabled", !prefs.getBoolean("enabled", false)).apply()
            HomeButtonService.instance?.watchCompanionWindows(false); render()
        }
        right.addView(text(if (BuildConfig.STREAMING_VERIFICATION) "Playback test build: hold Menu in a candidate app. Playback and dismissal still need checking on this TV."
            else if (StreamingSupport.activePackages.isEmpty()) "Streaming overlays are still being verified. Chat works here and on your paired phone; unsupported apps never receive an overlay."
            else "Hold Menu while watching in a supported app. Back closes the panel.", 13f))
        unlink = button(right, "Disconnect & erase") {
            AlertDialog.Builder(this).setTitle("Disconnect and erase?")
                .setMessage("Remove this TV's pairing, saved API key and shared conversation from the service? This does not revoke the key at your provider.")
                .setNegativeButton("Keep connection", null).setPositiveButton("Erase") { _, _ -> chat.disconnect() }.show()
        }
        right.addView(text("Your key and conversation are encrypted on the hosted service. No always-on computer. AttuneTV cannot see or hear your video; supply the title, episode and progress. Answers can be wrong or contain spoilers.", 12f))
        button(right, "Back to Home") { finish() }
        pair.requestFocus()
    }
    override fun onResume() { super.onResume(); chat.observe(changed); main.post(poll) }
    override fun onPause() { main.removeCallbacks(poll); chat.unobserve(changed); super.onPause() }
    private fun render() {
        status.text = if (chat.busy) "Connecting…" else chat.status
        pair.isEnabled = !chat.busy; pair.text = if (chat.pairLink.isEmpty()) "Pair phone" else "Get a new pairing code"
        unlink.isEnabled = !chat.busy && chat.state != null
        val code = chat.state?.optJSONObject("pair")?.optString("code").orEmpty().takeUnless { it == "null" }.orEmpty()
        val active = chat.pairExpires > System.currentTimeMillis()
        approve.visibility = if (code.isNotEmpty() && active) View.VISIBLE else View.GONE
        approve.text = "Approve phone $code"; approve.isEnabled = !chat.busy
        shortcut.visibility = if (StreamingSupport.activePackages.isEmpty()) View.GONE else View.VISIBLE
        shortcut.text = "Hold Menu shortcut: ${if (getSharedPreferences("native-chat", MODE_PRIVATE).getBoolean("enabled", false)) "On" else "Off"}"
        val link = if (active) chat.pairLink else ""
        if (renderedLink != link) {
            renderedLink = link; qr.visibility = if (link.isEmpty()) View.GONE else View.VISIBLE
            if (link.isNotEmpty()) {
                val m = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 560, 560, mapOf(EncodeHintType.MARGIN to 4))
                qr.setImageBitmap(Bitmap.createBitmap(IntArray(560 * 560) { i -> if (m[i % 560, i / 560]) Color.BLACK else Color.WHITE }, 560, 560, Bitmap.Config.ARGB_8888))
            } else qr.setImageDrawable(null)
        }
        pairing.text = when {
            code.isNotEmpty() && active -> "Check your phone shows $code, then approve it with the remote."
            link.isNotEmpty() -> "Scan with your phone camera. Tap Pair this phone, then approve its number here. Expires in five minutes."
            chat.connected -> "Connected. Use the paired phone or open the conversation here."
            chat.state?.optBoolean("paired") == true -> "Finish AI connection on your phone. Keep your API key out of messages and screenshots."
            chat.pairExpires > 0 -> "Code expired. Select Get a new pairing code."
            else -> "Pair a phone to get started. Your key is entered privately on your phone."
        }
    }
}
