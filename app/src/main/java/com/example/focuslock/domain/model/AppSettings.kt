package com.example.focuslock.domain.model

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    /** Minutes of cancellable countdown before a session locks the device; 0 disables it. */
    val countdownMinutes: Int = DEFAULT_COUNTDOWN_MINUTES,
    /** Minutes before a session at which a reminder notification is posted; 0 disables it. */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val dailyGoal: String = "",
    val motivationalMessage: String = DEFAULT_MOTIVATION,
) {
    companion object {
        const val DEFAULT_COUNTDOWN_MINUTES = 5
        const val DEFAULT_REMINDER_MINUTES = 15
        const val DEFAULT_MOTIVATION = "Stay focused."
        val COUNTDOWN_OPTIONS = listOf(0, 1, 5, 10)
        val REMINDER_OPTIONS = listOf(0, 5, 15, 30)
        const val MAX_TEXT_LENGTH = 80
    }
}
