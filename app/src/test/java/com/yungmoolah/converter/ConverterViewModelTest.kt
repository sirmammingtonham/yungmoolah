package com.yungmoolah.converter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.yungmoolah.converter.data.RatesApi
import com.yungmoolah.converter.data.RatesRepository
import com.yungmoolah.converter.data.RatesStore
import com.yungmoolah.converter.ui.ConverterUiState
import com.yungmoolah.converter.ui.ConverterViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real DataStore, repository and ViewModel against a stub server, so
 * these cover the wiring the pure unit tests deliberately skip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// The real Application arms WorkManager, which these tests neither need nor drive.
@Config(sdk = [34], application = android.app.Application::class)
class ConverterViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val storeScope = CoroutineScope(dispatcher + SupervisorJob())
    private lateinit var server: MockWebServer
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: RatesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        server = MockWebServer()
        server.start()
        // A DataStore of its own, in a per-test directory, so nothing leaks between cases.
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { tempFolder.newFile("moolah.preferences_pb").also { it.delete() } },
        )
        repository = RatesRepository(
            store = RatesStore(dataStore),
            api = RatesApi(
                baseUrl = server.url("/v6/latest/").toString(),
                // Keeps the fetch on the test scheduler instead of a real IO thread.
                ioDispatcher = dispatcher,
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `editing one amount recomputes every other pinned row`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        // Default pins are USD, EUR, GBP, JPY, INR with USD active and "1" seeded.
        assertEquals(listOf("USD", "EUR", "GBP", "JPY", "INR"), state().rows.map { it.code })
        assertEquals("USD", state().activeCode)

        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()

        val rows = state().rows.associateBy { it.code }
        assertEquals("100", rows.getValue("USD").amountText)
        assertEquals("91.42", rows.getValue("EUR").amountText)
        assertEquals("78.91", rows.getValue("GBP").amountText)
        assertEquals("14,720", rows.getValue("JPY").amountText)
        assertTrue(rows.getValue("USD").isActive)
        assertFalse(rows.getValue("EUR").isActive)
    }

    @Test
    fun `editing a different row makes it the source and converts the other way`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("JPY", "1000")
        advanceUntilIdle()

        val rows = state().rows.associateBy { it.code }
        assertEquals("JPY", state().activeCode)
        // The row being edited is grouped as it is typed.
        assertEquals("1,000", rows.getValue("JPY").amountText)
        // 1000 JPY / 147.2 = 6.79 USD
        assertEquals("6.79", rows.getValue("USD").amountText)
        assertEquals("6.21", rows.getValue("EUR").amountText)
    }

    @Test
    fun `focusing a row seeds it with the amount already displayed`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()
        viewModel.onRowFocused("EUR")
        advanceUntilIdle()

        // The 91.42 that EUR was showing becomes its editable text, not a blank field.
        assertEquals("EUR", state().activeCode)
        assertEquals("91.42", state().rows.first { it.code == "EUR" }.amountText)
        // USD is now a derived row, so it picks up the currency's own precision.
        assertEquals("100.00", state().rows.first { it.code == "USD" }.amountText)
    }

    @Test
    fun `clearing the active field blanks the others rather than showing stale numbers`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setBody(ratesBody()))
            val state = collectState()

            viewModel.onAmountChanged("USD", "")
            advanceUntilIdle()

            assertTrue(state().rows.all { it.amountText.isEmpty() })
        }

    @Test
    fun `clearing empties every row and leaves the row ready to type into`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()
        assertEquals("91.42", state().rows.first { it.code == "EUR" }.amountText)

        viewModel.clearAmount("USD")
        advanceUntilIdle()

        assertTrue(state().rows.all { it.amountText.isEmpty() })
        assertEquals("USD", state().activeCode)

        // Typing straight after a clear works, with no leftover digits.
        viewModel.onAmountChanged("USD", "7")
        advanceUntilIdle()
        assertEquals("7", state().rows.first { it.code == "USD" }.amountText)
        assertEquals("6.40", state().rows.first { it.code == "EUR" }.amountText)
    }

    @Test
    fun `clearing a row that was not active takes over the editing`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()

        viewModel.clearAmount("JPY")
        advanceUntilIdle()

        assertEquals("JPY", state().activeCode)
        assertTrue(state().rows.all { it.amountText.isEmpty() })
    }

    @Test
    fun `the row being edited gains separators as it is typed`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        // Each keystroke appends to whatever the field is showing, separators and
        // all, exactly as the text field hands it back.
        fun typeInto(code: String, keys: String) {
            for (key in keys) {
                val shown = state().rows.first { it.code == code }.amountText
                viewModel.onAmountChanged(code, shown + key)
                advanceUntilIdle()
            }
        }

        viewModel.clearAmount("USD")
        advanceUntilIdle()
        typeInto("USD", "1234567")

        assertEquals("1,234,567", state().rows.first { it.code == "USD" }.amountText)
        // The conversion uses the number, not the text on screen.
        assertEquals("1,128,641.15", state().rows.first { it.code == "EUR" }.amountText)
    }

    @Test
    fun `backspacing across a separator removes one digit`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "1000")
        advanceUntilIdle()
        assertEquals("1,000", state().rows.first { it.code == "USD" }.amountText)

        val shown = state().rows.first { it.code == "USD" }.amountText
        viewModel.onAmountChanged("USD", shown.dropLast(1))
        advanceUntilIdle()

        assertEquals("100", state().rows.first { it.code == "USD" }.amountText)
    }

    @Test
    fun `a sum in the field drives every other row`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "1250×3")
        advanceUntilIdle()

        assertEquals("1,250×3", state().rows.first { it.code == "USD" }.amountText)
        assertEquals("= 3,750.00", state().expressionResult)
        // 3750 × 0.9142
        assertEquals("3,428.25", state().rows.first { it.code == "EUR" }.amountText)
    }

    @Test
    fun `a half-typed sum keeps the other rows on its completed part`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "100×")
        advanceUntilIdle()

        // Worth 100 until the multiplier arrives, rather than blanking out.
        assertEquals("91.42", state().rows.first { it.code == "EUR" }.amountText)
    }

    @Test
    fun `the running total shows under the row being edited`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "20+5")
        advanceUntilIdle()
        assertEquals("= 25.00", state().rows.first { it.code == "USD" }.rateLabel)

        // A plain amount has no total to show.
        viewModel.onAmountChanged("USD", "25")
        advanceUntilIdle()
        assertEquals(null, state().rows.first { it.code == "USD" }.rateLabel)
        assertEquals(null, state().expressionResult)
    }

    @Test
    fun `operator keys append to whatever the field is showing`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        // What the operator bar does: append its key to the displayed text.
        fun press(key: Char) {
            val shown = state().rows.first { it.isActive }.amountText
            viewModel.onAmountChanged(state().activeCode, shown + key)
        }

        viewModel.onAmountChanged("USD", "1000")
        advanceUntilIdle()
        press('÷')
        advanceUntilIdle()
        viewModel.onAmountChanged("USD", state().rows.first { it.isActive }.amountText + "4")
        advanceUntilIdle()

        assertEquals("1,000÷4", state().rows.first { it.code == "USD" }.amountText)
        assertEquals("= 250.00", state().expressionResult)
    }

    @Test
    fun `clearing a sum empties the field`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "12×3")
        advanceUntilIdle()
        viewModel.clearAmount("USD")
        advanceUntilIdle()

        assertTrue(state().rows.all { it.amountText.isEmpty() })
        assertEquals(null, state().expressionResult)
    }

    // --- shortcuts tab --------------------------------------------------------

    @Test
    fun `shortcuts run from each pinned currency into the one being edited`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()

        // The direction that matters standing at a till abroad: foreign -> home.
        assertEquals(listOf("EUR", "GBP", "JPY", "INR"), state().shortcuts.map { it.from.code })
        assertTrue(state().shortcuts.all { it.to.code == "USD" })
        // 1 EUR is worth 1/0.9142 USD.
        assertEquals(1.0 / 0.9142, state().shortcuts.first().rate, 1e-9)
        assertEquals(listOf("add 10%"), state().shortcuts.first().shortcut.steps)
    }

    @Test
    fun `switching the row being edited switches what the shortcuts convert into`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setBody(ratesBody()))
            val state = collectState()

            viewModel.onRowFocused("EUR")
            advanceUntilIdle()

            assertTrue(state().shortcuts.all { it.to.code == "EUR" })
            assertTrue(state().shortcuts.none { it.from.code == "EUR" })
        }

    @Test
    fun `there are no shortcuts before any rates arrive`() = runTest(dispatcher) {
        server.shutdown()
        val state = collectState()
        advanceUntilIdle()

        assertTrue(state().shortcuts.isEmpty())
    }

    @Test
    fun `rejected keystrokes leave the amount untouched`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "12")
        advanceUntilIdle()
        viewModel.onAmountChanged("USD", "12x")
        advanceUntilIdle()

        assertEquals("12", state().rows.first { it.code == "USD" }.amountText)
    }

    @Test
    fun `each row reports its own unit rate`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()

        val rows = state().rows.associateBy { it.code }
        assertEquals("1 USD = 0.9142", rows.getValue("EUR").rateLabel)
        assertEquals("1 USD = 147.2", rows.getValue("JPY").rateLabel)
        // The row being edited is the reference, so it has no rate line of its own.
        assertEquals(null, rows.getValue("USD").rateLabel)
    }

    @Test
    fun `adding a currency pins it and it converts immediately`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("USD", "100")
        viewModel.addCurrency("CAD")
        advanceUntilIdle()

        val cad = state().rows.first { it.code == "CAD" }
        assertEquals("137.50", cad.amountText)
        assertEquals(6, state().rows.size)
    }

    @Test
    fun `removing a currency drops it and undo puts it back in place`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()

        viewModel.removeCurrency("GBP")
        advanceUntilIdle()
        assertEquals(listOf("USD", "EUR", "JPY", "INR"), state().rows.map { it.code })

        viewModel.undoRemove()
        advanceUntilIdle()
        assertEquals(listOf("USD", "EUR", "GBP", "JPY", "INR"), state().rows.map { it.code })
    }

    @Test
    fun `the last currency cannot be removed`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()

        listOf("EUR", "GBP", "JPY", "INR").forEach {
            viewModel.removeCurrency(it)
            advanceUntilIdle()
        }
        viewModel.removeCurrency("USD")
        advanceUntilIdle()

        assertEquals(listOf("USD"), state().rows.map { it.code })
        assertEquals("Keep at least one currency", state().transientMessage)
    }

    @Test
    fun `removing the active row hands editing to another one`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()

        viewModel.onAmountChanged("GBP", "50")
        advanceUntilIdle()
        assertEquals("GBP", state().activeCode)

        viewModel.removeCurrency("GBP")
        advanceUntilIdle()

        assertEquals("USD", state().activeCode)
        assertTrue(state().rows.first { it.code == "USD" }.isActive)
    }

    @Test
    fun `dragging a row to a new position reorders the list`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()

        // JPY (index 3) dragged to the top.
        viewModel.moveCurrency(3, 0)
        advanceUntilIdle()
        assertEquals(listOf("JPY", "USD", "EUR", "GBP", "INR"), state().rows.map { it.code })

        // and back down one place.
        viewModel.moveCurrency(0, 1)
        advanceUntilIdle()
        assertEquals(listOf("USD", "JPY", "EUR", "GBP", "INR"), state().rows.map { it.code })
    }

    @Test
    fun `a drag past the end of the list is ignored`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()
        val before = state().rows.map { it.code }

        // The drag can travel over the trailing "add currency" tile and the footer.
        viewModel.moveCurrency(0, 5)
        advanceUntilIdle()
        viewModel.moveCurrency(0, 99)
        advanceUntilIdle()
        viewModel.moveCurrency(-1, 0)
        advanceUntilIdle()

        assertEquals(before, state().rows.map { it.code })
    }

    @Test
    fun `a reordered list survives a relaunch`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        collectState()
        advanceUntilIdle()

        viewModel.moveCurrency(3, 0)
        advanceUntilIdle()

        val relaunched = collectState()
        assertEquals(listOf("JPY", "USD", "EUR", "GBP", "INR"), relaunched().rows.map { it.code })
    }

    @Test
    fun `rates are cached so a later launch works with no network`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        collectState()
        advanceUntilIdle()

        // Everything the first run downloaded is now on disk.
        val cached = repository.snapshot.first()
        assertNotNull(cached)
        assertEquals(0.9142, cached!!.rates.getValue("EUR"), 1e-9)

        // Second launch with the network refusing every request.
        server.shutdown()
        val secondState = collectState()
        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()

        assertEquals("91.42", secondState().rows.first { it.code == "EUR" }.amountText)
        assertNotNull(secondState().ratesUpdatedAtMillis)
        // The cached rates have not expired yet, so no request was even attempted
        // and there is nothing to warn about.
        assertFalse(secondState().isOffline)
    }

    @Test
    fun `an expired cache with no network still converts, flagged offline`() = runTest(dispatcher) {
        // A snapshot whose next-update time has already gone by.
        server.enqueue(MockResponse().setBody(ratesBody(nextUpdateInSeconds = -3600)))
        collectState()
        advanceUntilIdle()
        assertNotNull(repository.snapshot.first())

        server.shutdown()
        val secondState = collectState()
        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()

        assertTrue("expected the offline flag after a failed refresh", secondState().isOffline)
        // Stale beats nothing: the numbers are still there, just labelled as old.
        assertEquals("91.42", secondState().rows.first { it.code == "EUR" }.amountText)
        assertNotNull(secondState().ratesUpdatedAtMillis)
    }

    @Test
    fun `a failed refresh keeps the cached rates instead of clearing them`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        advanceUntilIdle()
        assertFalse(state().isOffline)

        server.enqueue(MockResponse().setResponseCode(500))
        viewModel.refresh(force = true)
        advanceUntilIdle()

        assertTrue(state().isOffline)
        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()
        assertEquals("91.42", state().rows.first { it.code == "EUR" }.amountText)
    }

    @Test
    fun `a forced refresh picks up new rates`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody(ratesBody()))
        val state = collectState()
        viewModel.onAmountChanged("USD", "100")
        advanceUntilIdle()
        assertEquals("91.42", state().rows.first { it.code == "EUR" }.amountText)

        server.enqueue(MockResponse().setBody(ratesBody(eur = "1.5")))
        viewModel.refresh(force = true)
        advanceUntilIdle()

        assertEquals("150.00", state().rows.first { it.code == "EUR" }.amountText)
        assertEquals("Rates updated", state().transientMessage)
        assertFalse(state().isOffline)
    }

    @Test
    fun `rows render before any rates arrive`() = runTest(dispatcher) {
        server.shutdown()
        val state = collectState()
        advanceUntilIdle()

        // No snapshot, so amounts are blank, but the pinned rows and the offline
        // status are still there instead of an empty screen.
        assertEquals(listOf("USD", "EUR", "GBP", "JPY", "INR"), state().rows.map { it.code })
        assertTrue(state().isOffline)
        assertEquals(null, state().ratesUpdatedAtMillis)
        assertEquals("", state().rows.first { it.code == "EUR" }.amountText)
    }

    // --- harness ---------------------------------------------------------------

    private lateinit var viewModel: ConverterViewModel

    /**
     * Builds a ViewModel and keeps its [ConverterUiState] flow hot, returning an
     * accessor for the newest value.
     *
     * [ConverterViewModel.uiState] is a `WhileSubscribed` flow, so it only
     * recomputes while something collects it — hence the idle collector. The
     * accessor reads `value` rather than a copy captured in that collector, which
     * would only be as fresh as the last delivery.
     */
    private fun TestScope.collectState(newViewModel: Boolean = true): () -> ConverterUiState {
        if (newViewModel || !this@ConverterViewModelTest::viewModel.isInitialized) {
            viewModel = ConverterViewModel(repository)
        }
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        return { viewModel.uiState.value }
    }

    /**
     * A rates payload timed relative to now.
     *
     * The timestamps have to move with the clock: a snapshot is stale once the
     * provider's next-update time has passed, so a fixture with a hard-coded time
     * quietly changes meaning — and these tests' outcome — once that date goes by.
     */
    private fun ratesBody(
        nextUpdateInSeconds: Long = 24 * 60 * 60,
        eur: String = "0.9142",
    ): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        return """
            {"result":"success","base_code":"USD",
             "time_last_update_unix":${nowSeconds - 3600},
             "time_next_update_unix":${nowSeconds + nextUpdateInSeconds},
             "rates":{"USD":1,"EUR":$eur,"GBP":0.7891,"JPY":147.2,"INR":88.4,"CAD":1.375}}
        """.trimIndent()
    }
}
