package com.example.focuslock.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.domain.model.FocusSchedule
import com.example.focuslock.domain.model.RepeatRule
import com.example.focuslock.feature.schedule.ScheduleListScreen
import com.example.focuslock.feature.schedule.ScheduleListUiState
import com.example.focuslock.ui.theme.FocusLockTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class ScheduleListScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun schedule(id: String, name: String, enabled: Boolean, repeat: RepeatRule = RepeatRule.Daily) = FocusSchedule(
        id = id,
        name = name,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(13, 0),
        repeat = repeat,
        strictMode = true,
        enabled = enabled,
        autoStart = true,
        createdAt = 0,
    )

    @Test
    fun groupsCardsAndConfirmsDelete() {
        var deleted: String? = null
        var duplicated: String? = null
        var toggled: Pair<String, Boolean>? = null
        rule.setContent {
            FocusLockTheme {
                ScheduleListScreen(
                    state = ScheduleListUiState(
                        loading = false,
                        today = listOf(schedule("a", "University Study", enabled = true)),
                        otherDays = listOf(schedule("b", "Weekend reading", enabled = false, RepeatRule.Weekly(setOf(DayOfWeek.SUNDAY)))),
                    ),
                    message = null,
                    onMessageShown = {},
                    onCreate = {},
                    onEdit = {},
                    onToggle = { id, on -> toggled = id to on },
                    onDuplicate = { duplicated = it.id },
                    onDelete = { deleted = it },
                )
            }
        }
        rule.onNodeWithText("Schedules").assertIsDisplayed()
        rule.onNodeWithText("TODAY").assertIsDisplayed()
        rule.onNodeWithText("OTHER DAYS").assertIsDisplayed()
        rule.onNodeWithText("Every day · Strict").assertIsDisplayed()
        rule.onAllNodesWithText("8:00").onFirst().assertIsDisplayed()

        val switches = rule.onAllNodes(isToggleable())
        switches[0].assertIsOn()
        switches[1].assertIsOff()
        switches[1].performClick()
        assertEquals("b" to true, toggled)

        rule.onAllNodesWithText("Duplicate").onFirst().performClick()
        assertEquals("a", duplicated)

        rule.onAllNodesWithText("Delete").onFirst().performClick()
        rule.onNodeWithText("Delete session?").assertIsDisplayed()
        assertEquals(null, deleted)
        rule.onNodeWithText("DELETE").performClick()
        assertEquals("a", deleted)
    }
}
