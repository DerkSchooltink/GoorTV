package dev.goor.tv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val MAX_HISTORY = 5
private const val PREFS_NAME = "search_history"
private const val KEY_QUERIES = "queries"

class SearchHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val updated = (listOf(trimmed) + _history.value.filter { it != trimmed }).take(MAX_HISTORY)
        _history.value = updated
        prefs.edit().putString(KEY_QUERIES, Json.encodeToString(updated)).apply()
    }

    private fun load(): List<String> {
        val json = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
