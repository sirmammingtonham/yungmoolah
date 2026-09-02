package com.yungmoolah.converter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.moolahDataStore: DataStore<Preferences> by preferencesDataStore(name = "moolah")

/**
 * Persists the last downloaded snapshot and the user's pinned currencies.
 *
 * The snapshot is stored as JSON in DataStore rather than a database: it is a
 * single small document that is always read and written whole, so a table would
 * only add ceremony.
 *
 * Takes the [DataStore] rather than a [Context] so tests can hand it an isolated
 * one; production code uses [create].
 */
class RatesStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The cached snapshot, or null when nothing has been downloaded yet. */
    val snapshot: Flow<RatesSnapshot?> = store.data.map { prefs ->
        prefs[KEY_SNAPSHOT]?.let { raw ->
            try {
                json.decodeFromString<RatesSnapshot>(raw)
            } catch (e: SerializationException) {
                // A snapshot written by an older, incompatible build: treat as absent
                // so the app refetches instead of crashing on launch.
                null
            }
        }
    }

    /** Pinned currency codes in the order the user arranged them. */
    val pinned: Flow<List<String>> = store.data.map { prefs ->
        val ordered = prefs[KEY_PINNED_ORDER]
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() && CURRENCY_BY_CODE.containsKey(it) }
        if (ordered.isNullOrEmpty()) DEFAULT_PINNED else ordered
    }

    /** The code whose amount the user last typed into. */
    val activeCode: Flow<String?> = store.data.map { prefs -> prefs[KEY_ACTIVE_CODE] }

    suspend fun saveSnapshot(snapshot: RatesSnapshot) {
        val encoded = json.encodeToString(RatesSnapshot.serializer(), snapshot)
        store.edit { prefs -> prefs[KEY_SNAPSHOT] = encoded }
    }

    suspend fun savePinned(codes: List<String>) {
        val cleaned = codes.filter { CURRENCY_BY_CODE.containsKey(it) }.distinct()
        store.edit { prefs -> prefs[KEY_PINNED_ORDER] = cleaned.joinToString(SEPARATOR) }
    }

    suspend fun saveActiveCode(code: String) {
        store.edit { prefs -> prefs[KEY_ACTIVE_CODE] = code }
    }

    companion object {
        /** The app-wide store, backed by a single file in the app's data directory. */
        fun create(context: Context): RatesStore = RatesStore(context.applicationContext.moolahDataStore)

        private const val SEPARATOR = ","
        private val KEY_SNAPSHOT = stringPreferencesKey("rates_snapshot")
        private val KEY_PINNED_ORDER = stringPreferencesKey("pinned_order")
        private val KEY_ACTIVE_CODE = stringPreferencesKey("active_code")
    }
}
