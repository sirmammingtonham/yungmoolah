package com.yungmoolah.converter

import com.yungmoolah.converter.domain.ladderFor
import com.yungmoolah.converter.domain.mentalShortcut
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rates here are quoted foreign-to-home, the direction the tab shows: 1 unit of the
 * foreign currency is worth this much at home.
 */
class MentalMathTest {

    @Test
    fun `a recipe is never further off than it claims`() {
        var rate = 0.00001
        while (rate < 5_000) {
            val shortcut = mentalShortcut(rate)!!
            val actual = abs(shortcut.multiplier - rate) / rate * 100.0
            assertEquals("rate $rate", actual, shortcut.errorPercent, 1e-6)
            assertTrue("rate $rate has no steps", shortcut.steps.isNotEmpty())
            rate *= 1.03
        }
    }

    @Test
    fun `every plausible rate gets a recipe`() {
        for (rate in listOf(0.00003, 0.0007, 0.0068, 0.011, 0.14, 0.73, 1.09, 1.27, 3.27)) {
            assertNotNull("no recipe for $rate", mentalShortcut(rate))
        }
    }

    // --- the recipes have to be doable in your head -----------------------------

    /**
     * Multiplying by an arbitrary decimal is not mental arithmetic, so no step is
     * ever allowed to ask for one. Every step must be a decimal shift, a small
     * whole-number multiply or divide, a simple fraction, or a percentage nudge.
     */
    @Test
    fun `no recipe asks for anything harder than a small whole number`() {
        val allowed = Regex(
            "^(drop (a|\\d+) zeros?|add (a|\\d+) zeros?|keep the number" +
                "|double it|halve it|halve it three times|quarter it" +
                "|×[2-9]|×5, then halve|divide by [3-9]" +
                "|add half|add a quarter|add three quarters" +
                "|take off a third|take off a quarter" +
                "|add \\d+%|take off \\d+%" +
                "|divide by [\\d,]+|×[\\d,]+)$"
        )
        var rate = 0.00001
        var checked = 0
        while (rate < 5_000) {
            for (step in mentalShortcut(rate)!!.steps) {
                assertTrue("rate $rate produced an unusable step: \"$step\"", allowed.matches(step))
                checked++
            }
            rate *= 1.017
        }
        assertTrue("expected to have checked plenty of steps, got $checked", checked > 500)
    }

    @Test
    fun `a recipe is at most three steps`() {
        var rate = 0.00001
        while (rate < 5_000) {
            val steps = mentalShortcut(rate)!!.steps
            assertTrue("rate $rate needs ${steps.size} steps: $steps", steps.size <= 3)
            rate *= 1.017
        }
    }

    // --- the currencies people actually pin -------------------------------------

    @Test
    fun `yen is drop two zeros and take off a third`() {
        // ¥1 = $0.006793, so ¥1,500 -> 15 -> $10.
        assertEquals(
            listOf("drop 2 zeros", "take off a third"),
            mentalShortcut(1.0 / 147.2)!!.steps,
        )
    }

    @Test
    fun `the euro and the pound are a percentage nudge`() {
        assertEquals(listOf("add 10%"), mentalShortcut(1.0 / 0.9142)!!.steps)
        assertEquals(listOf("add a quarter"), mentalShortcut(1.0 / 0.7891)!!.steps)
    }

    @Test
    fun `the won is drop three zeros and take off a quarter`() {
        // ₩1 = $0.000763, so ₩10,000 -> 10 -> $7.50.
        assertEquals(
            listOf("drop 3 zeros", "take off a quarter"),
            mentalShortcut(1.0 / 1310.0)!!.steps,
        )
    }

    @Test
    fun `a rate of exactly one asks for nothing`() {
        assertEquals(listOf("keep the number"), mentalShortcut(1.0)!!.steps)
        assertEquals(0.0, mentalShortcut(1.0)!!.errorPercent, 1e-9)
    }

    @Test
    fun `real rates stay within a couple of percent`() {
        val perUsd = listOf(
            0.9142, 0.7891, 147.2, 88.4, 1.375, 1.52, 7.12, 1310.0, 0.88, 17.9,
            5.42, 3.67, 35.8, 1.34, 4.05, 57.5, 26300.0, 16400.0, 41.0, 17.6,
            9.4, 10.1, 21.0, 355.0, 0.306, 3900.0, 1520.0, 30.5,
        )
        val errors = perUsd.map { mentalShortcut(1.0 / it)!!.errorPercent }
        assertTrue("worst case was ${errors.max()}%", errors.max() <= 4.0)
        assertTrue("average was ${errors.average()}%", errors.average() <= 2.0)
    }

    @Test
    fun `a nonsensical rate offers nothing`() {
        assertNull(mentalShortcut(0.0))
        assertNull(mentalShortcut(-3.0))
        assertNull(mentalShortcut(Double.NaN))
    }

    // --- the ladder -------------------------------------------------------------

    @Test
    fun `the ladder is denominated so the converted column is money you recognise`() {
        // ¥1 is worth well under a cent, so the rungs start in the hundreds.
        val yen = ladderFor(1.0 / 147.2)
        assertEquals(100.0, yen.first().fromAmount, 1e-9)
        assertEquals(0.679, yen.first().toAmount, 0.001)
        assertEquals(10_000.0, yen.last().fromAmount, 1e-9)

        // A euro is worth about a dollar, so single units are the right rungs.
        val euro = ladderFor(1.0 / 0.9142)
        assertEquals(1.0, euro.first().fromAmount, 1e-9)
        assertEquals(100.0, euro.last().fromAmount, 1e-9)
    }

    @Test
    fun `a very low value unit still gets a sensible ladder`() {
        val dong = ladderFor(1.0 / 26_300.0)
        assertEquals(10_000.0, dong.first().fromAmount, 1e-9)
        assertTrue("first rung converts to ${dong.first().toAmount}", dong.first().toAmount in 0.2..2.0)
    }

    @Test
    fun `a ladder is not offered for a nonsensical rate`() {
        assertTrue(ladderFor(0.0).isEmpty())
    }
}
