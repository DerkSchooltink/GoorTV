package dev.goor.tv.network

import dev.goor.tv.data.model.Channel

data class M3uParseResult(
    val urlTvg: String?,
    val channels: List<Channel>,
)

object M3uParser {
    private val GROUP_TITLE = Regex("""group-title="([^"]*)"""")
    private val TVG_LOGO = Regex("""tvg-logo="([^"]*)"""")
    private val TVG_ID = Regex("""tvg-id="([^"]*)"""")
    private val URL_TVG_HEADER = Regex("""(?:url-tvg|x-tvg-url)="([^"]*)"""")

    fun parse(sourceId: Long, content: String): M3uParseResult {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0
        var urlTvg: String? = null

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTM3U") && urlTvg == null) {
                URL_TVG_HEADER.find(line)?.groupValues?.get(1)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { urlTvg = it.substringBefore(",").trim().ifBlank { null } }
            }
            if (line.startsWith("#EXTINF")) {
                val name = line.substringAfterLast(",").trim()
                val group = GROUP_TITLE.find(line)?.groupValues?.get(1)
                val logo = TVG_LOGO.find(line)?.groupValues?.get(1)
                val tvgId = TVG_ID.find(line)?.groupValues?.get(1)
                var j = i + 1
                while (j < lines.size) {
                    val candidate = lines[j].trim()
                    if (candidate.isEmpty() || candidate.startsWith("#")) {
                        j++
                        continue
                    }
                    break
                }
                val url = lines.getOrNull(j)?.trim()
                if (!url.isNullOrBlank() && !url.startsWith("#")) {
                    channels.add(
                        Channel(
                            sourceId = sourceId,
                            name = name,
                            url = url,
                            group = group?.takeIf { it.isNotBlank() },
                            logoUrl = logo?.takeIf { it.isNotBlank() },
                            tvgChannelId = tvgId?.takeIf { it.isNotBlank() },
                        )
                    )
                    i = j + 1
                } else {
                    i++
                }
            } else {
                i++
            }
        }
        return M3uParseResult(urlTvg = urlTvg, channels = channels)
    }
}
