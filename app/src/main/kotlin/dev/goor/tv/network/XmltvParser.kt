package dev.goor.tv.network

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

object XmltvParser {

    data class ParsedProgramme(
        val tvgChannelId: String,
        val startMs: Long,
        val endMs: Long,
        val title: String,
        val description: String?,
        val category: String?,
        val iconUrl: String?,
    )

    data class ParsedChannel(
        val tvgChannelId: String,
        val displayNames: List<String>,
    )

    /**
     * Stream-parses XMLTV from [input], invoking [onChannel] for each `<channel>` block and
     * [onProgramme] for each valid `<programme>` block. Malformed elements are logged and
     * skipped. Caller owns [input] and is responsible for closing it.
     *
     * [onChannel] has a no-op default so existing call sites that only care about programmes
     * can keep the trailing-lambda form: `parse(stream) { p -> … }`. Callers that need both
     * must pass [onChannel] as a named argument.
     */
    suspend fun parse(
        input: InputStream,
        onChannel: suspend (ParsedChannel) -> Unit = {},
        onProgramme: suspend (ParsedProgramme) -> Unit,
    ) {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let { onChannel(it) }
                    "programme" -> readProgramme(parser)?.let { onProgramme(it) }
                }
            }
            event = try {
                parser.next()
            } catch (e: Exception) {
                Log.w("XmltvParser", "Parser error, aborting: ${e.message}")
                return
            }
        }
    }

    private fun readChannel(parser: XmlPullParser): ParsedChannel? {
        val id = parser.getAttributeValue(null, "id")?.takeIf { it.isNotBlank() } ?: run {
            drainElement(parser)
            return null
        }
        val names = mutableListOf<String>()
        var depth = 1
        while (depth > 0) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                Log.w("XmltvParser", "Malformed channel element skipped: ${e.message}")
                return null
            }
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (parser.name == "display-name") {
                        readText(parser).takeIf { it.isNotBlank() }?.let(names::add)
                        depth--
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return null
            }
        }
        return ParsedChannel(tvgChannelId = id, displayNames = names)
    }

    private fun drainElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                Log.w("XmltvParser", "Parser error while draining malformed element: ${e.message}")
                return
            }
            when (event) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun readProgramme(parser: XmlPullParser): ParsedProgramme? {
        val channel = parser.getAttributeValue(null, "channel")
        val startAttr = parser.getAttributeValue(null, "start")
        val stopAttr = parser.getAttributeValue(null, "stop")
        val startMs = startAttr?.let(::parseXmltvTime)
        val endMs = stopAttr?.let(::parseXmltvTime)

        var title: String? = null
        var description: String? = null
        var category: String? = null
        var iconUrl: String? = null

        var depth = 1
        while (depth > 0) {
            val event = try {
                parser.next()
            } catch (e: Exception) {
                Log.w("XmltvParser", "Malformed programme element skipped: ${e.message}")
                return null
            }
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    when (parser.name) {
                        "title" -> if (title == null) title = readText(parser).also { depth-- }
                        "desc" -> if (description == null) description = readText(parser).also { depth-- }
                        "category" -> if (category == null) category = readText(parser).also { depth-- }
                        "icon" -> {
                            if (iconUrl == null) iconUrl = parser.getAttributeValue(null, "src")
                        }
                    }
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return null
            }
        }

        if (channel.isNullOrBlank() || startMs == null || endMs == null || title.isNullOrBlank()) return null
        if (endMs <= startMs) return null

        return ParsedProgramme(
            tvgChannelId = channel,
            startMs = startMs,
            endMs = endMs,
            title = title!!,
            description = description?.takeIf { it.isNotBlank() },
            category = category?.takeIf { it.isNotBlank() },
            iconUrl = iconUrl?.takeIf { it.isNotBlank() },
        )
    }

    /** Reads text content of the current START_TAG up to and including its END_TAG. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var event = parser.next()
        while (event != XmlPullParser.END_TAG) {
            if (event == XmlPullParser.TEXT) sb.append(parser.text)
            event = parser.next()
        }
        return sb.toString().trim()
    }

    /**
     * Parses XMLTV time literal: `YYYYMMDDHHMMSS [+-]HHMM`. Seconds and timezone optional.
     * If timezone missing, assumes UTC. Returns epoch milliseconds (UTC).
     * Returns null on malformed input.
     */
    @Suppress("ReturnCount")
    fun parseXmltvTime(raw: String): Long? {
        val s = raw.trim()
        if (s.length < 12) return null
        try {
            val year = s.substring(0, 4).toInt()
            val month = s.substring(4, 6).toInt()
            val day = s.substring(6, 8).toInt()
            val hour = s.substring(8, 10).toInt()
            val minute = s.substring(10, 12).toInt()

            val rest = s.substring(12)
            // Seconds (optional, 2 digits)
            var idx = 0
            val second = if (rest.length >= 2 && rest[0].isDigit() && rest[1].isDigit()) {
                idx = 2
                rest.substring(0, 2).toInt()
            } else 0

            // Optional whitespace then optional timezone ±HHMM
            while (idx < rest.length && rest[idx].isWhitespace()) idx++
            var tzOffsetMinutes = 0
            if (idx < rest.length && (rest[idx] == '+' || rest[idx] == '-')) {
                val sign = if (rest[idx] == '+') 1 else -1
                idx++
                if (idx + 4 > rest.length) return null
                val tzH = rest.substring(idx, idx + 2).toInt()
                val tzM = rest.substring(idx + 2, idx + 4).toInt()
                tzOffsetMinutes = sign * (tzH * 60 + tzM)
            }

            return utcEpochMs(year, month, day, hour, minute, second) - tzOffsetMinutes * 60_000L
        } catch (e: NumberFormatException) {
            // Expected for dirty EPG data — malformed literals are the documented null case.
            return null
        } catch (e: StringIndexOutOfBoundsException) {
            // Every substring above is bounds-guarded, so this firing means a parser bug,
            // not bad input — log it instead of silently treating it as malformed data.
            Log.w("XmltvParser", "Unexpected bounds error parsing time literal: ${e.message}")
            return null
        }
    }

    /** Days since Unix epoch for the given proleptic Gregorian date (UTC). */
    private fun utcEpochMs(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        // Howard Hinnant's date algorithm — no allocations, no Calendar.
        val yAdj = if (mo <= 2) y - 1 else y
        val era = if (yAdj >= 0) yAdj / 400 else (yAdj - 399) / 400
        val yoe = (yAdj - era * 400)
        val moAdj = if (mo > 2) mo - 3 else mo + 9
        val doy = (153 * moAdj + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val daysSinceEpoch = era.toLong() * 146097L + doe.toLong() - 719468L
        return daysSinceEpoch * 86_400_000L + h * 3_600_000L + mi * 60_000L + s * 1_000L
    }
}
