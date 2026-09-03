package com.yungmoolah.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.domain.DIVIDE
import com.yungmoolah.converter.domain.MINUS
import com.yungmoolah.converter.domain.TIMES
import com.yungmoolah.converter.ui.theme.AmountTextStyle
import com.yungmoolah.converter.ui.theme.MoolahShapes

/** The arithmetic keys, in the order they appear. */
private val OPERATOR_KEYS = listOf('(', ')', DIVIDE, TIMES, MINUS, '+')

/**
 * The keys the number pad does not have, sat above it while a row is being edited.
 *
 * The system's decimal keyboard carries no operators, so a sum has to be typed from
 * somewhere. It also gives the amount a clear key: holding the keyboard's own delete
 * is detected where it can be (see `ConverterViewModel.registerDeletion`), but that
 * is a guess about a key the app does not own, and clearing an amount should not
 * depend on a guess.
 */
@Composable
fun OperatorBar(
    onKey: (Char) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                BarKey(
                    label = "C",
                    description = "Clear amount",
                    background = colors.tertiaryContainer,
                    foreground = colors.onTertiaryContainer,
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                )
                for (key in OPERATOR_KEYS) {
                    BarKey(
                        label = key.toString(),
                        description = describe(key),
                        background = colors.surfaceContainerHigh,
                        foreground = colors.onSurface,
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarKey(
    label: String,
    description: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .clip(MoolahShapes.Chip)
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(
            text = label,
            style = AmountTextStyle.copy(fontSize = 18.sp, letterSpacing = 0.sp),
            color = foreground,
        )
    }
}

private fun describe(key: Char): String = when (key) {
    '(' -> "Open bracket"
    ')' -> "Close bracket"
    DIVIDE -> "Divide"
    TIMES -> "Multiply"
    MINUS -> "Subtract"
    '+' -> "Add"
    else -> key.toString()
}
