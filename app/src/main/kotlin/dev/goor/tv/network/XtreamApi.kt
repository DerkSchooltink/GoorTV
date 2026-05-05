package dev.goor.tv.network

import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import io.ktor.client.call.*
import io.ktor.client.request.*
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
        val base = source.url.trimEnd('/')
        val u = source.username
        val p = source.password
        val remote: List<XtreamChannel> = httpClient
            .get("$base/player_api.php?username=$u&password=$p&action=get_live_streams")
            .body()
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
