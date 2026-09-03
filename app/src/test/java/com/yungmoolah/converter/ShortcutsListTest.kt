package com.yungmoolah.converter

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.domain.ladderFor
import com.yungmoolah.converter.domain.mentalShortcut
import com.yungmoolah.converter.ui.ConverterUiState
import com.yungmoolah.converter.ui.ShortcutCardUi
import com.yungmoolah.converter.ui.ShortcutsList
import com.yungmoolah.converter.ui.theme.MoolahTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h2400dp")
class ShortcutsListTest {

    @get:Rule
    val compose = createComposeRule()

    private fun card(code: String, perDollar: Double): ShortcutCardUi {
        val rate = 1.0 / perDollar
        return ShortcutCardUi(
            from = CURRENCY_BY_CODE.getValue(code),
            to = CURRENCY_BY_CODE.getValue("USD"),
            rate = rate,
            shortcut = mentalShortcut(rate)!!,
            ladder = ladderFor(rate),
        )
    }

    /**
     * Regression: the list was keyed on the destination currency, which is the home
     * one on every card, so a second card crashed the tab with a duplicate key.
     */
    @Test
    fun `several pairs render together`() {
        val state = ConverterUiState(
            shortcuts = listOf(card("EUR", 0.9142), card("JPY", 147.2), card("GBP", 0.7891)),
            ratesUpdatedAtMillis = System.currentTimeMillis(),
        )
        compose.setContent { MoolahTheme { ShortcutsList(state) } }

        compose.onNodeWithText("EUR → USD").assertExists()
        compose.onNodeWithText("JPY → USD").assertExists()
        compose.onNodeWithText("GBP → USD").assertExists()
    }

    @Test
    fun `each card shows its recipe, its accuracy and its ladder`() {
        val state = ConverterUiState(
            shortcuts = listOf(card("JPY", 147.2)),
            ratesUpdatedAtMillis = System.currentTimeMillis(),
        )
        compose.setContent { MoolahTheme { ShortcutsList(state) } }

        compose.onNodeWithText("drop 2 zeros, then take off a third").assertExists()
        compose.onNodeWithText("within 1.9%").assertExists()
        // The ladder is denominated in the foreign currency people see on a price tag.
        compose.onNodeWithText("1,000").assertExists()
        compose.onNodeWithText("6.79").assertExists()
    }

    @Test
    fun `with nothing else pinned the tab explains itself`() {
        compose.setContent {
            MoolahTheme {
                ShortcutsList(ConverterUiState(ratesUpdatedAtMillis = System.currentTimeMillis()))
            }
        }
        compose.onNodeWithText("Pin a second currency", substring = true).assertExists()
    }

    @Test
    fun `with no rates the tab says why it is empty`() {
        compose.setContent { MoolahTheme { ShortcutsList(ConverterUiState()) } }
        compose.onNodeWithText("once the rates have been downloaded", substring = true).assertExists()
    }
}
