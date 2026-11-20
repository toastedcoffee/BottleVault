// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.common.text

/**
 * Near-match scoring behind "did you mean". It works on names already run through
 * [NameNormalizer.normalize]; it never normalizes anything itself, so there is
 * still exactly one normalizer.
 *
 * A match key is the normalized name minus one leading article and one trailing
 * producer suffix, so "The Macallan" meets "Macallan" and "Sierra Nevada Brewing
 * Co." meets "Sierra Nevada Brewery". Two keys are near when their Levenshtein
 * distance is within a threshold that grows with length (see [threshold]).
 *
 * Nothing here is stored and no SQL fuzzy function is used: `pg_trgm` and its kind
 * do not exist on H2, which backs the dev profile.
 */
object NameMatcher {

    /** Dropped once from the front of a name. Lowercase, as normalize() leaves them. */
    val ARTICLES: List<String> = listOf("the", "a", "an", "el", "la", "le", "les", "los", "las", "il")

    /**
     * Dropped once from the end of a name. Longest first, so "brewing company" is
     * taken whole rather than leaving "brewing" behind. Punctuation is already gone
     * by the time a name gets here, so "Brewing Co." arrives as "brewing co".
     */
    val PRODUCER_SUFFIXES: List<String> = listOf(
        "distilling company", "brewing company", "beer company", "wine company",
        "distilling co", "brewing co", "beer co", "wine co",
        "distilleries", "distillery", "distillers", "distilling",
        "brewery", "brewers", "brewing",
        "vineyards", "vineyard", "winery", "wines", "cellars",
        "spirits",
    ).sortedByDescending { it.length }

    /**
     * The normalized name without one leading article and one trailing suffix. A
     * name that is nothing but an article or a suffix ("The", "Brewery") keeps
     * that part, so a key is never empty.
     */
    fun matchKey(normalized: String): String {
        var key = normalized
        ARTICLES.firstOrNull { key.startsWith("$it ") && key.length > it.length + 1 }
            ?.let { key = key.substring(it.length + 1) }
        PRODUCER_SUFFIXES.firstOrNull { key.endsWith(" $it") && key.length > it.length + 1 }
            ?.let { key = key.substring(0, key.length - it.length - 1) }
        return key
    }

    /** Edits allowed between two keys: 1 up to 8 characters, 2 up to 16, 3 beyond, by the longer key. */
    fun threshold(a: String, b: String): Int {
        val longer = maxOf(a.length, b.length)
        return when {
            longer <= 8 -> 1
            longer <= 16 -> 2
            else -> 3
        }
    }

    fun isNear(typedKey: String, candidateKey: String): Boolean {
        val max = threshold(typedKey, candidateKey)
        return distance(typedKey, candidateKey, max) <= max
    }

    /**
     * Levenshtein distance between [a] and [b], or `max + 1` as soon as it is
     * certain to exceed [max]. The early exits keep a scan of a few thousand
     * candidates cheap: most are rejected on length alone.
     */
    fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > max) return max + 1
            val swap = previous
            previous = current
            current = swap
        }
        return minOf(previous[b.length], max + 1)
    }

    /** A candidate row for [rank]: its id and its stored normalized name. */
    data class Candidate<ID>(val id: ID, val normalizedName: String)

    /**
     * The ids of at most [limit] candidates near [typedNormalized], nearest first,
     * then by name. A candidate whose normalized name equals the typed one is never
     * returned: that is not a suggestion, it is the row the create would return.
     */
    fun <ID> rank(typedNormalized: String, candidates: List<Candidate<ID>>, limit: Int): List<ID> {
        val typedKey = matchKey(typedNormalized)
        return candidates.asSequence()
            .filter { it.normalizedName != typedNormalized }
            .mapNotNull { c ->
                val key = matchKey(c.normalizedName)
                val max = threshold(typedKey, key)
                val d = distance(typedKey, key, max)
                if (d <= max) Triple(c.id, d, c.normalizedName) else null
            }
            .sortedWith(compareBy({ it.second }, { it.third }))
            .take(limit)
            .map { it.first }
            .toList()
    }
}
