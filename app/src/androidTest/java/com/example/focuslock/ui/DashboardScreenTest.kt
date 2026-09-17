package com.example.focuslock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.domain.model.AppMode
import com.example.focuslock.domain.usecase.FocusStats
import com.example.focuslock.feature.dashboard.CountdownInfo
import com.example.focuslock.feature.dashboard.DashboardScreen
import com.example.focuslock.feature.dashboard.DashboardUiState
import com.example.focuslock.feature.dashboard.TodayItem
import com.example.focuslock.feature.dashboard.TodayItemState
import com.example.focuslock.ui.components.SessionItem
import com.example.focuslock.ui.theme.FocusLockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule val rule = createComposeRule()

    private val item = SessionItem("s1@1", "University Study", 0, "Wed, Sep 18", "8:00 AM → 1:00 PM", false, true)

    private fun show(state: DashboardUiState, onSkip: (String) -> Unit = {}, onSetup: () -> Unit = {}) {
        rule.setContent {
            FocusLockTheme {
                DashboardScreen(
                    state = state,
                    message = null,
                    onMessageShown = {},
                    onCreateSchedule = {},
                    onOpenSchedules = {},
                    onOpenDeviceSetup = onSetup,
                    onSkip = onSkip,
                    onStartPending = {},
                    onStartFocusNow = { _, _, _ -> },
                )
            }
        }
    }

    @Test
    fun showsProgressAndUpcomingSessions() {
        show(
            DashboardUiState(
                loading = false,
                mode = AppMode.SCHEDULED,
                isDeviceOwner = true,
                stats = FocusStats(Duration.ofMinutes(252), Duration.ofMinutes(315), Duration.ofHours(12), 3, 1),
                next = item,
                nextStartsIn = Duration.ofMinutes(32),
                today = listOf(TodayItem("s1@1", "University Study", Duration.ofHours(5), TodayItemState.DONE)),
                hasSchedules = true,
            ),
        )
        rule.onNodeWithText("Scheduled").assertIsDisplayed()
        rule.onNodeWithText("4h 12m").assertIsDisplayed()
        rule.onNodeWithText("80%").assertIsDisplayed()
        rule.onNodeWithText("Goal 5h 15m").assertIsDisplayed()
        rule.onNodeWithText("1h 03m to go").assertIsDisplayed()
        rule.onNodeWithText("8:00 AM → 1:00 PM").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Starts in 32m").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("VIEW SCHEDULE").performScrollTo().assertIsDisplayed()
        rule.onNode(hasContentDescription("University Study, finished, 5 hours")).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("OPEN DEVICE SETUP").assertDoesNotExist()
    }

    @Test
    fun countdownCanBeCancelledAfterConfirmation() {
        var skipped: String? = null
        show(
            DashboardUiState(
                loading = false,
                mode = AppMode.COUNTDOWN,
                isDeviceOwner = true,
                countdown = CountdownInfo(item, Duration.ofMinutes(5)),
                hasSchedules = true,
            ),
            onSkip = { skipped = it },
        )
        rule.onNode(hasContentDescription("5 minutes")).assertIsDisplayed()
        rule.onNodeWithText("CANCEL BEFORE SESSION STARTS").performScrollTo().performClick()
        rule.onNodeWithText("Cancel this session?").assertIsDisplayed()
        rule.onNodeWithText("SKIP SESSION").performClick()
        assertEquals("s1@1", skipped)
    }

    @Test
    fun unprovisionedDeviceLinksToSetup() {
        var opened = false
        show(DashboardUiState(loading = false, mode = AppMode.UNPROVISIONED, isDeviceOwner = false), onSetup = { opened = true })
        rule.onNodeWithText("Limited protection").assertIsDisplayed()
        rule.onNodeWithText("OPEN DEVICE SETUP").performClick()
        assertTrue(opened)
    }
}
