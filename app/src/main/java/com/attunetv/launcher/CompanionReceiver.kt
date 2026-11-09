package com.attunetv.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import org.json.JSONObject

/** Only the ADB shell/system can send: protected by android.permission.DUMP in the manifest.
 * No HTTP listener, screen capture, credential storage, or arbitrary intents on the TV. */
class CompanionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.attunetv.launcher.COMPANION") return
        val encoded = intent.getStringExtra("payload") ?: return
        if (encoded.length > 24000) return
        val service = HomeButtonService.instance ?: return
        try {
            val message = JSONObject(String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8))
            if (service.companion.update(message)) resultCode = Activity.RESULT_OK
        } catch (_: Exception) {
            service.companion.hide()
        }
    }
}
