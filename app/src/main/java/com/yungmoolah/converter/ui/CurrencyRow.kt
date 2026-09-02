package com.yungmoolah.converter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.ui.theme.AmountTextStyle

/**
 * One pinned currency: flag and identity on the left, an editable amount on the right.
 *
 * Every row is a live text field. Typing in any of them makes it the source and
 * recomputes the others, which is the whole interaction model of the app — there
 * is no separate "from" and "to" picker.
 */
@Composable
fun CurrencyRow(
    row: CurrencyRowUi,
    onAmountChanged: (String) -> Unit,
    onFocused: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current

    val containerColor by animateColorAsState(
        targetValue = if (row.isActive) colors.secondaryContainer else colors.surfaceContainer,
        label = "rowContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (row.isActive) colors.primary else colors.outlineVariant,
        label = "rowBorder",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (row.isActive) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp),
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            // The identity block doubles as the row's gesture target: tapping it moves
            // focus to the amount, long-pressing promotes the row to the top.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(148.dp)
                    .pointerInput(row.code) {
                        detectTapGestures(
                            onTap = { focusRequester.requestFocus() },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPress()
                            },
                        )
                    },
            ) {
                Text(text = row.info.flag, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.code,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        if (row.info.symbol != row.code) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = row.info.symbol,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = row.info.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.weight(1f),
            ) {
                BasicTextField(
                    value = row.amountText,
                    onValueChange = onAmountChanged,
                    singleLine = true,
                    textStyle = AmountTextStyle.copy(
                        color = if (row.isActive) colors.onSecondaryContainer else colors.onSurface,
                        textAlign = TextAlign.End,
                        fontSize = amountFontSize(row.amountText.length),
                    ),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) onFocused() }
                        .semantics { contentDescription = "${row.info.name} amount" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (row.amountText.isEmpty()) {
                                Text(
                                    text = "0",
                                    style = AmountTextStyle.copy(
                                        color = colors.onSurfaceVariant.copy(alpha = 0.4f),
                                        textAlign = TextAlign.End,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            inner()
                        }
                    },
                )
                row.rateLabel?.let { label ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Steps the amount down a size or two so long currencies still fit on one line. */
private fun amountFontSize(length: Int) = when {
    length <= 9 -> 26.sp
    length <= 12 -> 22.sp
    length <= 15 -> 19.sp
    else -> 16.sp
}

/** Revealed behind a row as it is swiped away. */
@Composable
fun RowDismissBackground(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 28.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
