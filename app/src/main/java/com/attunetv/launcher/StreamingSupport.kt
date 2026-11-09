package com.attunetv.launcher

object StreamingSupport {
    val candidates = linkedMapOf(
        "com.amazon.firebat" to "Prime Video", "com.netflix.ninja" to "Netflix",
        "com.apple.atve.amazon.appletv" to "Apple TV", "com.disney.disneyplus" to "Disney+",
        "com.hulu.plus" to "Hulu", "com.hbo.hbonow" to "HBO Max",
        "com.peacock.peacockfiretv" to "Peacock", "com.cbs.ott" to "Paramount+"
    )
    // Native panel focus/IME/Back and playback must be verified per app on a real TV.
    // Mac-prototype compatibility does not qualify an app for this release list.
    val verifiedPackages: Set<String> = emptySet()
    fun supported(pkg: String?) = pkg != null && pkg in verifiedPackages && pkg in candidates
}
