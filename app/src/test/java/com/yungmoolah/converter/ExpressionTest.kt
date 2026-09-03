package com.yungmoolah.converter

import com.yungmoolah.converter.domain.evaluateEntry
import com.yungmoolah.converter.domain.evaluateExpression
import com.yungmoolah.converter.domain.groupForEditing
import com.yungmoolah.converter.domain.isExpression
import com.yungmoolah.converter.domain.sanitizeExpressionInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {

    @Test
    fun `arithmetic is told apart from a plain amount`() {
        assertFalse(isExpression("1234.5"))
        assertTrue(isExpression("12+3"))
        assertTrue(isExpression("12×3"))
        assertTrue(isExpression("(12)"))
    }

    // --- evaluation -----------------------------------------------------------

    @Test
    fun `the four operations work`() {
        assertEquals(15.0, evaluateExpression("12+3")!!, 1e-9)
        assertEquals(9.0, evaluateExpression("12−3")!!, 1e-9)
        assertEquals(36.0, evaluateExpression("12×3")!!, 1e-9)
        assertEquals(4.0, evaluateExpression("12÷3")!!, 1e-9)
    }

    @Test
    fun `asterisk and slash are accepted alongside the printed symbols`() {
        assertEquals(36.0, evaluateExpression("12*3")!!, 1e-9)
        assertEquals(4.0, evaluateExpression("12/3")!!, 1e-9)
        assertEquals(9.0, evaluateExpression("12-3")!!, 1e-9)
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals(14.0, evaluateExpression("2+3×4")!!, 1e-9)
        assertEquals(20.0, evaluateExpression("(2+3)×4")!!, 1e-9)
    }

    @Test
    fun `brackets nest`() {
        assertEquals(45.0, evaluateExpression("((2+1)×(4+1))×3")!!, 1e-9)
    }

    @Test
    fun `a leading minus negates`() {
        assertEquals(-5.0, evaluateExpression("−5")!!, 1e-9)
        assertEquals(-1.0, evaluateExpression("4+(−5)")!!, 1e-9)
    }

    @Test
    fun `decimals and a real bill work`() {
        assertEquals(73.5, evaluateExpression("24.50×3")!!, 1e-9)
        assertEquals(23.75, evaluateExpression("95÷4")!!, 1e-9)
        assertEquals(28.75, evaluateExpression("25×1.15")!!, 1e-9)
    }

    @Test
    fun `an incomplete or malformed expression does not evaluate`() {
        assertNull(evaluateExpression("12+"))
        assertNull(evaluateExpression("(12"))
        assertNull(evaluateExpression("12)"))
        assertNull(evaluateExpression(""))
        assertNull(evaluateExpression("+"))
    }

    @Test
    fun `dividing by zero yields nothing rather than infinity`() {
        assertNull(evaluateExpression("12÷0"))
    }

    // --- evaluating what is on screen mid-typing ------------------------------

    @Test
    fun `a half-typed expression is worth its longest complete part`() {
        assertEquals(120.0, evaluateEntry("120×")!!, 1e-9)
        assertEquals(120.0, evaluateEntry("120×(")!!, 1e-9)
        assertEquals(15.0, evaluateEntry("12+3×")!!, 1e-9)
        assertEquals(12.0, evaluateEntry("12.")!!, 1e-9)
    }

    @Test
    fun `an entry with nothing to evaluate is worth nothing`() {
        assertNull(evaluateEntry(""))
        assertNull(evaluateEntry("   "))
        assertNull(evaluateEntry("+"))
    }

    // --- what may be typed ----------------------------------------------------

    @Test
    fun `an operator cannot follow another operator`() {
        assertEquals("12+", sanitizeExpressionInput("12+×", "12+"))
        assertEquals("12", sanitizeExpressionInput("12++", "12"))
    }

    @Test
    fun `an expression cannot open with an operator other than minus`() {
        assertEquals("", sanitizeExpressionInput("×", ""))
        assertEquals("−", sanitizeExpressionInput("−", ""))
    }

    @Test
    fun `a closing bracket needs an opening one`() {
        assertEquals("12", sanitizeExpressionInput("12)", "12"))
        assertEquals("(12)", sanitizeExpressionInput("(12)", "(12"))
    }

    @Test
    fun `a bracket cannot close on nothing`() {
        assertEquals("(", sanitizeExpressionInput("()", "("))
    }

    @Test
    fun `a number cannot be followed straight by a bracket`() {
        // "5(" is a typo rather than implied multiplication.
        assertEquals("5", sanitizeExpressionInput("5(", "5"))
        assertEquals("5×", sanitizeExpressionInput("5×", "5"))
    }

    @Test
    fun `each number keeps at most one decimal point`() {
        assertEquals("1.5+2", sanitizeExpressionInput("1.5+2", "1.5+"))
        assertEquals("1.5+2.5", sanitizeExpressionInput("1.5+2.5", "1.5+2."))
        assertEquals("1.5+2.5", sanitizeExpressionInput("1.5+2.5.", "1.5+2.5"))
    }

    @Test
    fun `letters and other junk are refused`() {
        assertEquals("12+3", sanitizeExpressionInput("12+3a", "12+3"))
        assertEquals("12+3", sanitizeExpressionInput("12+3%", "12+3"))
    }

    @Test
    fun `a plain amount still goes through the amount rules`() {
        assertEquals("7", sanitizeExpressionInput("07", "0"))
        assertEquals("1", sanitizeExpressionInput("1234567890123", "1"))
    }

    @Test
    fun `an expression cannot run on forever`() {
        val long = "1" + "+1".repeat(30)
        assertEquals("1", sanitizeExpressionInput(long, "1"))
    }

    // --- display --------------------------------------------------------------

    @Test
    fun `each number in an expression is grouped on its own`() {
        assertEquals("1,250×3", groupForEditing("1250×3"))
        assertEquals("1,250+10,000", groupForEditing("1250+10000"))
        assertEquals("(1,250+50)÷4", groupForEditing("(1250+50)÷4"))
        assertEquals("1,250.75×3", groupForEditing("1250.75×3"))
    }

    @Test
    fun `grouping never puts a separator last, so deletion stays predictable`() {
        for (raw in listOf("1250×3", "1250+", "1000", "(1000", "1250×", "1000.")) {
            assertFalse("\"$raw\" grouped to a trailing separator", groupForEditing(raw).endsWith(","))
        }
    }
}
