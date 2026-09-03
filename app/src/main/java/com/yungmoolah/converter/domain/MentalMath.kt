package com.yungmoolah.converter.domain

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A recipe for doing a conversion in your head.
 *
 * @param multiplier what the recipe actually multiplies by, which is close to but
 *   not the same as the true rate
 * @param steps the operations in the order you would do them
 * @param errorPercent how far [multiplier] is from the true rate, so the reader can
 *   judge whether it is close enough for what they are buying
 */
data class MentalShortcut(
    val multiplier: Double,
    val steps: List<String>,
    val errorPercent: Double,
)

/**
 * A round amount in the foreign currency next to what it is worth at home — the
 * kind of glance you need looking at a price tag.
 */
data class LadderRow(val fromAmount: Double, val toAmount: Double)

/**
 * One operation a person can actually do without paper.
 *
 * @param effort roughly how much harder this is than doing nothing, in the same
 *   units as percentage error — so a step costing 1.5 is worth taking only if it
 *   buys more than 1.5 points of accuracy.
 */
private data class Operation(val factor: Double, val words: String, val effort: Double)

/**
 * Halving, doubling, dividing by a single digit, and shifting by a tenth. These
 * are the operations that survive being done in a shop; multiplying by something
 * like 0.83 does not, however close to the rate it lands.
 */
private val OPERATIONS: List<Operation> = listOf(
    Operation(1.0, "", 0.0),
    Operation(2.0, "double it", 0.6),
    Operation(0.5, "halve it", 0.6),
    Operation(3.0, "×3", 0.9),
    Operation(5.0, "×5", 0.9),
    Operation(1.5, "add half", 1.1),
    Operation(0.25, "quarter it", 1.1),
    Operation(4.0, "×4", 1.1),
    Operation(0.2, "divide by 5", 1.2),
    Operation(1.0 / 3.0, "divide by 3", 1.4),
    Operation(2.0 / 3.0, "take off a third", 1.5),
    Operation(0.75, "take off a quarter", 1.5),
    Operation(1.25, "add a quarter", 1.4),
    Operation(6.0, "×6", 1.6),
    Operation(1.0 / 6.0, "divide by 6", 2.0),
    Operation(8.0, "×8", 1.8),
    Operation(0.125, "halve it three times", 2.2),
    Operation(7.0, "×7", 2.2),
    Operation(1.0 / 7.0, "divide by 7", 2.6),
    Operation(1.75, "add three quarters", 2.2),
    Operation(2.5, "×5, then halve", 2.0),
    Operation(9.0, "×9", 2.4),
)

/**
 * The finishing nudge — a percentage of the running total, which is itself just a
 * decimal shift and a subtraction. This is what turns a rough multiple into
 * something within a percent or two.
 */
private val ADJUSTMENTS: List<Operation> = listOf(
    Operation(1.0, "", 0.0),
    Operation(1.1, "add 10%", 0.9),
    Operation(0.9, "take off 10%", 0.9),
    Operation(1.05, "add 5%", 1.2),
    Operation(0.95, "take off 5%", 1.2),
    Operation(1.2, "add 20%", 1.3),
    Operation(0.8, "take off 20%", 1.3),
)

/** Past this the recipe is not worth trusting, and the exact figure is better. */
private const val MAX_ERROR_PERCENT = 4.0

/** Dropping or adding zeros is the easiest move there is, but it is still a move. */
private const val SHIFT_EFFORT = 0.3

/**
 * What one more step costs, over and above how hard that step is.
 *
 * Roughly: a step has to buy more than this many percentage points of accuracy to
 * be worth taking. Without it the search happily trades a two-step recipe for a
 * three-step one that is half a percent closer, which is the wrong trade for
 * arithmetic done from memory in a shop.
 */
private const val STEP_PENALTY = 1.2

