package com.yungmoolah.converter

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.work.WorkManager
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boots the real Application and Activity, which is the one thing the other tests
 * stub out: it catches a broken manifest, theme, or dependency graph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
class AppStartupTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `the app launches and shows the pinned currencies`() {
        compose.onNodeWithText("YungMoolah").assertExists()
        compose.onNodeWithText("US Dollar").assertExists()
        compose.onNodeWithText("Euro").assertExists()
        compose.onNodeWithText("Add currency").assertExists()
    }

    @Test
    fun `launching arms the periodic background refresh exactly once`() {
        val context = compose.activity.applicationContext
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("rates-refresh")
            .get()

        assertEquals(1, work.size)
        assertTrue(
            "expected the refresh to be enqueued, was ${work.first().state}",
            work.first().state == WorkInfo.State.ENQUEUED,
        )
    }
}
