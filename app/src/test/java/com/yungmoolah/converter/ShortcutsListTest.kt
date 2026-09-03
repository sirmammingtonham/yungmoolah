package com.yungmoolah.converter

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.yungmoolah.converter.ui.ConverterUiState
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

    /**
     * Regression: the list was keyed on the destination currency, which is the home
     * one on every card, so a second card crashed the tab with a duplicate key.
     */
    @Test
    fun `several pairs render together`() {
        val state = ConverterUiState(
            shortcuts = listOf(
                shortcutCard("EUR", 0.9142),
                shortcutCard("JPY", 147.2),
                shortcutCard("GBP", 0.7891),
            ),
            homeCode = "USD",
            ratesUpdatedAtMillis = System.currentTimeMillis(),
        )
        compose.setContent { MoolahTheme { ShortcutsList(state) } }

        compose.onNodeWithText("EUR → USD").assertExists()
        compose.onNodeWithText("JPY → USD").assertExists()
        compose.onNodeWithText("GBP → USD").assertExists()
    }

    @Test
    fun `a card shows both directions, each with its own recipe and ladder`() {
        val state = ConverterUiState(
            shortcuts = listOf(shortcutCard("JPY", 147.2)),
            homeCode = "USD",
            ratesUpdatedAtMillis = System.currentTimeMillis(),
        )
        compose.setContent { MoolahTheme { ShortcutsList(state) } }

        compose.onNodeWithText("JPY ⇄ USD").assertExists()

        // Reading a price tag.
        compose.onNodeWithText("JPY → USD").assertExists()
        compose.onNodeWithText("drop 2 zeros, then take off a third").assertExists()
        compose.onNodeWithText("¥1,000").assertExists()
        compose.onNodeWithText("$6.79").assertExists()

        // Working out what to hand over.
        compose.onNodeWithText("USD → JPY").assertExists()
        compose.onNodeWithText("$10").assertExists()
        compose.onNodeWithText("¥1,472").assertExists()

        // Each direction states how far off its own recipe is.
        compose.onAllNodesWithText("within", substring = true).assertCountEquals(2)
    }

    @Test
    fun `the tab says where the home currency comes from`() {
        val state = ConverterUiState(
            shortcuts = listOf(shortcutCard("JPY", 147.2)),
            homeCode = "USD",
            ratesUpdatedAtMillis = System.currentTimeMillis(),
        )
        compose.setContent { MoolahTheme { ShortcutsList(state) } }

        compose.onNodeWithText("To and from USD", substring = true).assertExists()
        compose.onNodeWithText("top row on Convert", substring = true).assertExists()
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
