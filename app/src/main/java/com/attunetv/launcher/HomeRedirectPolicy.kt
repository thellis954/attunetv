package com.attunetv.launcher

/** Only the actual Home activity should be intercepted, never search/settings UI. */
object HomeRedirectPolicy {
    fun isHome(packageName: String, className: String): Boolean =
        packageName == "com.amazon.tv.launcher" && className in setOf(
            "com.amazon.tv.launcher.ui.HomeActivity",
            "com.amazon.tv.launcher.ui.HomeActivity_vNext",
        )
}
