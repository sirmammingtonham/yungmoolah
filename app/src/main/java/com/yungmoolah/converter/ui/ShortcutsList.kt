package com.yungmoolah.converter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.domain.formatAmount
import com.yungmoolah.converter.domain.formatWhole
import com.yungmoolah.converter.ui.theme.MoolahShapes
import com.yungmoolah.converter.ui.theme.RateLabelStyle

/**
 * How to do each conversion without the app: an approximation you can hold in your
 * head, how far off it is, and a ladder of round amounts to recognise on sight.
 */
@Composable
fun ShortcutsList(state: ConverterUiState, modifier: Modifier = Modifier) {
    if (state.shortcuts.isEmpty()) {
        EmptyShortcuts(hasRates = state.ratesUpdatedAtMillis != null, modifier = modifier)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // Keyed on the foreign currency: the destination is the home one, which is
        // the same for every card.
        items(state.shortcuts, key = { it.from.code }) { card -> ShortcutCard(card) }
    }
}

@Composable
private fun ShortcutCard(card: ShortcutCardUi) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = colors.surfaceContainer,
        shape = MoolahShapes.Card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = card.from.flag, fontSize = 17.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${card.from.code} → ${card.to.code}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "within ${accuracy(card.shortcut.errorPercent)}",
                    style = RateLabelStyle,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            // The recipe is the point of the card, so it gets the emphasis rather
            // than the multiplier it works out to — nobody multiplies by 0.0068.
            Text(
                text = card.shortcut.steps.joinToString(", then "),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.primary,
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Ladder(card)
        }
    }
}

@Composable
private fun Ladder(card: ShortcutCardUi) {
    val colors = MaterialTheme.colorScheme
    // Two columns of three, which fits the common ladder without scrolling.
    val rows = card.ladder.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for (pair in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (rung in pair) {
                    Row(modifier = Modifier.weight(1f)) {
                        Text(
                            // Always a round number, so decimals would be noise.
                            text = formatWhole(rung.fromAmount),
                            style = RateLabelStyle,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "→",
                            style = RateLabelStyle,
                            color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = formatAmount(rung.toAmount, card.to.code),
                            style = RateLabelStyle,
                            color = colors.onSurface,
                        )
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** One decimal, so a 1.6% recipe is not rounded up into looking like a 2% one. */
private fun accuracy(errorPercent: Double): String = "%.1f%%".format(errorPercent)

@Composable
private fun EmptyShortcuts(hasRates: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(40.dp),
    ) {
        Text(
            text = if (hasRates) {
                "Pin a second currency and its shortcut will show up here."
            } else {
                "Shortcuts appear once the rates have been downloaded."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
