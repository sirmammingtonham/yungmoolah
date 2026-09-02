package com.yungmoolah.converter

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.yungmoolah.converter.data.ALL_CURRENCIES
import com.yungmoolah.converter.ui.CurrencyPickerContent
import com.yungmoolah.converter.ui.searchCurrencies
import com.yungmoolah.converter.ui.theme.MoolahTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h1600dp")
class CurrencyPickerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `picking a currency reports its code`() {
        var picked: String? = null
        compose.setContent {
            MoolahTheme { CurrencyPickerContent(pinned = listOf("USD"), onPick = { picked = it }) }
        }

        // CAD is in the popular list, so it shows without searching.
        compose.onNodeWithText("Canadian Dollar").performClick()

        assertEquals("CAD", picked)
    }

    @Test
    fun `an already pinned currency cannot be picked twice`() {
        var picked: String? = null
        compose.setContent {
            MoolahTheme { CurrencyPickerContent(pinned = listOf("USD"), onPick = { picked = it }) }
        }

        compose.onNodeWithText("US Dollar").performClick()

        assertEquals(null, picked)
        compose.onNodeWithContentDescription("Already pinned").assertExists()
    }

    @Test
    fun `searching by name narrows the list`() {
        var picked: String? = null
        compose.setContent {
            MoolahTheme { CurrencyPickerContent(pinned = emptyList(), onPick = { picked = it }) }
        }

        compose.onNodeWithContentDescription("Search currencies").performTextReplacement("rupee")
        compose.onNodeWithText("Indian Rupee").performClick()

        assertEquals("INR", picked)
    }

    @Test
    fun `a query with no matches says so`() {
        compose.setContent {
            MoolahTheme { CurrencyPickerContent(pinned = emptyList(), onPick = {}) }
        }

        compose.onNodeWithContentDescription("Search currencies").performTextReplacement("zzzz")

        compose.onNodeWithText("No currency matches", substring = true).assertExists()
    }

    // --- ranking ---------------------------------------------------------------

    @Test
    fun `an empty query leads with the popular currencies and lists them all once`() {
        val results = searchCurrencies("")
        assertEquals("USD", results.first().code)
        assertEquals(ALL_CURRENCIES.size, results.size)
        assertEquals(results.size, results.map { it.code }.distinct().size)
    }

    @Test
    fun `an exact code match ranks first`() {
        assertEquals("SEK", searchCurrencies("sek").first().code)
        // "CAD" also appears inside "Canadian Dollar" for other currencies.
        assertEquals("CAD", searchCurrencies("CAD").first().code)
    }

    @Test
    fun `matching is case insensitive across codes and names`() {
        assertTrue(searchCurrencies("yen").any { it.code == "JPY" })
        assertTrue(searchCurrencies("YEN").any { it.code == "JPY" })
        assertTrue(searchCurrencies("swiss").any { it.code == "CHF" })
    }

    @Test
    fun `a query that matches nothing returns nothing`() {
        assertTrue(searchCurrencies("zzzz").isEmpty())
    }
}
