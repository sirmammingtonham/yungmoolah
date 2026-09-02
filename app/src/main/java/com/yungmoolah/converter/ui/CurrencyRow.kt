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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
import com.yungmoolah.converter.ui.theme.MoolahShapes
import com.yungmoolah.converter.ui.theme.RateLabelStyle

/** Width of the clear-button column, reserved on every row so amounts stay aligned. */
private val ClearSlotWidth = 32.dp

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
    onClear: () -> Unit,
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
    // Inactive rows are flat fills; only the row being edited is outlined, so the
    // accent marks one thing instead of drawing a grid of boxes.
    val borderColor by animateColorAsState(
        targetValue = if (row.isActive) colors.primary else Color.Transparent,
        label = "rowBorder",
    )

    Surface(
        color = containerColor,
        shape = MoolahShapes.Card,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = MoolahShapes.Card),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            // The identity block doubles as the row's gesture target: tapping it moves
            // focus to the amount, long-pressing promotes the row to the top.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(140.dp)
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
                Text(text = row.info.flag, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.code,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSurface,
                        )
                        if (row.info.symbol != row.code) {
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = row.info.symbol,
                                style = MaterialTheme.typography.labelSmall,
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
                                        color = colors.onSurfaceVariant.copy(alpha = 0.35f),
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
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = RateLabelStyle,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            // Reserved on every row, filled only on the one being edited, so the
            // amounts stay in one column instead of shifting as focus moves.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.width(ClearSlotWidth),
            ) {
                if (row.isActive && row.amountText.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear amount",
                            tint = colors.onSecondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Steps the amount down a size or two so long currencies still fit on one line. */
private fun amountFontSize(length: Int) = when {
    length <= 9 -> 27.sp
    length <= 12 -> 22.sp
    length <= 15 -> 19.sp
    else -> 16.sp
}

/** Revealed behind a row as it is swiped away. */
@Composable
fun RowDismissBackground(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MoolahShapes.Card,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 22.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
