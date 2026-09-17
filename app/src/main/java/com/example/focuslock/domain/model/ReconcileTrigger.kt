package com.example.focuslock.domain.model

/**
 * Why the policy reconciliation engine is running. Used for recovery bookkeeping and logs.
 *
 * [restoresScreen] marks background events after which a Device Owner session should bring the
 * lockdown screen back if lock task mode is no longer active. Triggers that come from the UI
 * itself never do, which prevents relaunch loops.
 */
enum class ReconcileTrigger(val isRecovery: Boolean, val restoresScreen: Boolean) {
    BOOT(isRecovery = true, restoresScreen = true),
    PROCESS_START(isRecovery = true, restoresScreen = true),
    PACKAGE_REPLACED(isRecovery = true, restoresScreen = true),
    ALARM(isRecovery = false, restoresScreen = true),
    TIME_CHANGED(isRecovery = false, restoresScreen = true),
    APP_FOREGROUND(isRecovery = false, restoresScreen = false),
    SESSION_TIMER(isRecovery = false, restoresScreen = false),
    SCHEDULES_CHANGED(isRecovery = false, restoresScreen = false),
    SETTINGS_CHANGED(isRecovery = false, restoresScreen = false),
    PERMISSION_CHANGED(isRecovery = false, restoresScreen = false),
    USER_ACTION(isRecovery = false, restoresScreen = false),
}
