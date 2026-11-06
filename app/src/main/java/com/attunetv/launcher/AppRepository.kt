package com.attunetv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppEntry(
    val key: String,            // flattened ComponentName, stable id for prefs
    val packageName: String,
    val label: String,
    val banner: Drawable?,      // TV banner (16:9) when the app ships one
    val icon: Drawable,
    val launchIntent: Intent,
    val hidden: Boolean,
    val color: Int,             // dominant artwork color, for tile tint + focus glow
)

/** Discovers launchable apps, preferring TV (leanback) entry points. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    fun loadApps(prefs: Prefs): List<AppEntry> {
        val byPackage = LinkedHashMap<String, AppEntry>()
        val hidden = prefs.hidden

        fun collect(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            val infos = pm.queryIntentActivities(intent, 0)
            for (ri in infos) {
                val ai = ri.activityInfo
                if (ai.packageName == context.packageName) continue
                if (ai.packageName in EXCLUDED_PACKAGES) continue
                if (byPackage.containsKey(ai.packageName)) continue // leanback wins
                val cn = ComponentName(ai.packageName, ai.name)
                val key = cn.flattenToShortString()
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                val label = niceLabel(ri.loadLabel(pm).toString(), ai.packageName)
                val banner = loadBanner(ai.packageName, ri.activityInfo.banner, ai.applicationInfo.banner)
                val icon = ri.loadIcon(pm)
                byPackage[ai.packageName] = AppEntry(
                    key = key,
                    packageName = ai.packageName,
                    label = label,
                    banner = banner,
                    icon = icon,
                    launchIntent = launch,
                    hidden = key in hidden || (key !in prefs.unhidden && label in DEFAULT_HIDDEN_LABELS),
                    color = colorCache.getOrPut(key) { Colors.dominant(banner ?: icon) },
                )
            }
        }

        collect(Intent.CATEGORY_LEANBACK_LAUNCHER)
        collect(Intent.CATEGORY_LAUNCHER)

        val order = prefs.appOrder.withIndex().associate { it.value to it.index }
        return byPackage.values.sortedWith(
            compareBy<AppEntry> { order[it.key] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() }
        )
    }

    /** Some sideloaded apps have no label and show their package name. Tidy those up. */
    private fun niceLabel(label: String, pkg: String): String {
        if (label != pkg && !Regex("^[a-z0-9_]+(\\.[a-z0-9_]+)+$", RegexOption.IGNORE_CASE).matches(label)) return label
        KNOWN_LABELS[pkg]?.let { return it }
        val part = pkg.split('.').filter { it !in setOf("com", "tv", "android", "amazon", "app", "firetv", "ftv") }
            .maxByOrNull { it.length } ?: return label
        return part.replaceFirstChar { it.uppercase() }
    }

    private fun loadBanner(pkg: String, activityBanner: Int, appBanner: Int): Drawable? {
        val res = if (activityBanner != 0) activityBanner else appBanner
        if (res == 0) return null
        return try {
            pm.getResourcesForApplication(pkg).getDrawable(res, null)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private val colorCache = HashMap<String, Int>()

        /** Amazon system shells we never want as tiles. */
        val EXCLUDED_PACKAGES = setOf(
            "com.amazon.tv.launcher",
        )

        /** Amazon system pieces that show up as "apps" but aren't useful tiles. Unhide via ☰ → Unhide. */
        val DEFAULT_HIDDEN_LABELS = setOf("Fire TV Profile UI")

        private val KNOWN_LABELS = mapOf(
            "com.apple.airplay.amazon" to "AirPlay",
        )

        /** First-run favorites, picked from whatever is installed. */
        val DEFAULT_FAVORITE_PACKAGES = listOf(
            "com.netflix.ninja",
            "com.amazon.firebat",                 // Prime Video
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",  // Prime Video (older)
            "com.disney.disneyplus",
            "com.hbo.hbonow",                     // Max
            "com.wbd.stream",                     // Max (newer)
            "com.hulu.plus",
            "com.plexapp.android",
            "com.spotify.tv.android",
            "tv.pluto.android",
            "com.apple.atve.amazon.appletv",
            "com.peacocktv.peacockandroid",
            "com.cbs.ott",                        // Paramount+
            "com.amazon.tv.settings.v2",
        )
    }
}
