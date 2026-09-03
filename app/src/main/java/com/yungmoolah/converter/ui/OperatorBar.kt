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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.domain.DIVIDE
import com.yungmoolah.converter.domain.MINUS
import com.yungmoolah.converter.domain.TIMES
import com.yungmoolah.converter.ui.theme.AmountTextStyle
import com.yungmoolah.converter.ui.theme.MoolahShapes

/** Every key on the bar, in the order they appear. */
private val KEYS = listOf('(', ')', DIVIDE, TIMES, MINUS, '+')

/**
 * The arithmetic keys, sat above the number pad.
 *
 * The system's decimal keyboard has no operators on it, so a sum has to be typed
 * from somewhere: this bar appears while a row is being edited and each key is
 * appended to the entry.
 */
@Composable
fun OperatorBar(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                for (key in KEYS) {
                    OperatorKey(key = key, onClick = { onKey(key) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OperatorKey(key: Char, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .clip(MoolahShapes.Chip)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = describe(key) },
    ) {
        Text(
            text = key.toString(),
            style = AmountTextStyle.copy(fontSize = 19.sp, letterSpacing = 0.sp),
            color = MaterialTheme.colorScheme.onSurface,
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
