package com.yungmoolah.converter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yungmoolah.converter.data.ALL_CURRENCIES
import com.yungmoolah.converter.ui.theme.MoolahShapes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onAmountChanged: (String, String) -> Unit,
    onRowFocused: (String) -> Unit,
    onClear: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: (String) -> Unit,
    onUndoRemove: () -> Unit,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }

    state.transientMessage?.let { message ->
        LaunchedEffect(message) {
            val isUndoable = message.startsWith("Removed ")
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isUndoable) "Undo" else null,
                withDismissAction = isUndoable,
                // Material defaults an action snackbar to Indefinite, which leaves
                // the "Removed …" bar parked on screen until it is tapped.
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onUndoRemove()
            onMessageShown()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                // The list shrinks around the keyboard so the row being edited
                // stays on screen instead of hiding behind it.
                .imePadding()
                // A tap that no row claimed drops the highlight and the keyboard.
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    })
                },
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count = state.rows.size, key = { state.rows[it].code }) { index ->
                    val row = state.rows[index]
                    ReorderableItem(reorderState, key = row.code) { isDragging ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onRemove(row.code)
                                    true
                                } else {
                                    false
                                }
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = { RowDismissBackground() },
                        ) {
                            CurrencyRow(
                                row = row,
                                onAmountChanged = { onAmountChanged(row.code, it) },
                                onFocused = { onRowFocused(row.code) },
                                onClear = { onClear(row.code) },
                                dragHandle = Modifier.longPressDraggableHandle(),
                                isDragging = isDragging,
                            )
                        }
                    }
                }

                item(key = "add") {
                    AddCurrencyTile(
                        enabled = state.rows.size < ALL_CURRENCIES.size,
                        onClick = { showPicker = true },
                    )
                }

                item(key = "status") {
                    StatusFooter(state = state, onRefresh = onRefresh)
                }
            }
        }
    }

    if (showPicker) {
        AddCurrencySheet(
            pinned = state.pinnedCodes,
            onPick = { code ->
                onAdd(code)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Freshness of the rates, and a tap target to refresh them.
 *
 * The link to the rates provider is required by the terms of its free tier, which
 * ask for attribution on the page the rates appear on but allow it to be discreet.
 */
@Composable
private fun StatusFooter(state: ConverterUiState, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val noRatesYet = state.ratesUpdatedAtMillis == null
    val label = when {
        state.isRefreshing -> "Updating rates…"
        noRatesYet -> "No rates yet — pull down to download"
        state.isOffline -> "Offline · rates from ${relativeTimeLabel(state.ratesUpdatedAtMillis)}"
        else -> "Rates updated ${relativeTimeLabel(state.ratesUpdatedAtMillis)}"
    }
    val showWarning = state.isOffline && !state.isRefreshing

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Surface(
            color = if (showWarning) colors.tertiaryContainer else colors.surfaceContainer,
            shape = MoolahShapes.Chip,
            modifier = Modifier
                .clip(MoolahShapes.Chip)
                .clickable(onClick = onRefresh),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
            ) {
                when {
                    state.isRefreshing -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    showWarning -> Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = colors.onTertiaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    else -> Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (showWarning) colors.onTertiaryContainer else colors.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Rates By Exchange Rate API",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun AddCurrencyTile(enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MoolahShapes.Card,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoolahShapes.Card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Add currency",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
