package com.yungmoolah.converter.domain

import com.yungmoolah.converter.data.RatesSnapshot

/**
 * Converts [amount] from [from] to [to] using a snapshot of base-relative rates.
 *
 * Both rates are quoted against the snapshot's own base, so the cross rate is
 * their ratio; this is what lets one edited amount drive every pinned row without
 * refetching anything.
 *
 * @return the converted amount, or null when either currency is missing from the
 *   snapshot, so callers can render a placeholder instead of a wrong number.
 */
fun convert(amount: Double, from: String, to: String, snapshot: RatesSnapshot): Double? {
    if (!amount.isFinite()) return null
    if (from == to) return amount
    val fromRate = snapshot.rates[from] ?: return null
    val toRate = snapshot.rates[to] ?: return null
    if (fromRate <= 0.0 || !fromRate.isFinite() || toRate <= 0.0 || !toRate.isFinite()) return null
    val result = amount * (toRate / fromRate)
    return if (result.isFinite()) result else null
}

/** The rate for one unit of [from] expressed in [to], for the "1 USD = 0.92 EUR" line. */
fun unitRate(from: String, to: String, snapshot: RatesSnapshot): Double? =
    convert(1.0, from, to, snapshot)
