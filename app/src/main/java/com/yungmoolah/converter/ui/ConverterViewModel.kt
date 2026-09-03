package com.yungmoolah.converter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.data.CurrencyInfo
import com.yungmoolah.converter.data.RatesRepository
import com.yungmoolah.converter.data.RatesSnapshot
import com.yungmoolah.converter.data.RefreshResult
import com.yungmoolah.converter.domain.LadderRow
import com.yungmoolah.converter.domain.MentalShortcut
import com.yungmoolah.converter.domain.convert
import com.yungmoolah.converter.domain.editAmount
import com.yungmoolah.converter.domain.evaluateEntry
import com.yungmoolah.converter.domain.groupForEditing
import com.yungmoolah.converter.domain.isExpression
import com.yungmoolah.converter.domain.ladderFor
import com.yungmoolah.converter.domain.mentalShortcut
import com.yungmoolah.converter.domain.formatAmount
import com.yungmoolah.converter.domain.formatForEditing
import com.yungmoolah.converter.domain.formatRate
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

/** One direction of a pair: how to do it in your head, and a ladder of amounts. */
data class ShortcutDirectionUi(
    val from: CurrencyInfo,
    val to: CurrencyInfo,
    val rate: Double,
    val shortcut: MentalShortcut,
    val ladder: List<LadderRow>,
)

/**
 * One pinned currency paired with the home currency, both ways round.
 *
 * Reading a foreign price tag and working out what to hand over are different
 * sums, and people need both, so a card carries a recipe for each direction.
 */
data class ShortcutCardUi(
    val foreign: CurrencyInfo,
    val home: CurrencyInfo,
    val toHome: ShortcutDirectionUi,
    val toForeign: ShortcutDirectionUi,
)

data class ConverterUiState(
    val rows: List<CurrencyRowUi> = emptyList(),
    /** Populated only while the entry is arithmetic, e.g. "= 3,750". */
    val expressionResult: String? = null,
    val shortcuts: List<ShortcutCardUi> = emptyList(),
    /** The currency the shortcuts convert to and from: the top row on Convert. */
    val homeCode: String = "",
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

class ConverterViewModel(
    private val repository: RatesRepository,
    /** Injectable so the held-delete detection below can be tested on a fake clock. */
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val editor = MutableStateFlow(EditorState())
    private val sync = MutableStateFlow(SyncState())

    /** Backs the "undo" action after a swipe-to-remove. */
    private var lastRemoved: Pair<Int, String>? = null

    /** State for spotting a held delete key; see [registerDeletion]. */
    private var lastDeletionAt = 0L
    private var runOfFastDeletions = 0

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

        if (edited.length < raw.length && registerDeletion()) {
            clearAmount(code)
            return
        }
        editor.update { it.copy(activeCode = code, input = edited) }
        if (state.activeCode != code) persistActive(code)
    }

    /**
     * Notes a deletion and reports whether the delete key is being held down.
     *
     * There is no reliable way to observe a long press on a key the app does not
     * own. Keyboards deliver a held backspace in whichever way they like — some
     * send key events, some delete through the input connection, and the ones that
     * synthesise their own repeats report every press as a first press — so none of
     * that can be trusted. What is always visible is the *rate* of deletions:
     * auto-repeat produces a run no thumb can, whichever route it arrives by.
     *
     * The thresholds are deliberately forgiving. A short amount empties within a
     * few presses, so waiting for a long run means the field is already clear by
     * the time the run is recognised.
     */
    private fun registerDeletion(): Boolean {
        val now = nowMillis()
        runOfFastDeletions = if (now - lastDeletionAt <= FAST_DELETION_WINDOW_MS) {
            runOfFastDeletions + 1
        } else {
            1
        }
        lastDeletionAt = now
        return runOfFastDeletions >= FAST_DELETIONS_TO_CLEAR
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
        // Reset the run, or holding delete again straight after would clear at once.
        runOfFastDeletions = 0
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
        // A half-typed expression still has a value, so the other rows keep up on
        // every keystroke rather than only once the arithmetic is finished.
        val amount = evaluateEntry(editorState.input) ?: 0.0
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
                // The row being edited shows what its arithmetic comes to instead.
                isActive -> expressionResultOf(editorState.input, code)
                snapshot == null -> null
                else -> unitRate(activeCode, code, snapshot)
                    ?.let { "1 $activeCode = ${formatRate(it)}" }
            }
            CurrencyRowUi(info = info, amountText = amountText, isActive = isActive, rateLabel = rateLabel)
        }

        return ConverterUiState(
            rows = rows,
            expressionResult = expressionResultOf(editorState.input, activeCode),
            shortcuts = shortcutsFor(snapshot, pinned),
            homeCode = pinned.firstOrNull().orEmpty(),
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

    /** "= 3,750" while the entry is arithmetic; null when it is a plain amount. */
    private fun expressionResultOf(input: String, code: String): String? {
        if (!isExpression(input)) return null
        val value = evaluateEntry(input) ?: return null
        return "= ${formatAmount(value, code)}"
    }

    /**
     * Builds the shortcuts tab from the pinned list, taking the top row as home.
     *
     * Home is deliberately the first pinned currency rather than whichever row is
     * being edited: the tab would otherwise rearrange itself under the user every
     * time they touched a different amount. Dragging a row to the top is how you
     * change it, which is the same gesture that already orders the main page.
     */
    private fun shortcutsFor(
        snapshot: RatesSnapshot?,
        pinned: List<String>,
    ): List<ShortcutCardUi> {
        if (snapshot == null) return emptyList()
        val home = pinned.firstOrNull()?.let { CURRENCY_BY_CODE[it] } ?: return emptyList()

        return pinned.drop(1).mapNotNull { code ->
            val foreign = CURRENCY_BY_CODE[code] ?: return@mapNotNull null
            val toHomeRate = unitRate(foreign.code, home.code, snapshot) ?: return@mapNotNull null
            val toForeignRate = unitRate(home.code, foreign.code, snapshot) ?: return@mapNotNull null
            ShortcutCardUi(
                foreign = foreign,
                home = home,
                toHome = direction(foreign, home, toHomeRate) ?: return@mapNotNull null,
                toForeign = direction(home, foreign, toForeignRate) ?: return@mapNotNull null,
            )
        }
    }

    /**
     * Each direction is searched on its own rather than by inverting the other.
     *
     * The operations are not closed under inversion — undoing "take off 10%" is not
     * "add 10%" — so inverting a recipe would either drift or reach for arithmetic
     * nobody can do. Searching twice costs nothing and keeps both sides easy.
     */
    private fun direction(
        from: CurrencyInfo,
        to: CurrencyInfo,
        rate: Double,
    ): ShortcutDirectionUi? {
        val shortcut = mentalShortcut(rate) ?: return null
        return ShortcutDirectionUi(from, to, rate, shortcut, ladderFor(rate))
    }

    private companion object {
        /**
         * A held key repeats every 50-300ms depending on the keyboard, accelerating
         * as it goes. Three deletions inside this window is around six a second,
         * which is beyond deliberate tapping and well inside auto-repeat.
         */
        const val FAST_DELETION_WINDOW_MS = 260L
        const val FAST_DELETIONS_TO_CLEAR = 3
    }

    class Factory(private val repository: RatesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConverterViewModel(repository) as T
    }
}
