package com.attunetv.launcher

/** Exact app identities; the Mac additionally gates these on its verified support list. */
object CompanionPolicy {
    val packages = setOf(
        "com.amazon.firebat", "com.netflix.ninja", "com.apple.atve.amazon.appletv",
        "com.disney.disneyplus", "com.hulu.plus", "com.hbo.hbonow",
        "com.peacock.peacockfiretv", "com.cbs.ott"
    )

    fun canShow(pkg: String) = pkg in packages
    fun shouldHide(expected: String?, incoming: String) =
        expected != null && incoming != expected && incoming != "com.attunetv.launcher"
}
