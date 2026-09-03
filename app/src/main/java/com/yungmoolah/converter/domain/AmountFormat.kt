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

/**
 * Adds thousands separators to a raw entry for display, leaving the decimal part
 * exactly as typed.
 *
 * The field shows this while the decimal point, trailing zeros and an empty
 * fraction all have to survive: "1234." stays "1,234." rather than collapsing to
 * "1,234", so the next keystroke still lands after the point.
 */
fun groupForEditing(raw: String): String {
    if (raw.isEmpty()) return ""
    val dot = raw.indexOf('.')
    val integerPart = if (dot >= 0) raw.substring(0, dot) else raw
    val tail = if (dot >= 0) raw.substring(dot) else ""
    if (integerPart.isEmpty()) return tail

    val grouped = StringBuilder()
    for ((i, ch) in integerPart.withIndex()) {
        if (i > 0 && (integerPart.length - i) % 3 == 0) grouped.append(',')
        grouped.append(ch)
    }
    return grouped.toString() + tail
}

/**
 * Turns an edit of the *displayed* text back into the raw entry behind it.
 *
 * The field is grouped as you type, so the text the keyboard hands back contains
 * separators the model never stores. Rather than re-parsing the whole string —
 * which cannot tell a group separator from a decimal comma — this works out what
 * the edit was: characters appended, characters deleted off the end, or anything
 * else (a paste, a select-all replacement), which falls back to reading the digits.
 *
 * The caret is pinned to the end of the field, so an edit only ever lands there.
 *
 * @param raw the current entry, without separators
 * @param oldDisplay what the field was showing, i.e. [groupForEditing] of [raw]
 * @param newDisplay what the field is showing after the edit
 */
fun editAmount(raw: String, oldDisplay: String, newDisplay: String): String {
    if (newDisplay.isEmpty()) return ""

    if (newDisplay.length > oldDisplay.length && newDisplay.startsWith(oldDisplay)) {
        var next = raw
        for (typed in newDisplay.substring(oldDisplay.length)) {
            // A keyboard that offers only a comma still means a decimal point here;
            // group separators never arrive this way, they are added on display.
            next = sanitizeAmountInput(next + if (typed == ',') '.' else typed, next)
        }
        return next
    }

    if (newDisplay.length < oldDisplay.length && oldDisplay.startsWith(newDisplay)) {
        // Grouping never puts a separator last, so every character deleted off the
        // end is one the raw entry actually holds.
        val deleted = oldDisplay.substring(newDisplay.length).count { it != ',' }
        return raw.dropLast(deleted)
    }

    val digits = newDisplay.filter { it.isDigit() || it == '.' }
    // Text that holds no number at all is not an edit anyone meant: keep the entry.
    // Clearing the field is the empty case above, and is handled there.
    if (digits.isEmpty()) return raw
    return sanitizeAmountInput(digits, raw)
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
