package com.yungmoolah.converter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yungmoolah.converter.data.ALL_CURRENCIES

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreen(
    state: ConverterUiState,
    onAmountChanged: (String, String) -> Unit,
    onRowFocused: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMoveToTop: (String) -> Unit,
    onAdd: (String) -> Unit,
    onUndoRemove: () -> Unit,
    onRefresh: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }

    state.transientMessage?.let { message ->
        LaunchedEffect(message) {
            val isUndoable = message.startsWith("Removed ")
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isUndoable) "Undo" else null,
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
                .imePadding(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(
                        state = state,
                        onRefresh = onRefresh,
                    )
                }

                items(state.rows, key = { it.code }) { row ->
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
                            onLongPress = { onMoveToTop(row.code) },
                        )
                    }
                }

                item(key = "add") {
                    AddCurrencyTile(
                        enabled = state.rows.size < ALL_CURRENCIES.size,
                        onClick = { showPicker = true },
                    )
                }

                item(key = "footer") {
                    Text(
                        text = "Rates by exchangerate-api.com · swipe a row to remove · " +
                            "long-press to move it to the top",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
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

@Composable
private fun Header(state: ConverterUiState, onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = "YungMoolah",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        StatusChip(state = state, onRefresh = onRefresh)
        AnimatedVisibility(visible = state.rows.isNotEmpty() && state.activeCode.isNotEmpty()) {
            Text(
                text = "Editing ${state.activeCode} — every other row follows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun StatusChip(state: ConverterUiState, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val noRatesYet = state.ratesUpdatedAtMillis == null
    val label = when {
        state.isRefreshing -> "Updating rates…"
        noRatesYet -> "No rates yet — pull down to download"
        state.isOffline -> "Offline · rates from ${relativeTimeLabel(state.ratesUpdatedAtMillis)}"
        else -> "Rates updated ${relativeTimeLabel(state.ratesUpdatedAtMillis)}"
    }
    val showWarning = state.isOffline && !state.isRefreshing

    Surface(
        color = if (showWarning) colors.tertiaryContainer else colors.surfaceContainerHigh,
        shape = RoundedCornerShape(50),
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onRefresh),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        ) {
            when {
                state.isRefreshing -> CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.primary,
                    modifier = Modifier.size(14.dp),
                )
                showWarning -> Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = colors.onTertiaryContainer,
                    modifier = Modifier.size(15.dp),
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (showWarning) colors.onTertiaryContainer else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddCurrencyTile(enabled: Boolean, onClick: () -> Unit) {
    if (!enabled) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
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
