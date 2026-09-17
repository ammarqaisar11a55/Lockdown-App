package com.example.focuslock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.domain.model.AllowedApplication
import com.example.focuslock.domain.model.EnforcementLevel
import com.example.focuslock.feature.lockdown.LockdownDisplay
import com.example.focuslock.feature.lockdown.LockdownScreen
import com.example.focuslock.feature.lockdown.LockdownUiState
import com.example.focuslock.feature.lockdown.TAG_REMAINING
import com.example.focuslock.ui.theme.LockdownTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class LockdownScreenTest {
    @get:Rule val rule = createComposeRule()

    private val display = LockdownDisplay(
        sessionName = "University Study",
        remaining = Duration.ofHours(2).plusMinutes(47).plusSeconds(18),
        endTime = "5:00 PM",
        currentTime = "2:12 PM",
        dailyGoal = "Finish chapter 4",
        motivationalMessage = "Stay focused.",
        batteryPercent = 82,
        allowedApps = listOf(AllowedApplication("com.example.notes", "Notes")),
        strictMode = true,
        enforcement = EnforcementLevel.DEVICE_OWNER,
    )

    @Test
    fun showsSessionInformationWithoutUnlockButton() {
        rule.setContent { LockdownTheme { LockdownScreen(LockdownUiState.Locked(display), onLaunchApp = {}) } }

        rule.onNodeWithText("LOCKED").assertIsDisplayed()
        rule.onNodeWithTag(TAG_REMAINING).assertIsDisplayed()
        rule.onNode(hasContentDescription("2 hours 47 minutes remaining")).assertIsDisplayed()
        rule.onNodeWithText("University Study").assertIsDisplayed()
        rule.onNodeWithText("Ends at 5:00 PM").assertIsDisplayed()
        rule.onNodeWithText("Battery 82%").assertIsDisplayed()
        rule.onNodeWithText("2:12 PM").assertIsDisplayed()
        rule.onNodeWithText("Finish chapter 4").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Emergency calls", substring = true).performScrollTo().assertIsDisplayed()

        listOf("Unlock", "End session", "Exit", "Stop").forEach {
            rule.onNodeWithText(it, ignoreCase = true).assertDoesNotExist()
        }
        rule.onNodeWithText("Limited protection", substring = true).assertDoesNotExist()
    }

    @Test
    fun launchesAllowedApps() {
        var launched: String? = null
        rule.setContent { LockdownTheme { LockdownScreen(LockdownUiState.Locked(display), onLaunchApp = { launched = it }) } }
        rule.onNodeWithText("Notes").performScrollTo().performClick()
        assertEquals("com.example.notes", launched)
    }

    @Test
    fun pinningFallbackIsDisclosed() {
        val pinned = display.copy(enforcement = EnforcementLevel.SCREEN_PINNING)
        rule.setContent { LockdownTheme { LockdownScreen(LockdownUiState.Locked(pinned), onLaunchApp = {}) } }
        rule.onNodeWithText("Limited protection", substring = true).performScrollTo().assertIsDisplayed()
    }
}
