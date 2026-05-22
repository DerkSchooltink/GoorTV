package dev.goor.tv.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the app's HTTP client. Defaults to the Android engine in production
 * (registered as a Koin singleton). Tests pass [MockEngine] via the [engine]
 * parameter and skip Koin entirely.
 */
fun defaultHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(Android, config)
}
