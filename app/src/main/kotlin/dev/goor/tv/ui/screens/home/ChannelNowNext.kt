package dev.goor.tv.ui.screens.home

import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme

/**
 * The EPG programmes a Home row can show for one channel.
 *
 * Both fields are nullable: a channel can have no EPG at all (both null), only
 * a next programme (a gap in the schedule), or only a now programme (nothing
 * scheduled afterwards). [hasProgrammes] drives the group-name fallback in the
 * row.
 */
data class ChannelNowNext(
    val now: Programme? = null,
    val next: Programme? = null,
) {
    val hasProgrammes: Boolean get() = now != null || next != null
}

/**
 * Resolves the programmes for [channel] from the `(sourceId, tvgChannelId)` map
 * built by [HomeViewModel]. Channels without a usable tvg-id — including custom
 * manual channels — resolve to an empty value, so the row keeps the group-name
 * fallback.
 */
internal fun resolveNowNext(
    channel: Channel,
    byChannel: Map<Pair<Long, String>, ChannelNowNext>,
): ChannelNowNext {
    val tvgId = channel.tvgChannelId?.takeIf { it.isNotBlank() } ?: return ChannelNowNext()
    return byChannel[channel.sourceId to tvgId] ?: ChannelNowNext()
}

/**
 * Merges the currently-airing and next-up programme queries into one map keyed
 * by `(sourceId, tvgChannelId)`. A channel that only has a next programme still
 * gets an entry so the row can render `NEXT` without inventing a `NOW`.
 */
internal fun mergeNowNext(
    current: List<Programme>,
    upcoming: List<Programme>,
): Map<Pair<Long, String>, ChannelNowNext> {
    val nowByKey = current.associateBy { it.sourceId to it.tvgChannelId }
    val nextByKey = upcoming.associateBy { it.sourceId to it.tvgChannelId }
    return (nowByKey.keys + nextByKey.keys).associateWith { key ->
        ChannelNowNext(now = nowByKey[key], next = nextByKey[key])
    }
}

/**
 * Escapes the SQLite `LIKE` metacharacters `%` and `_` (and the escape
 * character itself) so a user query is matched **literally**. Use with
 * `LIKE … ESCAPE '\'` in the query; without this, searching for `100%` would
 * match every channel and `_` would match any single character.
 *
 * Matching is still ASCII case-insensitive, which is SQLite's `LIKE` semantics;
 * the pre-existing channel-name search had the same limitation. Do not treat
 * this as a Unicode-aware matcher.
 */
internal fun escapeLikePattern(query: String): String =
    query.replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
