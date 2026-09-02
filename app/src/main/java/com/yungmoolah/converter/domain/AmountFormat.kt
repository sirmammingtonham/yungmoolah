package com.yungmoolah.converter.domain

import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Longest amount we let someone type, so a held-down key can't blow up layout. */
private const val MAX_INTEGER_DIGITS = 12
private const val MAX_DECIMAL_DIGITS = 6

private val symbols = DecimalFormatSymbols(Locale.US)

/**
 * Keeps [proposed] only if it is still a well-formed amount, otherwise falls back
 * to [current].
 *
 * Accepts a comma as a decimal separator (many keyboards offer only one of the
 * two) and normalises it to a dot, so the field stays usable on any locale's
 * keypad without a locale-aware parser.
 */
fun sanitizeAmountInput(proposed: String, current: String): String {
    if (proposed.isEmpty()) return ""
    val normalized = proposed.replace(',', '.')
    if (normalized.any { it != '.' && !it.isDigit() }) return current
    if (normalized.count { it == '.' } > 1) return current

    val dot = normalized.indexOf('.')
    val integerPart = if (dot >= 0) normalized.substring(0, dot) else normalized
    val decimalPart = if (dot >= 0) normalized.substring(dot + 1) else ""
    if (integerPart.length > MAX_INTEGER_DIGITS) return current
    if (decimalPart.length > MAX_DECIMAL_DIGITS) return current

    // Collapse a leading run of zeros ("007" -> "7") but keep "0" and "0.x" typable.
    val trimmedInteger = integerPart.trimStart('0').ifEmpty { if (integerPart.isEmpty()) "" else "0" }
    return when {
        dot < 0 -> trimmedInteger
        else -> "$trimmedInteger.$decimalPart"
    }
}

/** Parses a sanitized field value; a partial entry like "" or "." counts as zero. */
fun parseAmount(text: String): Double = text.toDoubleOrNull() ?: 0.0

/**
 * Formats [value] for display in a currency's row.
 *
 * Uses the currency's own ISO fraction digits, so dollars show cents and yen show
 * none. A value too small to survive that rounding — 0.004 USD would render as a
 * misleading "0.00" — is instead shown with just enough decimals to carry two
 * significant digits.
 */
fun formatAmount(value: Double, code: String): String {
    if (!value.isFinite()) return "—"
    val isoDigits = CURRENCY_BY_CODE[code]?.fractionDigits ?: 2
    val atIsoDigits = round(value, isoDigits)
    if (value == 0.0 || atIsoDigits.signum() != 0) {
        return grouping(isoDigits).format(atIsoDigits)
    }

    // Too small for this currency's usual precision: widen until a digit survives,
    // take one more for a second significant figure, then drop padding zeros.
    val firstVisible = (isoDigits + 1..MAX_DECIMAL_DIGITS)
        .firstOrNull { round(value, it).signum() != 0 }
        ?: return grouping(isoDigits).format(atIsoDigits)
    val decimals = minOf(firstVisible + 1, MAX_DECIMAL_DIGITS)
    return grouping(decimals).format(round(value, decimals)).trimEnd('0').trimEnd('.')
}

/**
 * Formats the amount the way it should appear when a row gains focus: the plain
 * value with no grouping separators, ready to be edited as text.
 */
fun formatForEditing(value: Double, code: String): String {
    if (!value.isFinite() || value == 0.0) return ""
    val formatted = formatAmount(value, code).replace(",", "")
    // Only a fractional value has trailing zeros worth dropping; trimming an
    // integer like "100" would turn it into "1".
    if (!formatted.contains('.')) return formatted
    return formatted.trimEnd('0').trimEnd('.').ifEmpty { "" }
}

/**
 * Renders a unit rate for the "1 USD = 0.9234" line.
 *
 * Rates need different precision than wallet amounts: a large rate only needs
 * cents, while a tiny one is nothing but decimals.
 */
fun formatRate(rate: Double): String {
    if (!rate.isFinite() || rate <= 0.0) return "—"
    val decimals = when {
        rate >= 100 -> 2
        rate >= 0.01 -> 4
        else -> MAX_DECIMAL_DIGITS
    }
    return grouping(decimals).format(round(rate, decimals))
}

private fun round(value: Double, decimals: Int): BigDecimal =
    BigDecimal(value).setScale(decimals, RoundingMode.HALF_UP)

private fun grouping(decimals: Int): DecimalFormat =
    DecimalFormat("#,##0", symbols).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }
