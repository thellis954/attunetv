package com.attunetv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings AttuneTV up after the TV boots. (The accessibility service also catches
 * Amazon's home appearing; this covers the case where it starts a little late.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs(context).openOnBoot) return
        // Background activity starts are only allowed when our accessibility service is
        // running; that's the normal setup, so try and ignore failures.
        try {
            context.startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}
