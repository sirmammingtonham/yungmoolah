package com.yungmoolah.converter.data

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/** Outcome of a refresh attempt, so the UI can distinguish "no network" from "fetched". */
sealed interface RefreshResult {
    data class Updated(val snapshot: RatesSnapshot) : RefreshResult
    /** Rates were already current, so nothing was downloaded. */
    data object AlreadyFresh : RefreshResult
    data class Failed(val message: String) : RefreshResult
}

/**
 * Offline-first source of truth for exchange rates.
 *
 * Readers observe [snapshot], which is served straight from disk and therefore
 * available immediately on launch and while offline. [refresh] is the only thing
 * that touches the network, and it never throws: a failure leaves the cached
 * snapshot in place and is reported back as [RefreshResult.Failed].
 */
class RatesRepository(
    private val store: RatesStore,
    private val api: RatesApi = RatesApi(),
) {
    val snapshot: Flow<RatesSnapshot?> = store.snapshot
    val pinned: Flow<List<String>> = store.pinned
    val activeCode: Flow<String?> = store.activeCode

    /**
     * Downloads rates unless [cached] is still current and [force] is false.
     *
     * @param force refetch even when the cached snapshot has not expired, for
     *   an explicit pull-to-refresh.
     */
    suspend fun refresh(
        cached: RatesSnapshot?,
        force: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): RefreshResult {
        if (!force && cached != null && !cached.isStale(nowMillis)) {
            return RefreshResult.AlreadyFresh
        }
        return try {
            val fresh = api.fetchLatest()
            store.saveSnapshot(fresh)
            RefreshResult.Updated(fresh)
        } catch (e: IOException) {
            RefreshResult.Failed(e.message ?: "Could not reach the rates service")
        }
    }

    suspend fun setPinned(codes: List<String>) = store.savePinned(codes)

    suspend fun setActiveCode(code: String) = store.saveActiveCode(code)
}
