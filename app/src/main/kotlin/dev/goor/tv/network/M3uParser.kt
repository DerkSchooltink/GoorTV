package dev.goor.tv.network

import dev.goor.tv.data.model.Channel

object M3uParser {
    fun parse(sourceId: Long, content: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXTINF")) {
                val name = line.substringAfterLast(",").trim()
                val group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1)
                val logo = Regex("""tvg-logo="([^"]*)"""").find(line)?.groupValues?.get(1)
                val url = lines.getOrNull(i + 1)?.trim()
                if (!url.isNullOrBlank() && !url.startsWith("#")) {
                    channels.add(
                        Channel(
                            sourceId = sourceId,
                            name = name,
                            url = url,
                            group = group?.takeIf { it.isNotBlank() },
                            logoUrl = logo?.takeIf { it.isNotBlank() },
                        )
                    )
                }
                i += 2
            } else {
                i++
            }
        }
        return channels
    }
}
