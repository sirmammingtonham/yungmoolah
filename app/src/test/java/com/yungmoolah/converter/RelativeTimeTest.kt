package com.yungmoolah.converter

import com.yungmoolah.converter.ui.relativeTimeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RelativeTimeTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `a missing timestamp reads as never`() {
        assertEquals("never", relativeTimeLabel(null, now))
        assertEquals("never", relativeTimeLabel(0L, now))
    }

    @Test
    fun `anything under a minute reads as just now`() {
        assertEquals("just now", relativeTimeLabel(now, now))
        assertEquals("just now", relativeTimeLabel(now - 59_000L, now))
    }

    @Test
    fun `older timestamps name their age`() {
        assertTrue(relativeTimeLabel(now - 5 * 60_000L, now).contains("5"))
        assertTrue(relativeTimeLabel(now - 3 * 3_600_000L, now).contains("3"))
        assertTrue(relativeTimeLabel(now - 2 * 86_400_000L, now).contains("2"))
    }
}
