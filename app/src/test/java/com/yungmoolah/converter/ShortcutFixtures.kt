package com.yungmoolah.converter

import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.domain.ladderFor
import com.yungmoolah.converter.domain.mentalShortcut
import com.yungmoolah.converter.ui.ShortcutCardUi
import com.yungmoolah.converter.ui.ShortcutDirectionUi

/**
 * Builds a shortcuts card the way the ViewModel does, from a rate quoted the way
 * the provider quotes them.
 *
 * @param perHome units of [foreignCode] to one unit of [homeCode], e.g. 147.2 yen
 *   to the dollar.
 */
internal fun shortcutCard(
    foreignCode: String,
    perHome: Double,
    homeCode: String = "USD",
): ShortcutCardUi {
    val foreign = CURRENCY_BY_CODE.getValue(foreignCode)
    val home = CURRENCY_BY_CODE.getValue(homeCode)
    return ShortcutCardUi(
        foreign = foreign,
        home = home,
        toHome = direction(foreign, home, 1.0 / perHome),
        toForeign = direction(home, foreign, perHome),
    )
}

private fun direction(
    from: com.yungmoolah.converter.data.CurrencyInfo,
    to: com.yungmoolah.converter.data.CurrencyInfo,
    rate: Double,
) = ShortcutDirectionUi(
    from = from,
    to = to,
    rate = rate,
    shortcut = mentalShortcut(rate)!!,
    ladder = ladderFor(rate),
)
