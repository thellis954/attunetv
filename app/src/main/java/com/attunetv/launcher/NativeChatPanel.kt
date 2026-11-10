package com.attunetv.launcher

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged

/** Shared conversation in a dismissible native window; no playback capture or player controls. */
class NativeChatPanel(private val service: HomeButtonService) {
    private val context = Screen.normalize(service)
    private val manager = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val chat = NativeChat.get(service)
    private var panel: LinearLayout? = null
    private var expected: String? = null
    private var update: (() -> Unit)? = null
    private val timeout = Runnable { hide() }
    private val poll = object : Runnable { override fun run() { if (isShowing) { chat.refresh(); main.postDelayed(this, 2500) } } }
    val isShowing get() = panel != null
    private fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Float) = TextView(context).apply { text = value; textSize = size; if (android.os.Build.VERSION.SDK_INT >= 26) fontVariationSettings = "'wght' 450"; setTextColor(Color.WHITE); setPadding(0, dp(5), 0, dp(5)) }
    private fun button(value: String, action: () -> Unit) = ChatUi.style(Button(context)).apply {
        text = value; textSize = 13f; setOnClickListener { touch(); action() }
        setOnFocusChangeListener { _, focused -> if (focused) touch() }
    }
    private fun field(hintText: String) = EditText(context).apply {
        if (android.os.Build.VERSION.SDK_INT >= 26) fontVariationSettings = "'wght' 450"
        hint = hintText; contentDescription = hintText; textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(0xFFB9C8CE.toInt()); setSingleLine()
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    private fun touch() { main.removeCallbacks(timeout); if (isShowing) main.postDelayed(timeout, 120000) }
    fun show(pkg: String, preview: Boolean = false) {
        if (!StreamingSupport.supported(pkg) && !(preview && pkg == service.packageName)) return
        hide(); expected = pkg
        val column = object : LinearLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                touch()
                if (event.keyCode == KeyEvent.KEYCODE_BACK) { if (event.action == KeyEvent.ACTION_UP) hide(); return true }
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply { setColor(Color.argb(246, 14, 25, 33)); cornerRadius = dp(16).toFloat(); setStroke(dp(1), 0xFF657E82.toInt()) }
        }
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(context).apply { addView(body); isFillViewport = true }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(label("Ask with AttuneTV", 21f))
        val status = label("Loading conversation…", 12f); body.addView(status)
        val details = label("", 13f); body.addView(details)
        val edit = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val title = field("Movie or show title")
        val episode = field("Season and episode")
        val progress = field("Progress / scene — avoid later spoilers")
        edit.addView(title); edit.addView(episode); edit.addView(progress)
        var revision = -1
        var contextRevision = -1
        var editing = false
        var submitted = false
        val serviceName = if (preview) "" else StreamingSupport.candidates[pkg].orEmpty()
        val change = button("Set what’s playing") {
            val current = chat.state?.optJSONObject("context")
            title.setText(current?.optString("title").orEmpty())
            episode.setText(current?.optString("episode").orEmpty())
            progress.setText(current?.optString("progress").orEmpty())
            if (title.text.isBlank()) {
                val metadata = PlaybackContext.read(service, pkg)
                title.setText(metadata.title.orEmpty()); episode.setText(metadata.episode.orEmpty())
            }
            contextRevision = chat.state?.optInt("revision") ?: -1
            editing = true; submitted = false; edit.visibility = View.VISIBLE; title.requestFocus()
        }
        body.addView(change); body.addView(edit)
        edit.addView(label("Confirm the title and where you are. Saving starts a fresh conversation on both screens.", 11f))
        val save = button("Save viewing context") {
            if (title.text.isBlank()) { status.text = "Enter the title first."; return@button }
            submitted = true
            chat.saveContext(title.text.toString(), episode.text.toString(), progress.text.toString(),
                serviceName.ifEmpty { chat.state?.optJSONObject("context")?.optString("service").orEmpty() }, contextRevision)
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(column.windowToken, 0)
        }; edit.addView(save)
        val answer = label("Pair your phone from Ask on Home to connect AI.", 15f).apply { isFocusable = true }
        body.addView(answer)
        val question = field("Your question").apply { setSingleLine(false); maxLines = 3; minLines = 1 }
        body.addView(question)
        var draftRevision = -1
        question.doAfterTextChanged { if (it.isNullOrBlank()) draftRevision = -1 else if (draftRevision < 0) draftRevision = revision }
        val review = button("Use updated viewing context") { draftRevision = revision; update?.invoke() }
        review.visibility = View.GONE; body.addView(review)
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val ask = button("Ask") {
            if (question.text.isBlank()) { status.text = "Enter a question first."; return@button }
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(column.windowToken, 0)
            chat.ask(question.text.toString(), draftRevision)
        }
        val stop = button("Stop answer") { chat.stop() }
        actions.addView(ask, LinearLayout.LayoutParams(0, dp(42), 1f)); actions.addView(stop, LinearLayout.LayoutParams(0, dp(42), 1f)); body.addView(actions)
        body.addView(label("Chat on your paired phone while watching. Use its keyboard microphone to dictate. No video or audio capture. Answers may contain errors or spoilers.", 11f))
        column.addView(button("Close • Back") { hide() }, LinearLayout.LayoutParams(-1, dp(42)))
        val listener: () -> Unit = {
            val state = chat.state
            status.text = chat.status
            if (state != null) {
                revision = state.optInt("revision")
                val c = state.optJSONObject("context")
                details.text = listOf(c?.optString("title"), c?.optString("episode"), c?.optString("progress"), c?.optString("service")).filter { !it.isNullOrBlank() }.joinToString(" · ")
                change.text = if (c?.optString("title").isNullOrEmpty()) "Set what’s playing" else "Change what’s playing"
                // A phone edit must never silently replace a draft or send it against a different show.
                if (editing && revision != contextRevision) {
                    if (submitted && c?.optString("title") == title.text.toString().trim() && c.optString("episode") == episode.text.toString().trim() && c.optString("progress") == progress.text.toString().trim()) {
                        editing = false; edit.visibility = View.GONE; question.requestFocus()
                    } else status.text = "Context changed on your phone. Select Change what’s playing to reload it."
                }
                val transcript = CompanionProtocol.transcript(state)
                answer.text = transcript.ifBlank { if (chat.connected) "Ready for your first question." else "Finish AI connection on your paired phone." }
                val staleDraft = question.text.isNotBlank() && draftRevision >= 0 && draftRevision != revision
                review.visibility = if (staleDraft) View.VISIBLE else View.GONE
                if (staleDraft) status.text = "Viewing context changed. Review it above before sending your draft."
                ask.isEnabled = !staleDraft && chat.connected && !chat.busy && !chat.pending && !editing && !c?.optString("title").isNullOrBlank()
                stop.isEnabled = chat.pending && !chat.busy
                save.isEnabled = !chat.busy && contextRevision == revision
            } else { ask.isEnabled = false; stop.isEnabled = false }
            change.isEnabled = state != null && !chat.busy
        }
        val params = WindowManager.LayoutParams(dp(390), dp(490), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.END or Gravity.TOP; x = dp(16); y = dp(16); this.title = "AttuneTV conversation"
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        try {
            manager.addView(column, params); panel = column; update = listener; chat.observe(listener)
            service.watchCompanionWindows(true); touch(); main.post(poll); change.requestFocus()
        } catch (_: Exception) { hide() }
    }
    fun onWindow(pkg: String) {
        if (!isShowing) return
        val keyboard = Settings.Secure.getString(service.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        if (pkg != expected && pkg != service.packageName && pkg != keyboard) hide()
    }
    fun hide() {
        main.removeCallbacks(timeout); main.removeCallbacks(poll); update?.let(chat::unobserve); update = null
        panel?.let { view ->
            service.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
            try { manager.removeViewImmediate(view) } catch (_: Exception) {}
        }
        panel = null; expected = null; service.watchCompanionWindows(false)
        // Closing the TV window does not cancel a conversation being used on the phone.
    }
}
