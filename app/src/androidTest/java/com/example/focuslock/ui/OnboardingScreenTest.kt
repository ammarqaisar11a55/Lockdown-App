package com.example.focuslock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.feature.onboarding.OnboardingScreen
import com.example.focuslock.ui.theme.FocusLockTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun walksThroughPagesAndOpensSetup() {
        var result: Boolean? = null
        rule.setContent { FocusLockTheme { OnboardingScreen(onRequestNotifications = {}, onFinish = { result = it }) } }

        rule.onNodeWithText("Welcome to Lockdown App").assertIsDisplayed()
        rule.onNodeWithText("GET STARTED").performClick()

        rule.onNodeWithText("How strong lockdown works").assertIsDisplayed()
        rule.onNodeWithText("Learn more").performScrollTo().performClick()
        rule.onNodeWithText("Show less").assertIsDisplayed()
        rule.onNodeWithText("CONTINUE").performClick()

        rule.onNodeWithText("Your safety comes first").assertIsDisplayed()
        rule.onNodeWithText("SET UP THIS DEVICE").performClick()
        assertEquals(true, result)
    }

    @Test
    fun canSkipSetup() {
        var result: Boolean? = null
        rule.setContent { FocusLockTheme { OnboardingScreen(onRequestNotifications = {}, onFinish = { result = it }) } }
        rule.onNodeWithText("GET STARTED").performClick()
        rule.onNodeWithText("CONTINUE").performClick()
        rule.onNodeWithText("Not now").performClick()
        assertEquals(false, result)
    }
}
