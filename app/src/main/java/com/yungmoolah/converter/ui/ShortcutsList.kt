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
import com.yungmoolah.converter.domain.formatLadderAmount
import com.yungmoolah.converter.domain.formatWhole
import com.yungmoolah.converter.ui.theme.MoolahShapes
import com.yungmoolah.converter.ui.theme.RateLabelStyle

/**
 * How to do each conversion without the app.
 *
 * One card per pinned currency, paired with the home currency both ways round, and
 * a ladder of round amounts for each direction.
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
        item(key = "caption") {
            // Says where the home currency comes from, so the tab is not a black box.
            Text(
                text = "To and from ${state.homeCode}, the top row on Convert. " +
                    "Drag a row up there to change it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        // Keyed on the foreign currency: the home one is the same on every card.
        items(state.shortcuts, key = { it.foreign.code }) { card -> ShortcutCard(card) }
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
                Text(text = card.foreign.flag, fontSize = 17.sp)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "${card.foreign.code} ⇄ ${card.home.code}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.width(7.dp))
                Text(text = card.home.flag, fontSize = 17.sp)
            }

            Direction(card.toHome)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.outlineVariant)
            Direction(card.toForeign)
        }
    }
}

@Composable
private fun Direction(direction: ShortcutDirectionUi) {
    val colors = MaterialTheme.colorScheme
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${direction.from.code} → ${direction.to.code}",
            style = RateLabelStyle,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "within ${accuracy(direction.shortcut.errorPercent)}",
            style = RateLabelStyle,
            color = colors.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(5.dp))
    // The recipe is the point of the card, so it gets the emphasis rather than the
    // multiplier it works out to — nobody multiplies by 0.0068.
    Text(
        text = direction.shortcut.steps.joinToString(", then "),
        style = MaterialTheme.typography.headlineSmall,
        color = colors.primary,
    )
    Spacer(Modifier.height(9.dp))
    Ladder(direction)
}

@Composable
private fun Ladder(direction: ShortcutDirectionUi) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (rung in direction.ladder) {
            Row {
                Text(
                    text = "${direction.from.symbol}${formatWhole(rung.fromAmount)}",
                    style = RateLabelStyle,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "→",
                    style = RateLabelStyle,
                    color = colors.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "${direction.to.symbol}${formatLadderAmount(rung.toAmount, direction.to.code)}",
                    style = RateLabelStyle,
                    color = colors.onSurface,
                )
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
