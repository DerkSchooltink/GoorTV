package dev.goor.tv.util

/**
 * Normalises channel display strings for matching playlist channels to XMLTV `<channel>`
 * blocks by name when the provider didn't supply a `tvg-id` / `epg_channel_id`.
 *
 * Strategy: produce one or more lowercase alphanumeric keys. We yield a "base" key plus
 * progressively more aggressive variants (drop trailing digits, drop leading country
 * code) so a channel like "NL - ESPN 01" can still match a display-name "ESPN".
 *
 * Order matters: callers should try variants in the listed order and stop at the first hit.
 */
object ChannelNameNormalizer {

    private val BRACKETED = Regex("[\\[(].*?[])]")
    private val NON_ALNUM = Regex("[^a-z0-9]")
    private val QUALITY_TAGS = Regex(
        "\\b(uhd|fhd|hd|sd|4k|hevc|h265|h264|raw|backup|live events|live|events|multi|audio|premium|vip|d|n|s)\\b"
    )
    // 2-letter ISO country code prefix (nl, uk, de, …). Lookahead ensures we leave
    // enough characters behind for a meaningful key.
    private val LEADING_CC = Regex("^[a-z]{2}(?=[a-z0-9]{3,})")
    private val TRAILING_DIGITS = Regex("\\d+$")
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
        val base = cleaned.replace(NON_ALNUM, "").let { stripLeadingZeros(it) }
        if (base.isEmpty()) return emptyList()

        val variants = LinkedHashSet<String>()
        variants += base

        // Drop a trailing channel number ("espn1" → "espn") to widen the match.
        val withoutTrailingDigits = base.replace(TRAILING_DIGITS, "")
        if (withoutTrailingDigits.length >= MIN_KEY_LEN) variants += withoutTrailingDigits

        // Drop a leading 2-3 letter country code ("nlespn" → "espn").
        val withoutCountry = LEADING_CC.replaceFirst(base, "")
        if (withoutCountry.length >= MIN_KEY_LEN && withoutCountry != base) variants += withoutCountry

        val withoutBoth = LEADING_CC.replaceFirst(withoutTrailingDigits, "")
        if (withoutBoth.length >= MIN_KEY_LEN && withoutBoth !in variants) variants += withoutBoth

        return variants.filter { it.length >= MIN_KEY_LEN }
    }

    /** Single canonical key, used when indexing names (e.g. display-name → tvg-id). */
    fun canonical(name: String): String? = keys(name).firstOrNull()

    private fun stripLeadingZeros(s: String): String = DIGIT_GROUP.replace(s) { it.groupValues[1] }

    private const val MIN_KEY_LEN = 2
}
