package com.yungmoolah.converter

import com.yungmoolah.converter.data.RatesSnapshot
import com.yungmoolah.converter.domain.convert
import com.yungmoolah.converter.domain.unitRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversionTest {

    private val snapshot = RatesSnapshot(
        baseCode = "USD",
        rates = mapOf("USD" to 1.0, "EUR" to 0.9, "JPY" to 150.0, "GBP" to 0.8, "BROKEN" to 0.0),
        fetchedAtMillis = 1_000L,
        ratesUpdatedAtMillis = 1_000L,
        nextUpdateAtMillis = 100_000L,
    )

    @Test
    fun `converts from the snapshot base`() {
        assertEquals(90.0, convert(100.0, "USD", "EUR", snapshot)!!, 1e-9)
    }

    @Test
    fun `converts between two non-base currencies via the cross rate`() {
        // 100 EUR -> USD is 111.11..., then -> JPY at 150
        assertEquals(100.0 / 0.9 * 150.0, convert(100.0, "EUR", "JPY", snapshot)!!, 1e-9)
    }

    @Test
    fun `round trip returns the original amount`() {
        val there = convert(250.0, "GBP", "JPY", snapshot)!!
        assertEquals(250.0, convert(there, "JPY", "GBP", snapshot)!!, 1e-9)
    }

    @Test
    fun `same currency is an identity`() {
        assertEquals(42.5, convert(42.5, "EUR", "EUR", snapshot)!!, 1e-9)
    }

    @Test
    fun `zero converts to zero`() {
        assertEquals(0.0, convert(0.0, "USD", "JPY", snapshot)!!, 1e-9)
    }

    @Test
    fun `unknown currency yields null rather than a wrong number`() {
        assertNull(convert(10.0, "USD", "XXX", snapshot))
        assertNull(convert(10.0, "XXX", "USD", snapshot))
    }

    @Test
    fun `a zero or non-finite rate yields null`() {
        assertNull(convert(10.0, "USD", "BROKEN", snapshot))
        assertNull(convert(10.0, "BROKEN", "USD", snapshot))
    }

    @Test
    fun `non-finite input yields null`() {
        assertNull(convert(Double.NaN, "USD", "EUR", snapshot))
        assertNull(convert(Double.POSITIVE_INFINITY, "USD", "EUR", snapshot))
    }

    @Test
    fun `unit rate reports one unit of the source`() {
        assertEquals(0.9, unitRate("USD", "EUR", snapshot)!!, 1e-9)
        assertEquals(150.0 / 0.9, unitRate("EUR", "JPY", snapshot)!!, 1e-9)
    }

    @Test
    fun `staleness follows the provider's next update time`() {
        assertEquals(false, snapshot.isStale(99_999L))
        assertEquals(true, snapshot.isStale(100_000L))
        assertEquals(false, snapshot.copy(nextUpdateAtMillis = 0L).isStale(Long.MAX_VALUE))
    }
}
