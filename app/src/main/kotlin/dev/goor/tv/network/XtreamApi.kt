package dev.goor.tv.network

import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamChannel(
    @SerialName("stream_id") val streamId: Long,
    @SerialName("name") val name: String,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("category_name") val category: String? = null,
)

object XtreamApi {
    suspend fun fetchLiveChannels(source: Source): List<Channel> {
        val parsed = Url(source.url)
        val base = "${parsed.protocol.name}://${parsed.host}:${parsed.specifiedPort}"
        val u = source.username
        val p = source.password
        val response = httpClient.get("$base/player_api.php?username=$u&password=$p&action=get_live_streams")
        if (!response.status.isSuccess()) {
            error("Xtream API returned ${response.status.value} for source ${source.name}")
        }
        val remote: List<XtreamChannel> = response.body()
        return remote.map {
            Channel(
                sourceId = source.id,
                name = it.name,
                url = "$base/live/$u/$p/${it.streamId}.ts",
                group = it.category,
                logoUrl = it.icon,
            )
        }
    }
}
