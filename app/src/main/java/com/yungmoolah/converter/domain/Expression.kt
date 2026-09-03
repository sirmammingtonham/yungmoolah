package com.yungmoolah.converter.domain

/** Multiplication and division as they are written in the field. */
const val TIMES = '×'
const val DIVIDE = '÷'
const val MINUS = '−'

private const val MAX_EXPRESSION_LENGTH = 40

private val OPERATORS = setOf('+', MINUS, '-', TIMES, '*', DIVIDE, '/')

/** True for a character that can appear in an entry: a digit, a point, or arithmetic. */
fun isEntryChar(ch: Char): Boolean =
    ch.isDigit() || ch == '.' || ch == '(' || ch == ')' || ch in OPERATORS

/** True when [entry] is arithmetic rather than a plain amount. */
fun isExpression(entry: String): Boolean =
    entry.any { it in OPERATORS || it == '(' || it == ')' }

/**
 * Keeps [proposed] only if it is still a well-formed entry, otherwise falls back
 * to [current].
 *
 * A plain number is handed to [sanitizeAmountInput] unchanged, so the ordinary
 * case keeps its own rules — leading-zero collapse and per-currency digit limits.
 * Arithmetic is checked structurally instead: no two operators in a row, no
 * unbalanced closing bracket, one decimal point per number.
 */
fun sanitizeExpressionInput(proposed: String, current: String): String {
    if (proposed.isEmpty()) return ""
    if (!isExpression(proposed)) return sanitizeAmountInput(proposed, current)
    if (proposed.length > MAX_EXPRESSION_LENGTH) return current
    if (proposed.any { !it.isDigit() && it != '.' && it != '(' && it != ')' && it !in OPERATORS }) {
        return current
    }

    var depth = 0
    var previous: Char? = null
    var decimalsInToken = 0
    for (ch in proposed) {
        when {
            ch == '(' -> {
                // "5(" is a typo, not implied multiplication.
                if (previous != null && (previous.isDigit() || previous == ')')) return current
                depth++
                decimalsInToken = 0
            }
            ch == ')' -> {
                if (depth == 0) return current
                if (previous == null || previous in OPERATORS || previous == '(') return current
                depth--
                decimalsInToken = 0
            }
            ch in OPERATORS -> {
                // A leading minus, or one just inside a bracket, is a negation.
                val isNegation = (ch == MINUS || ch == '-') && (previous == null || previous == '(')
                if (!isNegation && (previous == null || previous in OPERATORS || previous == '(')) {
                    return current
                }
                decimalsInToken = 0
            }
            ch == '.' -> {
                if (++decimalsInToken > 1) return current
            }
        }
        previous = ch
    }
    return proposed
}

/**
 * Evaluates the longest leading part of [entry] that parses.
 *
 * A half-typed expression still has a value — "120 ×" is worth 120 — which is what
 * keeps the other rows updating on every keystroke rather than only once the
 * expression is finished.
 *
 * @return the value, or null when nothing in [entry] evaluates.
 */
fun evaluateEntry(entry: String): Double? {
    if (entry.isBlank()) return null
    var candidate = entry
    while (candidate.isNotEmpty()) {
        evaluateExpression(candidate)?.let { return it }
        candidate = candidate.dropLast(1)
    }
    return null
}

/**
 * Evaluates a complete arithmetic expression.
 *
 * @return the value, or null if [expr] does not parse or the result is not finite.
 */
fun evaluateExpression(expr: String): Double? {
    val parser = Parser(expr)
    val value = parser.parseSum() ?: return null
    if (!parser.atEnd()) return null
    return value.takeIf { it.isFinite() }
}

/** Recursive descent over `sum := product (('+'|'-') product)*`. */
private class Parser(private val text: String) {
    private var index = 0

    fun atEnd(): Boolean = index >= text.length

    fun parseSum(): Double? {
        var left = parseProduct() ?: return null
        while (!atEnd()) {
            val op = text[index]
            if (op != '+' && op != MINUS && op != '-') break
            index++
            val right = parseProduct() ?: return null
            left = if (op == '+') left + right else left - right
        }
        return left
    }

    private fun parseProduct(): Double? {
        var left = parseFactor() ?: return null
        while (!atEnd()) {
            val op = text[index]
            if (op != TIMES && op != '*' && op != DIVIDE && op != '/') break
            index++
            val right = parseFactor() ?: return null
            left = if (op == TIMES || op == '*') left * right else left / right
        }
        return left
    }

    private fun parseFactor(): Double? {
        if (atEnd()) return null
        val ch = text[index]
        if (ch == MINUS || ch == '-') {
            index++
            return parseFactor()?.let { -it }
        }
        if (ch == '(') {
            index++
            val inner = parseSum() ?: return null
            if (atEnd() || text[index] != ')') return null
            index++
            return inner
        }
        return parseNumber()
    }

    private fun parseNumber(): Double? {
        val start = index
        while (!atEnd() && (text[index].isDigit() || text[index] == '.')) index++
        if (index == start) return null
        return text.substring(start, index).toDoubleOrNull()
    }
}
