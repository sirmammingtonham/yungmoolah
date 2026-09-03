package com.yungmoolah.converter

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.ui.MoolahScreen
import com.yungmoolah.converter.ui.ConverterUiState
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.yungmoolah.converter.domain.formatAmount
import com.yungmoolah.converter.domain.formatRate
import com.yungmoolah.converter.ui.AppHeader
import com.yungmoolah.converter.ui.CurrencyPickerContent
import com.yungmoolah.converter.ui.MoolahTab
import com.yungmoolah.converter.ui.ShortcutsList
import com.yungmoolah.converter.ui.CurrencyRowUi
import com.yungmoolah.converter.ui.theme.MoolahTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen state to `app/build/screenshots` for design review, and
 * fails if a state comes out blank — which is how a broken theme or a layout that
 * measures to nothing shows up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h900dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun row(code: String, amount: String, active: Boolean = false, rate: String? = null) =
        CurrencyRowUi(CURRENCY_BY_CODE.getValue(code), amount, active, rate)

    /** Rates per dollar, used to keep every fixture internally consistent. */
    private val perDollar = mapOf("EUR" to 0.9142, "GBP" to 0.7891, "JPY" to 147.2, "INR" to 88.4)

    private fun derivedRow(code: String, dollars: Double) = row(
        code = code,
        amount = formatAmount(dollars * perDollar.getValue(code), code),
        rate = "1 USD = ${formatRate(perDollar.getValue(code))}",
    )

    private val state = ConverterUiState(
        rows = listOf(row("USD", "1,250", active = true)) +
            perDollar.keys.map { derivedRow(it, 1250.0) },
        activeCode = "USD",
        isLoading = false,
        ratesUpdatedAtMillis = System.currentTimeMillis() - 7_200_000L,
        pinnedCodes = listOf("USD", "EUR", "GBP", "JPY", "INR"),
        homeCode = "USD",
        // Cards are built exactly as the ViewModel builds them, from rates quoted
        // per dollar, so the render cannot drift from the real screen.
        shortcuts = perDollar.map { (code, rate) -> shortcutCard(code, rate) },
    )

    private fun shoot(
        name: String,
        dark: Boolean,
        beforeCapture: () -> Unit = {},
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            MoolahTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
        beforeCapture()
        compose.waitForIdle()
        // Robolectric never runs a real redraw loop, so captureToImage() times out;
        // drawing the decor view straight into a bitmap works instead.
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File("build/screenshots").apply { mkdirs() }.resolve("$name.png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("$name rendered at ${bmp.width}x${bmp.height}", bmp.width > 300 && bmp.height > 600)
        assertTrue("$name rendered as a single flat colour", distinctColours(bmp) > 20)
    }

    /** Samples a grid of pixels; a screen that drew nothing has one colour. */
    private fun distinctColours(bmp: Bitmap): Int {
        val seen = HashSet<Int>()
        for (x in 0 until bmp.width step 8) {
            for (y in 0 until bmp.height step 8) seen += bmp.getPixel(x, y)
        }
        return seen.size
    }

    @Test
    fun light() = shoot("light", dark = false) {
        MoolahScreen(state, {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun dark() = shoot("dark", dark = true) {
        MoolahScreen(state, {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun offline() = shoot("offline", dark = false) {
        MoolahScreen(state.copy(isOffline = true), {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun picker() = shoot("picker", dark = false) {
        CurrencyPickerContent(pinned = listOf("USD", "EUR"), onPick = {})
    }

    @Test
    @Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h1400dp-xhdpi")
    fun shortcuts() = shoot("shortcuts", dark = false) {
        androidx.compose.foundation.layout.Column {
            AppHeader(selected = MoolahTab.Shortcuts, onSelect = {})
            ShortcutsList(state)
        }
    }

    @Test
    fun shortcutsDark() = shoot("shortcuts-dark", dark = true) {
        androidx.compose.foundation.layout.Column {
            AppHeader(selected = MoolahTab.Shortcuts, onSelect = {})
            ShortcutsList(state)
        }
    }

    @Test
    fun editing() {
        // A sum in the field, with every other row showing what it comes to.
        val expr = state.copy(
            rows = listOf(row("USD", "1,250×3", active = true, rate = "= 3,750.00")) +
                perDollar.keys.map { derivedRow(it, 3_750.0) },
        )
        shoot(
            name = "editing",
            dark = false,
            // Focusing a row is what brings the operator keys onto the screen.
            beforeCapture = {
                compose.onNodeWithContentDescription("US Dollar amount").performClick()
            },
        ) {
            MoolahScreen(expr, {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
        }
    }
}
