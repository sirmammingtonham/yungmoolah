package com.yungmoolah.converter

import com.yungmoolah.converter.domain.editAmount
import com.yungmoolah.converter.domain.groupForEditing
import org.junit.Assert.assertEquals
import org.junit.Test

/** The grouped-as-you-type entry field: display formatting and edit intent. */
class AmountEditingTest {

    // --- grouping -------------------------------------------------------------

    @Test
    fun `groups the integer part in threes`() {
        assertEquals("1", groupForEditing("1"))
        assertEquals("999", groupForEditing("999"))
        assertEquals("1,000", groupForEditing("1000"))
        assertEquals("12,345", groupForEditing("12345"))
        assertEquals("123,456,789", groupForEditing("123456789"))
    }

    @Test
    fun `leaves the decimal part exactly as typed`() {
        assertEquals("1,234.5", groupForEditing("1234.5"))
        assertEquals("1,234.50", groupForEditing("1234.50"))
        assertEquals("0.007", groupForEditing("0.007"))
    }

    @Test
    fun `keeps a trailing decimal point so the next keystroke lands after it`() {
        assertEquals("1,234.", groupForEditing("1234."))
        assertEquals(".", groupForEditing("."))
    }

    @Test
    fun `an empty entry displays as empty`() {
        assertEquals("", groupForEditing(""))
    }

    @Test
    fun `a separator is never the last character, so deletion always removes a digit`() {
        for (raw in listOf("1", "12", "123", "1234", "12345", "123456", "1234567", "1234.5")) {
            val display = groupForEditing(raw)
            assertEquals("$display ends in a separator", true, display.last() != ',')
        }
    }

    // --- typing ---------------------------------------------------------------

    private fun type(start: String, keys: String): String {
        var raw = start
        for (key in keys) {
            val display = groupForEditing(raw)
            raw = editAmount(raw, display, display + key)
        }
        return raw
    }

    @Test
    fun `typing digits builds the entry`() {
        assertEquals("1234567", type("", "1234567"))
        assertEquals("1,234,567", groupForEditing(type("", "1234567")))
    }

    @Test
    fun `typing a decimal point and decimals works`() {
        assertEquals("12.75", type("", "12.75"))
    }

    @Test
    fun `a comma from the keyboard means a decimal point, not a separator`() {
        // Some locales' number pads offer only a comma.
        assertEquals("12.75", type("", "12,75"))
    }

    @Test
    fun `characters the sanitiser rejects are dropped, and typing carries on`() {
        assertEquals("12", type("", "12abc"))
        // The second point is refused; the digit after it still lands.
        assertEquals("1.55", type("", "1.5.5"))
    }

    @Test
    fun `typing continues correctly across a grouping boundary`() {
        // The display gains a separator at 1000, which must not shift the entry.
        var raw = type("", "999")
        assertEquals("999", raw)
        raw = type(raw, "9")
        assertEquals("9999", raw)
        assertEquals("9,999", groupForEditing(raw))
    }

    // --- deleting -------------------------------------------------------------

    private fun backspace(raw: String, times: Int = 1): String {
        var current = raw
        repeat(times) {
            val display = groupForEditing(current)
            current = editAmount(current, display, display.dropLast(1))
        }
        return current
    }

    @Test
    fun `backspace removes one digit at a time`() {
        assertEquals("123", backspace("1234"))
        assertEquals("12", backspace("1234", times = 2))
        assertEquals("", backspace("1234", times = 4))
    }

    @Test
    fun `backspace across a separator still removes exactly one digit`() {
        // "1,000" -> deleting the last character must give 100, not 1000 unchanged.
        assertEquals("100", backspace("1000"))
        assertEquals("1234", backspace("12345"))
    }

    @Test
    fun `backspace removes a trailing decimal point`() {
        assertEquals("12", backspace("12."))
        assertEquals("12.", backspace("12.5"))
    }

    @Test
    fun `clearing the whole field empties the entry`() {
        assertEquals("", editAmount("1234", "1,234", ""))
    }

    // --- anything else --------------------------------------------------------

    @Test
    fun `a pasted value is read for its digits`() {
        assertEquals("4321.5", editAmount("1234", "1,234", "4,321.5"))
        assertEquals("4321", editAmount("1234", "1,234", "4 321"))
    }

    @Test
    fun `a paste that is not a number leaves the entry alone`() {
        assertEquals("1234", editAmount("1234", "1,234", "abc"))
    }

    @Test
    fun `every raw entry round-trips through display and back`() {
        for (raw in listOf("0", "7", "1234", "1000000", "0.5", "1234.56", "999999999999")) {
            val display = groupForEditing(raw)
            // Re-typing the last character reproduces the same entry.
            val without = raw.dropLast(1)
            val rebuilt = editAmount(without, groupForEditing(without), display)
            assertEquals(raw, rebuilt)
        }
    }
}
