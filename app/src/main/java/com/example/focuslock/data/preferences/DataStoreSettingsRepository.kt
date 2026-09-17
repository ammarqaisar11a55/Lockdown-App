package com.example.focuslock.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.focuslock.domain.model.AppSettings
import com.example.focuslock.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    override suspend fun get(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings()).sanitized()
            prefs[ONBOARDING_COMPLETED] = updated.onboardingCompleted
            prefs[COUNTDOWN_MINUTES] = updated.countdownMinutes
            prefs[REMINDER_MINUTES] = updated.reminderMinutes
            prefs[DAILY_GOAL] = updated.dailyGoal
            prefs[MOTIVATION] = updated.motivationalMessage
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toSettings() = AppSettings(
        onboardingCompleted = this[ONBOARDING_COMPLETED] ?: false,
        countdownMinutes = this[COUNTDOWN_MINUTES] ?: AppSettings.DEFAULT_COUNTDOWN_MINUTES,
        reminderMinutes = this[REMINDER_MINUTES] ?: AppSettings.DEFAULT_REMINDER_MINUTES,
        dailyGoal = this[DAILY_GOAL].orEmpty(),
        motivationalMessage = this[MOTIVATION] ?: AppSettings.DEFAULT_MOTIVATION,
    ).sanitized()

    private fun AppSettings.sanitized() = copy(
        countdownMinutes = countdownMinutes.takeIf { it in AppSettings.COUNTDOWN_OPTIONS }
            ?: AppSettings.DEFAULT_COUNTDOWN_MINUTES,
        reminderMinutes = reminderMinutes.takeIf { it in AppSettings.REMINDER_OPTIONS }
            ?: AppSettings.DEFAULT_REMINDER_MINUTES,
        dailyGoal = dailyGoal.take(AppSettings.MAX_TEXT_LENGTH),
        motivationalMessage = motivationalMessage.take(AppSettings.MAX_TEXT_LENGTH),
    )

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val COUNTDOWN_MINUTES = intPreferencesKey("countdown_minutes")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        val DAILY_GOAL = stringPreferencesKey("daily_goal")
        val MOTIVATION = stringPreferencesKey("motivational_message")
    }
}
