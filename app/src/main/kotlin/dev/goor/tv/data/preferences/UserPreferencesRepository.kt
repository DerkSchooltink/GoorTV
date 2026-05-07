package dev.goor.tv.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    private val sortOrderKey = stringPreferencesKey("sort_order")

    val sortOrder: Flow<SortOrder> = dataStore.data.map { prefs ->
        prefs[sortOrderKey]?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
            ?: SortOrder.BY_GROUP
    }

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[sortOrderKey] = order.name }
    }
}
