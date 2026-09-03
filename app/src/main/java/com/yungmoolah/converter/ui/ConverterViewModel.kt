package com.yungmoolah.converter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.data.CurrencyInfo
import com.yungmoolah.converter.data.RatesRepository
import com.yungmoolah.converter.data.RatesSnapshot
import com.yungmoolah.converter.data.RefreshResult
import com.yungmoolah.converter.domain.convert
import com.yungmoolah.converter.domain.editAmount
import com.yungmoolah.converter.domain.groupForEditing
import com.yungmoolah.converter.domain.formatAmount
import com.yungmoolah.converter.domain.formatForEditing
import com.yungmoolah.converter.domain.formatRate
import com.yungmoolah.converter.domain.parseAmount
import com.yungmoolah.converter.domain.unitRate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One pinned currency, ready to render. */
data class CurrencyRowUi(
    val info: CurrencyInfo,
    /** Text for the amount field: the raw entry on the active row, a conversion elsewhere. */
    val amountText: String,
    val isActive: Boolean,
    /** e.g. "1 USD = 0.9234" — null on the active row and when rates are unavailable. */
    val rateLabel: String?,
) {
    val code: String get() = info.code
}

data class ConverterUiState(
    val rows: List<CurrencyRowUi> = emptyList(),
    val activeCode: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** True when the newest attempt to reach the provider failed. */
    val isOffline: Boolean = false,
    /** When the provider last recalculated the rates we are showing. */
    val ratesUpdatedAtMillis: Long? = null,
    val transientMessage: String? = null,
    val pinnedCodes: List<String> = emptyList(),
)

/** UI-owned editing state; deliberately not persisted beyond the active currency. */
private data class EditorState(
    val activeCode: String? = null,
    val input: String = "1",
)

private data class SyncState(
    val isRefreshing: Boolean = false,
    val isOffline: Boolean = false,
    val message: String? = null,
)

class ConverterViewModel(private val repository: RatesRepository) : ViewModel() {

    private val editor = MutableStateFlow(EditorState())
    private val sync = MutableStateFlow(SyncState())

    /** Backs the "undo" action after a swipe-to-remove. */
    private var lastRemoved: Pair<Int, String>? = null

