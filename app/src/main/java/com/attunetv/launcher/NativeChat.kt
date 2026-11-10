package com.attunetv.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** Native TV client for the shared HTTPS companion. Provider keys never reach the TV. */
class NativeChat private constructor(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val vault = ChatVault(context.applicationContext, "companion-session.enc", "attunetv-companion")
    @Volatile private var credential = ""
    private var actionError: String? = null
    private var loading = false
    private val listeners = linkedSetOf<() -> Unit>()
    var state: JSONObject? = null; private set
    var status = "Pair your phone to connect an AI provider."; private set
    var pairLink = ""; private set
    var pairExpires = 0L; private set
    var busy = false; private set
    val connected get() = state?.optJSONObject("account") != null
    val pending get() = state?.optJSONArray("messages")?.let { a -> (0 until a.length()).any { a.getJSONObject(it).optString("status") == "pending" } } == true
    private var storageReady = false
    private var retryId = ""
    private var retryQuestion = ""
    private var retryRevision = -1

    init {
        worker.execute {
            try {
                credential = vault.read().optString("credential")
                main.post { storageReady = true; refresh() }
            } catch (_: Exception) { main.post { status = "Cannot unlock the saved connection. Restart the TV and try again."; publish() } }
        }
    }
    fun observe(listener: () -> Unit) { listeners.add(listener); listener() }
    fun unobserve(listener: () -> Unit) { listeners.remove(listener) }
    private fun publish() { listeners.toList().forEach { it() } }
    private class ApiError(val code: Int, message: String) : Exception(message)
    private fun request(path: String, body: JSONObject? = null): JSONObject {
        val conn = URL(ORIGIN + "/api" + path).openConnection() as HttpsURLConnection
        try {
            conn.connectTimeout = 12000; conn.readTimeout = 20000; conn.instanceFollowRedirects = false
            conn.setRequestProperty("Origin", "null")
            if (credential.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $credential")
            if (body != null) {
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 1_000_000); out.write(buffer, 0, n) }
                out.toByteArray()
            } ?: byteArrayOf()
            val result = try { JSONObject(String(bytes, Charsets.UTF_8)) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) throw ApiError(code, result.optString("error", "The companion service is unavailable. Try again."))
            return result
        } finally { conn.disconnect() }
    }
    private fun accept(value: JSONObject) {
        state = value
        val account = value.optJSONObject("account")
        status = actionError ?: if (account != null) "${account.optString("provider")} · ${account.optString("model")}"
            else if (value.optBoolean("paired")) "Phone connected. Finish AI connection on your phone."
            else "Pair your phone to connect OpenAI, Claude, Grok or Kimi."
    }
    private fun failure(error: Exception, sticky: Boolean = false) {
        status = if (error is ApiError) error.message.orEmpty() else "Cannot reach AttuneTV. Check the TV connection and retry."
        if (sticky) actionError = status
        if (error is ApiError && error.code == 401) {
            state = null; pairLink = ""; pairExpires = 0
            // Session expiration is explicit: Pair phone starts a new room, never silently moves a question.
            credential = ""
        }
    }
    fun refresh() {
        if (!storageReady || loading || busy || credential.isEmpty()) return
        loading = true
        worker.execute {
            try { val value = request("/state"); main.post { accept(value); loading = false; publish() } }
            catch (e: Exception) { main.post { failure(e); loading = false; publish() } }
        }
    }
    private fun mutate(action: () -> Unit) {
        if (!storageReady || busy) return
        actionError = null; busy = true; publish()
        worker.execute {
            try {
                action(); val value = request("/state")
                main.post { accept(value); busy = false; publish() }
            } catch (e: Exception) { main.post { failure(e, sticky = true); busy = false; publish() } }
        }
    }
    fun pair() = mutate {
        if (credential.isEmpty()) {
            val next = request("/tv", JSONObject()).getString("credential")
            vault.write(JSONObject().put("credential", next)); credential = next
        }
        val value = request("/pair", JSONObject())
        val link = value.getString("link")
        require(link.startsWith("$ORIGIN/phone#pair="))
        main.post { pairLink = link; pairExpires = value.getLong("expires") }
    }
    fun approve(code: String) = mutate {
        request("/pair/approve", JSONObject().put("code", code))
        main.post { pairLink = ""; pairExpires = 0 }
    }
    fun saveContext(title: String, episode: String, progress: String, service: String, revision: Int) = mutate {
        request("/context", CompanionProtocol.context(title, episode, progress, service, revision))
    }
    fun ask(question: String, revision: Int) {
        if (question.isBlank()) return
        if (retryQuestion != question || retryRevision != revision) {
            retryId = UUID.randomUUID().toString(); retryQuestion = question; retryRevision = revision
        }
        val id = retryId
        mutate { request("/messages", JSONObject().put("id", id).put("question", question).put("revision", revision))
            main.post { retryId = ""; retryQuestion = ""; retryRevision = -1 } }
    }
    fun stop() = mutate { request("/stop", JSONObject()) }
    fun clear() = mutate { request("/clear", JSONObject()) }
    fun disconnect() {
        if (!storageReady || busy || credential.isEmpty()) return
        actionError = null; busy = true; publish()
        worker.execute {
            try {
                request("/disconnect", JSONObject()); vault.write(JSONObject())
                main.post { credential = ""; state = null; pairLink = ""; pairExpires = 0; busy = false
                    status = "Disconnected. The saved API key and conversation were erased from this service."; publish() }
            } catch (e: Exception) { main.post { failure(e, sticky = true); busy = false; publish() } }
        }
    }
    companion object {
        const val ORIGIN = "https://attunetv-samsung.vercel.app"
        @Volatile private var instance: NativeChat? = null
        fun get(context: Context): NativeChat = instance ?: synchronized(this) { instance ?: NativeChat(context).also { instance = it } }
    }
}

object CompanionProtocol {
    fun context(title: String, episode: String, progress: String, service: String, revision: Int): JSONObject {
        require(title.isNotBlank()) { "Enter the movie or show title." }
        return JSONObject().put("title", title.trim().take(240)).put("episode", episode.trim().take(120))
            .put("progress", progress.trim().take(160)).put("service", service.trim().take(80)).put("revision", revision)
    }
    fun transcript(state: JSONObject): String {
        val messages = state.optJSONArray("messages") ?: return ""
        return (0 until messages.length()).joinToString("\n\n") { i ->
            val m = messages.getJSONObject(i)
            val who = if (m.optString("role") == "user") "You" else "AttuneTV"
            "$who\n${if (m.optString("status") == "pending") "Thinking…" else m.optString("text") }"
        }
    }
}
