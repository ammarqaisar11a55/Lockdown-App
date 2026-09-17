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

        rule.onNodeWithText("Welcome to FocusLock").assertIsDisplayed()
        rule.onNodeWithText("Get started").performScrollTo().performClick()

        rule.onNodeWithText("How strong lockdown works").assertIsDisplayed()
        rule.onNodeWithText("Learn more").performScrollTo().performClick()
        rule.onNodeWithText("Show less").assertIsDisplayed()
        rule.onNodeWithText("Continue").performScrollTo().performClick()

        rule.onNodeWithText("Your safety comes first").assertIsDisplayed()
        rule.onNodeWithText("Set up this device").performScrollTo().performClick()
        assertEquals(true, result)
    }

    @Test
    fun canSkipSetup() {
        var result: Boolean? = null
        rule.setContent { FocusLockTheme { OnboardingScreen(onRequestNotifications = {}, onFinish = { result = it }) } }
        rule.onNodeWithText("Get started").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performScrollTo().performClick()
        rule.onNodeWithText("Not now").performScrollTo().performClick()
        assertEquals(false, result)
    }
}
