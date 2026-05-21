package dev.goor.tv.util

/**
 * Normalises channel display strings for matching playlist channels to XMLTV `<channel>`
 * blocks by name when the provider didn't supply a `tvg-id` / `epg_channel_id`.
 *
 * Strategy: produce one or more lowercase alphanumeric keys. We yield a "base" key plus
 * progressively more aggressive variants (drop a single trailing digit, drop a leading
 * country code) so a channel like "NL - ESPN 1" can still match a display-name "ESPN".
 *
 * Order matters: callers should try variants in the listed order and stop at the first hit.
 *
 * Why only single trailing digits: stripping a multi-digit suffix collapses every "ESPN N"
 * channel into the same bucket, which causes ESPN 11–30 to land on the wrong tvg-id when
 * the EPG only carries display-name "ESPN" for one feed. Single-digit stripping preserves
 * the useful case (ESPN 1 → "espn" → matches lone "ESPN" display-name) without that risk.
 */
object ChannelNameNormalizer {

    private val BRACKETED = Regex("[\\[(].*?[])]")
    private val NON_ALNUM = Regex("[^a-z0-9]")
    // Single-letter tokens (d, n, s) were dropped — they're already stripped via brackets
    // when they appear as `(D)`/`(N)` markers and stripping bare letters would mangle
    // legitimate names like "BBC N" or "RTL S".
    private val QUALITY_TAGS = Regex(
        "\\b(uhd|fhd|hd|sd|4k|hevc|h265|h264|raw|backup|live events|live|events|multi|audio|premium|vip)\\b"
    )
    // 2-letter ISO country-code prefix as a separate token in the *original* string —
    // "NL - ESPN" or "uk bbc", not bare "es" inside "espn". Tested before NON_ALNUM-stripping.
    private val LEADING_CC_TOKEN = Regex("^[a-z]{2}\\b")
    // Single trailing digit preceded by a letter — see KDoc on the object for why.
    // "espn1" → "espn", but "espn10" stays put (the digit is preceded by another digit).
    private val TRAILING_DIGIT = Regex("(?<=[a-z])\\d$")
    // Strip leading zeros from any digit group so "espn02" canonicalises as "espn2".
    private val DIGIT_GROUP = Regex("0+(\\d)")

    /**
     * Returns lookup keys for [name], most-specific first. An empty list means the name
     * had no useful content (after stripping brackets/qualifiers it was blank or numeric).
     */
    fun keys(name: String): List<String> {
        val cleaned = name.lowercase()
            .replace(BRACKETED, " ")
            .replace(QUALITY_TAGS, " ")
            .trim()
        val hasCcPrefix = LEADING_CC_TOKEN.containsMatchIn(cleaned)
        val base = stripLeadingZeros(cleaned.replace(NON_ALNUM, ""))
        if (base.length < MIN_KEY_LEN) return emptyList()

        val variants = LinkedHashSet<String>()
        variants += base

        val withoutTrailingDigit = TRAILING_DIGIT.replaceFirst(base, "")
        if (withoutTrailingDigit.length >= MIN_KEY_LEN) variants += withoutTrailingDigit

        if (hasCcPrefix) {
            val withoutCountry = base.drop(2)
            if (withoutCountry.length >= MIN_KEY_LEN) variants += withoutCountry
            val withoutBoth = TRAILING_DIGIT.replaceFirst(withoutCountry, "")
            if (withoutBoth.length >= MIN_KEY_LEN) variants += withoutBoth
        }

        return variants.toList()
    }

    /** Single canonical key, used when indexing names (e.g. display-name → tvg-id). */
    fun canonical(name: String): String? = keys(name).firstOrNull()

    private fun stripLeadingZeros(s: String): String = DIGIT_GROUP.replace(s) { it.groupValues[1] }

    private const val MIN_KEY_LEN = 2
}
