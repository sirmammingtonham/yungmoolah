package com.yungmoolah.converter.data

import kotlinx.serialization.Serializable

/**
 * One complete set of exchange rates, as persisted to disk.
 *
 * Every rate is expressed relative to [baseCode], so converting between two
 * arbitrary currencies is a ratio of their two rates — see
 * [com.yungmoolah.converter.domain.convert].
 */
@Serializable
data class RatesSnapshot(
    val baseCode: String,
    val rates: Map<String, Double>,
    /** When this device downloaded the snapshot. */
    val fetchedAtMillis: Long,
    /** When the provider last recalculated the rates. */
    val ratesUpdatedAtMillis: Long,
    /** When the provider expects to publish the next set; 0 if unknown. */
    val nextUpdateAtMillis: Long,
) {
    fun hasRate(code: String): Boolean = rates[code]?.let { it > 0.0 && it.isFinite() } == true

    /** True once the provider has published newer rates than the ones we hold. */
    fun isStale(nowMillis: Long): Boolean =
        nextUpdateAtMillis > 0L && nowMillis >= nextUpdateAtMillis
}
