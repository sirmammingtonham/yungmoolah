package com.yungmoolah.converter

import com.yungmoolah.converter.domain.formatAmount
import com.yungmoolah.converter.domain.formatForEditing
import com.yungmoolah.converter.domain.formatRate
import com.yungmoolah.converter.domain.parseAmount
import com.yungmoolah.converter.domain.sanitizeAmountInput
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountFormatTest {

    @Test
    fun `accepts digits and a single decimal point`() {
        assertEquals("12", sanitizeAmountInput("12", ""))
        assertEquals("12.5", sanitizeAmountInput("12.5", "12"))
        assertEquals("0.05", sanitizeAmountInput("0.05", "0.0"))
    }

    @Test
    fun `treats a comma as a decimal separator`() {
        assertEquals("12.5", sanitizeAmountInput("12,5", "12"))
    }

    @Test
    fun `rejects letters, symbols and a second decimal point`() {
        assertEquals("12", sanitizeAmountInput("12a", "12"))
        assertEquals("12", sanitizeAmountInput("12-", "12"))
        assertEquals("12.5", sanitizeAmountInput("12.5.5", "12.5"))
    }

    @Test
    fun `rejects entries that are too long`() {
        assertEquals("1", sanitizeAmountInput("1234567890123", "1"))
        assertEquals("1.5", sanitizeAmountInput("1.5000000", "1.5"))
    }

    @Test
    fun `collapses leading zeros but keeps zero typable`() {
        assertEquals("7", sanitizeAmountInput("07", "0"))
        assertEquals("0", sanitizeAmountInput("0", ""))
        assertEquals("0.5", sanitizeAmountInput("0.5", "0"))
    }

    @Test
    fun `clearing the field is allowed`() {
        assertEquals("", sanitizeAmountInput("", "123"))
    }

    @Test
    fun `parses partial entries as zero`() {
        assertEquals(0.0, parseAmount(""), 1e-9)
        assertEquals(0.0, parseAmount("."), 1e-9)
        assertEquals(1234.5, parseAmount("1234.5"), 1e-9)
    }

    @Test
    fun `formats with grouping and the currency's own decimal places`() {
        assertEquals("1,234.57", formatAmount(1234.567, "USD"))
        assertEquals("1,235", formatAmount(1234.567, "JPY"))
    }

    @Test
    fun `keeps the currency's own precision for ordinary values`() {
        assertEquals("0.87", formatAmount(0.87, "USD"))
        assertEquals("0.13", formatAmount(0.125, "USD"))
        assertEquals("2.35", formatAmount(2.345, "USD"))
        assertEquals("0.00", formatAmount(0.0, "USD"))
    }

    @Test
    fun `widens precision only when the value would round away to zero`() {
        assertEquals("0.004", formatAmount(0.004, "USD"))
        assertEquals("0.00012", formatAmount(0.000123, "USD"))
        // A yen amount below one whole yen still gets shown rather than hidden.
        assertEquals("0.4", formatAmount(0.4, "JPY"))
    }

    @Test
    fun `clamps precision for values below the widest precision`() {
        assertEquals("0", formatAmount(1e-12, "JPY"))
        assertEquals("0.00", formatAmount(1e-12, "USD"))
    }

    @Test
    fun `editing text drops grouping and trailing zeros`() {
        assertEquals("1234.5", formatForEditing(1234.5, "USD"))
        assertEquals("1234", formatForEditing(1234.0, "USD"))
        assertEquals("", formatForEditing(0.0, "USD"))
    }

    @Test
    fun `editing text keeps whole numbers intact for zero-decimal currencies`() {
        // Regression: trimming trailing zeros blindly turned 100 JPY into 1.
        assertEquals("100", formatForEditing(100.0, "JPY"))
        assertEquals("1000", formatForEditing(1000.0, "JPY"))
        assertEquals("100", formatForEditing(100.0, "USD"))
    }

    @Test
    fun `every sanitized value round-trips through the parser`() {
        for (raw in listOf("1", "0", "0.5", "1234.56", "999999999999", "0.000001")) {
            assertEquals(raw, sanitizeAmountInput(raw, "x"))
            parseAmount(raw) // must not throw
        }
    }

    @Test
    fun `rates carry more precision than wallet amounts`() {
        assertEquals("0.9234", formatRate(0.92341))
        assertEquals("150.75", formatRate(150.7512))
        assertEquals("1.0850", formatRate(1.085))
        assertEquals("0.000123", formatRate(0.000123))
        assertEquals("—", formatRate(0.0))
    }
}
