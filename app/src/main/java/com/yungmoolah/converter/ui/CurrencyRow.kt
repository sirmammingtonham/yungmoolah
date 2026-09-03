package com.yungmoolah.converter.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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
 *
 * @param dragHandle applied to the identity block, so a long press there starts a
 *   reorder drag while the amount keeps its own touch handling.
 */
@Composable
fun CurrencyRow(
    row: CurrencyRowUi,
    onAmountChanged: (String) -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandle: Modifier = Modifier,
    isDragging: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }

    val containerColor by animateColorAsState(
        targetValue = when {
            isDragging -> colors.surfaceContainerHigh
            row.isActive -> colors.secondaryContainer
            else -> colors.surfaceContainer
        },
        label = "rowContainer",
    )
    // Inactive rows are flat fills; only the row being edited is outlined, so the
    // accent marks one thing instead of drawing a grid of boxes.
    val borderColor by animateColorAsState(
        targetValue = if (row.isActive) colors.primary else Color.Transparent,
        label = "rowBorder",
    )
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "rowElevation")

    Surface(
        color = containerColor,
        shape = MoolahShapes.Card,
        shadowElevation = elevation,
        modifier = modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = borderColor, shape = MoolahShapes.Card)
            // Tapping anywhere on the row starts editing it, so the target is the
            // whole card rather than just the digits.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusRequester.requestFocus() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(140.dp).then(dragHandle),
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
                AmountField(
                    text = row.amountText,
                    isActive = row.isActive,
                    contentDescription = "${row.info.name} amount",
                    focusRequester = focusRequester,
                    onValueChange = onAmountChanged,
                    onFocused = onFocused,
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

/**
 * The amount entry, with the caret pinned to the end of the text.
 *
 * The field mirrors [text] locally because the caret has to be corrected even when
 * the model does not change — tapping into the middle of a number, or typing a
 * character the sanitiser rejects, both leave the model as it was. Every edit
 * writes the mirror, which guarantees a recomposition, and the [SideEffect] then
 * reconciles the mirror back to the model with the caret at the end.
 */
@Composable
private fun AmountField(
    text: String,
    isActive: Boolean,
    contentDescription: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }

    SideEffect {
        if (field.text != text || field.selection != TextRange(text.length)) {
            field = TextFieldValue(text, TextRange(text.length))
        }
    }

    BasicTextField(
        value = field,
        onValueChange = { edited ->
            field = edited.copy(selection = TextRange(edited.text.length))
            onValueChange(edited.text)
        },
        singleLine = true,
        textStyle = AmountTextStyle.copy(
            color = if (isActive) colors.onSecondaryContainer else colors.onSurface,
            textAlign = TextAlign.End,
            fontSize = amountFontSize(text.length),
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .semantics { this.contentDescription = contentDescription },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (text.isEmpty()) {
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
