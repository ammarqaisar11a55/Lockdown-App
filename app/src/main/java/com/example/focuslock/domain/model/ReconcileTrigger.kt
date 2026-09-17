package com.example.focuslock.domain.model

/** Why the policy reconciliation engine is running. Used for recovery bookkeeping and logs. */
enum class ReconcileTrigger(val isRecovery: Boolean) {
    BOOT(isRecovery = true),
    PROCESS_START(isRecovery = true),
    PACKAGE_REPLACED(isRecovery = true),
    ALARM(isRecovery = false),
    TIME_CHANGED(isRecovery = false),
    APP_FOREGROUND(isRecovery = false),
    SESSION_TIMER(isRecovery = false),
    SCHEDULES_CHANGED(isRecovery = false),
    SETTINGS_CHANGED(isRecovery = false),
    PERMISSION_CHANGED(isRecovery = false),
    USER_ACTION(isRecovery = false),
}
