package com.yungmoolah.converter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yungmoolah.converter.data.ALL_CURRENCIES
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.data.CurrencyInfo
import com.yungmoolah.converter.data.POPULAR_CODES
import com.yungmoolah.converter.ui.theme.MoolahShapes

/**
 * Currency picker. Opens on the common currencies and narrows as you type,
 * matching either the code or the name so "yen" and "JPY" both work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCurrencySheet(
    pinned: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MoolahShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        CurrencyPickerContent(pinned = pinned, onPick = onPick)
    }
}

/**
 * The picker's body, separate from the sheet that hosts it so it can be rendered
 * and driven on its own.
 */
@Composable
fun CurrencyPickerContent(
    pinned: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { searchCurrencies(query) }

    Column(modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Add a currency",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${ALL_CURRENCIES.size} currencies, updated daily",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search code or name") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = MoolahShapes.Field,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .semantics { contentDescription = "Search currencies" },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(results, key = { it.code }) { info ->
                val alreadyPinned = pinned.contains(info.code)
                CurrencyPickerRow(
                    info = info,
                    alreadyPinned = alreadyPinned,
                    onClick = { if (!alreadyPinned) onPick(info.code) },
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        text = "No currency matches \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyPickerRow(
    info: CurrencyInfo,
    alreadyPinned: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !alreadyPinned, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(text = info.flag, fontSize = 24.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = info.code,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (alreadyPinned) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (alreadyPinned) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Already pinned",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Ranks matches so an exact code lands first; with no query, the common
 * currencies lead and the rest follow alphabetically.
 */
fun searchCurrencies(query: String): List<CurrencyInfo> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        val popular = POPULAR_CODES.mapNotNull { CURRENCY_BY_CODE[it] }
        return popular + ALL_CURRENCIES.filterNot { POPULAR_CODES.contains(it.code) }
    }
    val needle = trimmed.uppercase()
    return ALL_CURRENCIES
        .filter { it.code.contains(needle) || it.name.uppercase().contains(needle) }
        .sortedBy { info ->
            when {
                info.code == needle -> 0
                info.code.startsWith(needle) -> 1
                info.name.uppercase().startsWith(needle) -> 2
                else -> 3
            }
        }
}
