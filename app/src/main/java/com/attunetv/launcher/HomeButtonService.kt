package com.attunetv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS doesn't let you pick a default home app. This service watches for Amazon's
 * launcher coming to the foreground (Home button, boot, exiting an app) and immediately
 * brings AttuneTV back on top instead.
 */
class HomeButtonService : AccessibilityService() {

    private var lastRedirect = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in AMAZON_HOME_PACKAGES) return

        val cls = event.className?.toString().orEmpty()
        lastSeenClass = cls
        if (!HomeRedirectPolicy.isHome(pkg, cls)) return

        val now = SystemClock.elapsedRealtime()
        if (now < bypassUntil) return
        if (now - lastRedirect < DEBOUNCE_MS) return
        if (!Prefs(this).redirectEnabled) return
        if (PASSTHROUGH_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }) return

        lastRedirect = now
        startActivity(
            Intent(this, HomeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        val AMAZON_HOME_PACKAGES = setOf("com.amazon.tv.launcher")

        /**
         * Amazon launcher screens we should NOT hijack (e.g. dialogs it hosts).
         * Extend this if a specific Amazon screen gets bounced back home.
         */
        val PASSTHROUGH_CLASS_HINTS = listOf("Dialog", "Toast", "Profile")

        private const val DEBOUNCE_MS = 800L

        /** While now < bypassUntil the real Amazon home is allowed to show. */
        @Volatile var bypassUntil = 0L
        @Volatile var lastSeenClass: String? = null

        fun allowAmazonHome(seconds: Int = 20) {
            bypassUntil = SystemClock.elapsedRealtime() + seconds * 1000L
        }

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeButtonService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
