package com.yungmoolah.converter

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.ui.ConverterListTag
import com.yungmoolah.converter.ui.ConverterUiState
import com.yungmoolah.converter.ui.CurrencyRowUi
import com.yungmoolah.converter.ui.MoolahScreen
import com.yungmoolah.converter.ui.theme.MoolahTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard is the one part of the layout the other UI tests cannot see:
 * Robolectric reports no IME inset, so a screen that subtracts the keyboard twice
 * still renders perfectly in every screenshot. This test posts a real inset to the
 * view hierarchy and checks how much room the list is actually left with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class KeyboardInsetTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun row(code: String, amount: String, active: Boolean = false) =
        CurrencyRowUi(CURRENCY_BY_CODE.getValue(code), amount, active, null)

    private val state = ConverterUiState(
        rows = listOf(row("USD", "100", active = true), row("EUR", "91.42"), row("JPY", "14,720")),
        activeCode = "USD",
        isLoading = false,
        ratesUpdatedAtMillis = System.currentTimeMillis(),
        pinnedCodes = listOf("USD", "EUR", "JPY"),
    )

    /** Posts an IME inset of [imeHeightPx] to every Compose view in the hierarchy. */
    private fun showKeyboard(imeHeightPx: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeHeightPx))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        compose.activity.window.decorView.forEachViewIn { view ->
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
        compose.waitForIdle()
    }

    private fun View.forEachViewIn(action: (View) -> Unit) {
        action(this)
        if (this is ViewGroup) {
            for (i in 0 until childCount) getChildAt(i).forEachViewIn(action)
        }
    }

    @androidx.compose.runtime.Composable
    private fun Screen() = MoolahScreen(
        state = state,
        onAmountChanged = { _, _ -> },
        onRowFocused = {},
        onClear = {},
        onRemove = {},
        onMove = { _, _ -> },
        onAdd = {},
        onUndoRemove = {},
        onRefresh = {},
        onMessageShown = {},
    )

    private fun listHeightPx(): Int =
        compose.onNodeWithTag(ConverterListTag).fetchSemanticsNode().size.height

    @Test
    fun `the list keeps most of the screen when the keyboard is down`() {
        compose.setContent { MoolahTheme { Screen() } }

        val screen = compose.activity.window.decorView.height
        assertTrue("screen measured $screen px", screen > 0)
        assertTrue(
            "list got ${listHeightPx()} of $screen px",
            listHeightPx() > screen * 0.7,
        )
    }

    /**
     * Regression: the content subtracted the keyboard once through Scaffold's inner
     * padding (whose bottom is the operator bar, and the bar carries its own
     * imePadding) and again through an imePadding of its own. The list collapsed to
     * a sliver, and focusing a row scrolled it to the top of a viewport barely one
     * row tall.
     */
    @Test
    fun `the keyboard is subtracted once, not twice`() {
        compose.setContent { MoolahTheme { Screen() } }
        val screen = compose.activity.window.decorView.height
        val ime = screen / 2

        // Editing a row is what puts the operator bar on screen and the keyboard up.
        compose.onNodeWithContentDescription("US Dollar amount").performClick()
        showKeyboard(ime)

        val remaining = screen - ime
        val list = listHeightPx()
        assertTrue("no IME inset reached the layout; list unchanged at $list px", list < screen * 0.7)
        assertTrue(
            "list got $list px of the $remaining px left above a ${ime}px keyboard",
            list > remaining * 0.5,
        )
    }

    @Test
    fun `the row being edited stays visible above the keyboard`() {
        compose.setContent { MoolahTheme { Screen() } }
        val ime = compose.activity.window.decorView.height / 2

        compose.onNodeWithContentDescription("US Dollar amount").performClick()
        showKeyboard(ime)

        // Both the row being edited and the arithmetic keys have to be reachable.
        compose.onNodeWithContentDescription("US Dollar amount").assertIsDisplayed()
        compose.onNodeWithContentDescription("Multiply").assertIsDisplayed()
    }

    @Test
    fun `the operator keys sit above the keyboard, not behind it`() {
        compose.setContent { MoolahTheme { Screen() } }
        val screen = compose.activity.window.decorView.height
        val ime = screen / 2

        compose.onNodeWithContentDescription("US Dollar amount").performClick()
        showKeyboard(ime)

        val keys = compose.onNodeWithContentDescription("Multiply").fetchSemanticsNode()
        val keysBottom = keys.boundsInRoot.bottom
        assertTrue(
            "operator keys reach ${keysBottom}px, past the top of the keyboard at ${screen - ime}px",
            keysBottom <= screen - ime + 1,
        )
    }
}
