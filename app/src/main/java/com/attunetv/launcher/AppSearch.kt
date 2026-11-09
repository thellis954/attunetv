package com.attunetv.launcher

import java.text.Normalizer
import java.util.Locale

/** Match all words in a query, ignoring accents, case and repeated spaces. */
object AppSearch {
    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    fun matches(label: String, query: String): Boolean {
        val name = normalize(label)
        return normalize(query).trim().split(Regex("\\s+")).all { it in name }
    }
}
