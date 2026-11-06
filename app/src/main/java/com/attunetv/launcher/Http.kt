package com.attunetv.launcher

import java.net.HttpURLConnection
import java.net.URL

object Http {
    fun get(url: String, timeoutMs: Int = 10_000): String {
        val u = url.replaceFirst(Regex("^webcal://", RegexOption.IGNORE_CASE), "https://")
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "AttuneTV/1.0 (Android TV launcher)")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code for $u")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
