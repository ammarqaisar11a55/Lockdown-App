package com.example.focuslock.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.core.scheduling.ScheduleValidationError
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.feature.schedule.RepeatOption
import com.example.focuslock.feature.schedule.ScheduleEditorScreen
import com.example.focuslock.feature.schedule.ScheduleEditorUiState
import com.example.focuslock.feature.schedule.ScheduleForm
import com.example.focuslock.feature.schedule.TAG_ERROR
import com.example.focuslock.feature.schedule.TAG_NAME
import com.example.focuslock.feature.schedule.TAG_REVIEW
import com.example.focuslock.feature.schedule.TAG_SAVE
import com.example.focuslock.feature.schedule.TAG_STARTS_NOW
import com.example.focuslock.feature.schedule.TAG_STRICT
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.ui.theme.FocusLockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleEditorScreenTest {
    @get:Rule val rule = createComposeRule()

    private val review = FocusSchedule(
        id = "s",
        name = "University Study",
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(13, 0),
        repeat = RepeatRule.Daily,
        strictMode = false,
        enabled = true,
        autoStart = true,
        createdAt = 0,
    )

    /** Hosts the stateless screen with a minimal in-memory state holder. */
    private fun setContent(initial: ScheduleEditorUiState = ScheduleEditorUiState(loading = false)): () -> ScheduleEditorUiState {
        var state by mutableStateOf(initial)
        rule.setContent {
            FocusLockTheme {
                ScheduleEditorScreen(
                    state = state,
                    onBack = {},
                    onConfigureApps = {},
                    onFormChange = { transform -> state = state.copy(form = transform(state.form)) },
                    onToggleDay = {},
                    onSave = { state = state.copy(review = review.copy(name = state.form.name, strictMode = state.form.strictMode)) },
                    onConfirmSave = { state = state.copy(review = null, finished = true) },
                    onDismissReview = { state = state.copy(review = null) },
                    onDelete = {},
                )
            }
        }
        return { state }
    }

    @Test
    fun createFlowRequiresReviewConfirmation() {
        val state = setContent()
        rule.onNodeWithText("Create focus session").assertIsDisplayed()
        rule.onNodeWithTag(TAG_NAME).performTextInput("University Study")
        rule.onNodeWithTag(TAG_SAVE).performScrollTo().performClick()

        rule.onNodeWithTag(TAG_REVIEW).assertIsDisplayed()
        rule.onNodeWithTag(TAG_STARTS_NOW).assertDoesNotExist()
        rule.onNodeWithText("you will not be able to voluntarily end the session", substring = true).assertIsDisplayed()
        assertEquals(false, state().finished)

        rule.onNodeWithText("CONFIRM").performClick()
        assertTrue(state().finished)
        assertEquals("University Study", state().form.name)
    }

    @Test
    fun reviewWarnsWhenTheSessionWouldStartImmediately() {
        setContent(ScheduleEditorUiState(loading = false, review = review, reviewStartsNow = true))
        rule.onNodeWithTag(TAG_STARTS_NOW).assertIsDisplayed()
        rule.onNodeWithText("locks the device right away", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancellingReviewDoesNotSave() {
        val state = setContent()
        rule.onNodeWithTag(TAG_SAVE).performScrollTo().performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithTag(TAG_REVIEW).assertDoesNotExist()
        assertEquals(false, state().finished)
    }

    @Test
    fun strictModeNeedsExplicitConfirmation() {
        val state = setContent()
        rule.onNodeWithTag(TAG_STRICT).performScrollTo().performClick()
        rule.onNodeWithText("Enable Strict mode?").assertIsDisplayed()
        assertEquals(false, state().form.strictMode)

        rule.onNodeWithText("Cancel").performClick()
        assertEquals(false, state().form.strictMode)

        rule.onNodeWithTag(TAG_STRICT).performScrollTo().performClick()
        rule.onNodeWithText("ENABLE STRICT MODE").performClick()
        assertEquals(true, state().form.strictMode)
    }

    @Test
    fun repeatOptionsAndCustomDays() {
        val state = setContent()
        rule.onNodeWithText("Every day").performScrollTo().performClick()
        assertEquals(RepeatOption.DAILY, state().form.repeat)
        rule.onNodeWithText("Custom days").performScrollTo().performClick()
        assertEquals(RepeatOption.CUSTOM, state().form.repeat)
        rule.onNodeWithText("Once").performScrollTo().performClick()
        assertEquals(RepeatOption.ONCE, state().form.repeat)
    }

    @Test
    fun editModeShowsDeleteConfirmationAndErrors() {
        setContent(
            ScheduleEditorUiState(
                loading = false,
                isEditing = true,
                form = ScheduleForm(name = "Gym"),
                error = ScheduleValidationError.Overlaps("Study"),
            ),
        )
        rule.onNodeWithText("Edit focus session").assertIsDisplayed()
        rule.onNodeWithTag(TAG_ERROR).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Overlaps with", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescriptionDelete()
        rule.onNodeWithText("Delete session?").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionDelete() {
        onNode(androidx.compose.ui.test.hasContentDescription("Delete")).performClick()
    }
}