    val uiState: StateFlow<ConverterUiState> = combine(
        repository.snapshot,
        repository.pinned,
        editor,
        sync,
    ) { snapshot, pinned, editorState, syncState ->
        buildState(snapshot, pinned, editorState, syncState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConverterUiState(),
    )

    init {
        viewModelScope.launch {
            // Restore the row the user was last editing before touching the network.
            repository.activeCode.first()?.let { saved ->
                editor.update { if (it.activeCode == null) it.copy(activeCode = saved) else it }
            }
            refresh(force = false)
        }
    }

    /**
     * Called on every keystroke in any row's amount field.
     *
     * [proposed] is the *displayed* text, separators and all; the raw entry behind
     * it is recovered by [editAmount].
     */
    fun onAmountChanged(code: String, proposed: String) {
        val state = uiState.value
        val raw = if (state.activeCode == code) {
            editor.value.input
        } else {
            // Typing into a row that was not active: seed from what it was showing.
            state.rows.firstOrNull { it.code == code }?.amountText?.replace(",", "").orEmpty()
        }
        val edited = editAmount(raw = raw, oldDisplay = groupForEditing(raw), newDisplay = proposed)
        editor.update { it.copy(activeCode = code, input = edited) }
        if (state.activeCode != code) persistActive(code)
    }

    /**
     * Makes [code] the row being edited.
     *
     * Its currently displayed conversion is seeded into the field as plain text so
     * editing continues from the number already on screen instead of clearing it.
     */
    fun onRowFocused(code: String) {
        val state = uiState.value
        if (state.activeCode == code) return
        val shown = state.rows.firstOrNull { it.code == code }?.amountText
        val seeded = shown
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.let { formatForEditing(it, code) }
            ?: ""
        editor.update { it.copy(activeCode = code, input = seeded) }
        persistActive(code)
    }

    /**
     * Empties the amount being edited, which blanks every row.
     *
     * [code] becomes the active row first, so the button clears the field the user
     * is looking at even if focus had moved on.
     */
    fun clearAmount(code: String) {
        editor.update { it.copy(activeCode = code, input = "") }
        if (uiState.value.activeCode != code) persistActive(code)
    }

    fun addCurrency(code: String) {
        if (!CURRENCY_BY_CODE.containsKey(code)) return
        viewModelScope.launch {
            val pinned = repository.pinned.first()
            if (pinned.contains(code)) {
                sync.update { it.copy(message = "$code is already pinned") }
                return@launch
            }
            repository.setPinned(pinned + code)
        }
    }

    fun removeCurrency(code: String) {
        viewModelScope.launch {
            val pinned = repository.pinned.first()
            if (pinned.size <= 1) {
                sync.update { it.copy(message = "Keep at least one currency") }
                return@launch
            }
            val index = pinned.indexOf(code)
            if (index < 0) return@launch
            lastRemoved = index to code
            repository.setPinned(pinned - code)
            sync.update { it.copy(message = "Removed $code") }
        }
    }

    fun undoRemove() {
        val (index, code) = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch {
            val pinned = repository.pinned.first().toMutableList()
            if (pinned.contains(code)) return@launch
            pinned.add(index.coerceIn(0, pinned.size), code)
            repository.setPinned(pinned)
        }
    }

    /**
     * Moves the row at [from] to [to], for long-press drag reordering.
     *
     * Out-of-range indices are ignored rather than clamped: the drag can pass over
     * the trailing "add currency" tile, and clamping would silently drop the row in
     * the wrong place.
     */
    fun moveCurrency(from: Int, to: Int) {
        if (from == to) return
        viewModelScope.launch {
            val pinned = repository.pinned.first()
            if (from !in pinned.indices || to !in pinned.indices) return@launch
            repository.setPinned(pinned.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun refresh(force: Boolean = true) {
        if (sync.value.isRefreshing) return
        viewModelScope.launch {
            sync.update { it.copy(isRefreshing = true) }
            val cached = repository.snapshot.first()
            val result = repository.refresh(cached = cached, force = force)
            sync.update {
                when (result) {
                    is RefreshResult.Updated -> it.copy(
                        isRefreshing = false,
                        isOffline = false,
                        message = if (force) "Rates updated" else null,
                    )
                    RefreshResult.AlreadyFresh -> it.copy(isRefreshing = false, isOffline = false)
                    is RefreshResult.Failed -> it.copy(
                        isRefreshing = false,
                        isOffline = true,
                        message = if (cached == null) result.message else null,
                    )
                }
            }
        }
    }

    fun consumeMessage() = sync.update { it.copy(message = null) }

    private fun persistActive(code: String) {
        viewModelScope.launch { repository.setActiveCode(code) }
    }

    private fun buildState(
        snapshot: RatesSnapshot?,
        pinned: List<String>,
        editorState: EditorState,
        syncState: SyncState,
    ): ConverterUiState {
        // The active row may have been unpinned since it was chosen.
        val activeCode = editorState.activeCode?.takeIf { pinned.contains(it) }
            ?: pinned.firstOrNull()
            ?: ""
        val amount = parseAmount(editorState.input)
        val blankInput = editorState.input.isBlank()

        val rows = pinned.mapNotNull { code ->
            val info = CURRENCY_BY_CODE[code] ?: return@mapNotNull null
            val isActive = code == activeCode
            val amountText = when {
                isActive -> groupForEditing(editorState.input)
                blankInput || snapshot == null -> ""
                else -> convert(amount, activeCode, code, snapshot)?.let { formatAmount(it, code) } ?: ""
            }
            val rateLabel = when {
                isActive || snapshot == null -> null
                else -> unitRate(activeCode, code, snapshot)
                    ?.let { "1 $activeCode = ${formatRate(it)}" }
            }
            CurrencyRowUi(info = info, amountText = amountText, isActive = isActive, rateLabel = rateLabel)
        }

        return ConverterUiState(
            rows = rows,
            activeCode = activeCode,
            isLoading = snapshot == null && syncState.isRefreshing,
            isRefreshing = syncState.isRefreshing,
            isOffline = syncState.isOffline || snapshot == null,
            ratesUpdatedAtMillis = snapshot?.ratesUpdatedAtMillis?.takeIf { it > 0L }
                ?: snapshot?.fetchedAtMillis,
            transientMessage = syncState.message,
            pinnedCodes = pinned,
        )
    }

    class Factory(private val repository: RatesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConverterViewModel(repository) as T
    }
}
