package com.yungmoolah.converter

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yungmoolah.converter.data.CURRENCY_BY_CODE
import com.yungmoolah.converter.ui.ConverterScreen
import com.yungmoolah.converter.ui.ConverterUiState
import com.yungmoolah.converter.ui.CurrencyPickerContent
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

    private val state = ConverterUiState(
        rows = listOf(
            row("USD", "1,250", active = true),
            row("EUR", "1,142.75", rate = "1 USD = 0.9142"),
            row("GBP", "986.38", rate = "1 USD = 0.7891"),
            row("JPY", "184,000", rate = "1 USD = 147.20"),
            row("INR", "110,500.00", rate = "1 USD = 88.4000"),
        ),
        activeCode = "USD",
        isLoading = false,
        ratesUpdatedAtMillis = System.currentTimeMillis() - 7_200_000L,
        pinnedCodes = listOf("USD", "EUR", "GBP", "JPY", "INR"),
    )

    private fun shoot(name: String, dark: Boolean, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            MoolahTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
            }
        }
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
        ConverterScreen(state, {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun dark() = shoot("dark", dark = true) {
        ConverterScreen(state, {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun offline() = shoot("offline", dark = false) {
        ConverterScreen(state.copy(isOffline = true), {_,_->}, {}, {}, {}, {_,_->}, {}, {}, {}, {})
    }

    @Test
    fun picker() = shoot("picker", dark = false) {
        CurrencyPickerContent(pinned = listOf("USD", "EUR"), onPick = {})
    }
}