/**
 * Works out the easiest way to apply [rate] in your head.
 *
 * Searches recipes of the shape *shift the decimal, apply one easy operation, then
 * nudge by a percentage* — which is the shape mental arithmetic actually takes
 * ("drop two zeros and take off a third") — and picks whichever balances accuracy
 * against effort best.
 *
 * @return the recipe, or null if [rate] is not a usable number.
 */
fun mentalShortcut(rate: Double): MentalShortcut? {
    if (!rate.isFinite() || rate <= 0.0) return null

    val exponent = kotlin.math.floor(log10(rate)).toInt()
    // A rate is reachable at several powers of ten; each is scored and the cheapest
    // recipe wins, rather than whichever the search happened to reach first.
    val shifts = (exponent - 2..exponent + 2).toList() + 0

    var best: MentalShortcut? = null
    var bestCost = Double.MAX_VALUE

    for (shift in shifts.distinct()) {
        val scale = 10.0.pow(shift)
        val shiftWords = shiftStep(shift)
        val shiftEffort = if (shift == 0) 0.0 else SHIFT_EFFORT

        for (operation in OPERATIONS) {
            for (adjustment in ADJUSTMENTS) {
                val multiplier = scale * operation.factor * adjustment.factor
                val error = abs(multiplier - rate) / rate * 100.0
                if (error > MAX_ERROR_PERCENT) continue

                val steps = listOfNotNull(shiftWords, operation.words, adjustment.words)
                    .filter { it.isNotEmpty() }
                val cost = error + shiftEffort + operation.effort + adjustment.effort +
                    steps.size * STEP_PENALTY
                if (cost >= bestCost) continue

                bestCost = cost
                best = MentalShortcut(
                    multiplier = multiplier,
                    steps = steps.ifEmpty { listOf("keep the number") },
                    errorPercent = error,
                )
            }
        }
    }

    return best ?: divisionShortcut(rate)
}

/**
 * The last resort when nothing neat lands: divide by a whole number.
 *
 * Harder than the recipes above, but it is exact enough to be honest and is still
 * better than carrying four decimal places around.
 */
private fun divisionShortcut(rate: Double): MentalShortcut {
    return if (rate < 1.0) {
        val divisor = (1.0 / rate).roundToLong().coerceAtLeast(2L)
        val multiplier = 1.0 / divisor
        MentalShortcut(
            multiplier = multiplier,
            steps = listOf("divide by ${grouped(divisor)}"),
            errorPercent = abs(multiplier - rate) / rate * 100.0,
        )
    } else {
        val factor = rate.roundToLong().coerceAtLeast(1L)
        MentalShortcut(
            multiplier = factor.toDouble(),
            steps = listOf("×${grouped(factor)}"),
            errorPercent = abs(factor - rate) / rate * 100.0,
        )
    }
}

/** "drop 2 zeros" — the vernacular for a decimal shift, and how people say it. */
private fun shiftStep(shift: Int): String? = when {
    shift == 0 -> null
    shift == -1 -> "drop a zero"
    shift == 1 -> "add a zero"
    shift < 0 -> "drop ${-shift} zeros"
    else -> "add $shift zeros"
}

/**
 * Round amounts to convert, scaled so the converted column lands in roughly the
 * 1-to-100 range you deal with day to day: hundreds of yen, not tenths.
 *
 * Powers of ten only. They are the rungs you can scale between without thinking,
 * and both directions of a pair have to fit on one card.
 */
fun ladderAmounts(rate: Double): List<Double> {
    if (!rate.isFinite() || rate <= 0.0) return emptyList()
    // One unit of `base` is worth about one unit of the destination currency.
    val base = 10.0.pow(log10(1.0 / rate).roundToInt()).coerceIn(1.0, 1_000_000.0)
    return listOf(1.0, 10.0, 100.0).map { it * base }
}

/** The ladder for one direction of a currency pair. */
fun ladderFor(rate: Double): List<LadderRow> =
    ladderAmounts(rate).map { LadderRow(it, it * rate) }

private fun grouped(value: Long): String = java.text.DecimalFormat("#,##0").format(value)
