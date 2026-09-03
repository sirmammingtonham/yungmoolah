package com.yungmoolah.converter

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.ui.MoolahScreen
import com.yungmoolah.converter.ui.ConverterUiState
import com.yungmoolah.converter.ui.CurrencyRowUi
import com.yungmoolah.converter.ui.theme.MoolahTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Renders the real screen on the JVM to check the composition and its callbacks. */
@RunWith(RobolectricTestRunner::class)
// A tall window so the whole page, including the trailing add tile, composes at once.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class ConverterScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun row(code: String, amount: String, active: Boolean = false, rate: String? = null) =
        CurrencyRowUi(
            info = CURRENCY_BY_CODE.getValue(code),
            amountText = amount,
            isActive = active,
            rateLabel = rate,
        )

    private val state = ConverterUiState(
        rows = listOf(
            row("USD", "100", active = true),
            row("EUR", "91.42", rate = "1 USD = 0.9142"),
            row("JPY", "14,720", rate = "1 USD = 147.20"),
        ),
        activeCode = "USD",
        isLoading = false,
        isRefreshing = false,
        isOffline = false,
        ratesUpdatedAtMillis = System.currentTimeMillis() - 60_000L,
        pinnedCodes = listOf("USD", "EUR", "JPY"),
    )

    @Test
    fun `renders every pinned currency with its amount`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onNodeWithText("USD").assertExists()
        compose.onNodeWithText("Euro").assertExists()
        compose.onNodeWithText("Japanese Yen").assertExists()
        compose.onNodeWithContentDescription("US Dollar amount").assertTextEquals("100")
        compose.onNodeWithContentDescription("Euro amount").assertTextEquals("91.42")
        compose.onNodeWithContentDescription("Japanese Yen amount").assertTextEquals("14,720")
        compose.onNodeWithText("1 USD = 147.20").assertExists()
    }

    @Test
    fun `typing in a row reports the new amount for that currency`() {
        val edits = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MoolahTheme {
                MoolahScreen(state, onAmountChanged = { code, text -> edits += code to text })
            }
        }

        compose.onNodeWithContentDescription("Euro amount").performTextReplacement("250")

        assertEquals(listOf("EUR" to "250"), edits)
    }

    @Test
    fun `the status line reports when the rates were updated`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }
        compose.onNodeWithText("Rates updated", substring = true).assertExists()
    }

    @Test
    fun `an offline cache is called out instead of shown as current`() {
        compose.setContent {
            MoolahTheme { MoolahScreen(state.copy(isOffline = true)) }
        }
        compose.onNodeWithText("Offline", substring = true).assertExists()
    }

    @Test
    fun `with no rates yet the screen invites a download`() {
        compose.setContent {
            MoolahTheme {
                MoolahScreen(
                    state.copy(
                        ratesUpdatedAtMillis = null,
                        isOffline = true,
                        rows = state.rows.map { it.copy(amountText = "", rateLabel = null) },
                    )
                )
            }
        }
        compose.onNodeWithText("No rates yet", substring = true).assertExists()
    }

    @Test
    fun `the clear key wipes the row being edited`() {
        var cleared: String? = null
        compose.setContent { MoolahTheme { MoolahScreen(state, onClear = { cleared = it }) } }

        compose.onNodeWithContentDescription("US Dollar amount").performClick()
        compose.onNodeWithContentDescription("Clear amount").performClick()

        assertEquals("USD", cleared)
    }

    @Test
    fun `the clear key is on the arithmetic bar, not on the rows`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        // Nothing on the rows themselves until a row is being edited.
        compose.onAllNodesWithContentDescription("Clear amount").assertCountEquals(0)

        compose.onNodeWithContentDescription("Euro amount").performClick()

        // Exactly one, on the bar, however many rows there are.
        compose.onAllNodesWithContentDescription("Clear amount").assertCountEquals(1)
    }

    @Test
    fun `the removal snackbar dismisses itself`() {
        var messageConsumed = false
        compose.setContent {
            MoolahTheme {
                MoolahScreen(
                    state.copy(transientMessage = "Removed GBP"),
                    onMessageShown = { messageConsumed = true },
                )
            }
        }

        compose.onNodeWithText("Removed GBP").assertExists()
        compose.onNodeWithText("Undo").assertExists()

        // Regression: Material defaults a snackbar with an action to Indefinite, so
        // this bar used to sit on screen until it was tapped.
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.onAllNodesWithText("Removed GBP").fetchSemanticsNodes().isEmpty()
        }
        assertTrue("expected the message to be consumed after dismissal", messageConsumed)
    }

    @Test
    fun `tapping a row starts editing it`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onNodeWithContentDescription("Euro amount").performClick()

        compose.onNodeWithContentDescription("Euro amount").assertIsFocused()
    }

    @Test
    fun `tapping outside the rows drops the highlight`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }
        compose.onNodeWithContentDescription("Euro amount").performClick()
        compose.onNodeWithContentDescription("Euro amount").assertIsFocused()

        // The footer area belongs to no row, so the tap reaches the page behind it.
        compose.onNodeWithText("Rates By Exchange Rate API").performClick()

        compose.onNodeWithContentDescription("Euro amount").assertIsNotFocused()
    }

    @Test
    fun `the branding and the gesture hints are gone`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onAllNodesWithText("YungMoolah").assertCountEquals(0)
        compose.onAllNodesWithText("every other row follows", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("swipe a row", substring = true).assertCountEquals(0)
    }

    @Test
    fun `the rates provider is still credited, as its terms require`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onNodeWithText("Rates By Exchange Rate API").assertExists()
    }

    @Test
    fun `the header carries the name quietly and both tabs`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onNodeWithText("yungmoolah").assertExists()
        compose.onNodeWithText("Convert").assertExists()
        compose.onNodeWithText("Shortcuts").assertExists()
    }

    @Test
    fun `the shortcuts tab replaces the converter`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }
        compose.onNodeWithText("Euro").assertExists()

        compose.onNodeWithText("Shortcuts").performClick()

        compose.onAllNodesWithText("Euro").assertCountEquals(0)
        compose.onNodeWithText("Pin a second currency", substring = true).assertExists()

        compose.onNodeWithText("Convert").performClick()
        compose.onNodeWithText("Euro").assertExists()
    }

    @Test
    fun `the operator keys appear only while a row is being edited`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onAllNodesWithContentDescription("Multiply").assertCountEquals(0)

        compose.onNodeWithContentDescription("Euro amount").performClick()

        compose.onNodeWithContentDescription("Multiply").assertExists()
        compose.onNodeWithContentDescription("Divide").assertExists()
        compose.onNodeWithContentDescription("Open bracket").assertExists()
    }

    @Test
    fun `an operator key appends to the row being edited`() {
        val edits = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MoolahTheme {
                MoolahScreen(state, onAmountChanged = { code, text -> edits += code to text })
            }
        }

        compose.onNodeWithContentDescription("US Dollar amount").performClick()
        compose.onNodeWithContentDescription("Multiply").performClick()

        assertEquals(listOf("USD" to "100×"), edits)
    }

    @Test
    fun `the add tile opens the picker`() {
        compose.setContent { MoolahTheme { MoolahScreen(state) } }

        compose.onNodeWithText("Add currency").performClick()

        compose.onNodeWithText("Add a currency").assertExists()
    }

    @Test
    fun `tapping the status chip triggers a refresh`() {
        var refreshes = 0
        compose.setContent {
            MoolahTheme { MoolahScreen(state, onRefresh = { refreshes++ }) }
        }

        compose.onNodeWithText("Rates updated", substring = true).performClick()

        assertEquals(1, refreshes)
    }
}

/** Keeps the tests focused on one callback at a time. */
@Composable
private fun MoolahScreen(
    state: ConverterUiState,
    onAmountChanged: (String, String) -> Unit = { _, _ -> },
    onAdd: (String) -> Unit = {},
    onRefresh: () -> Unit = {},
    onClear: (String) -> Unit = {},
    onMessageShown: () -> Unit = {},
) = MoolahScreen(
    state = state,
    onAmountChanged = onAmountChanged,
    onRowFocused = {},
    onClear = onClear,
    onRemove = {},
    onMove = { _, _ -> },
    onAdd = onAdd,
    onUndoRemove = {},
    onRefresh = onRefresh,
    onMessageShown = onMessageShown,
)
