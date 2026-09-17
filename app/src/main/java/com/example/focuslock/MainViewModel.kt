package com.example.focuslock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.SettingsRepository
import com.example.focuslock.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    stateRepository: LockdownStateRepository,
    private val coordinator: LockdownCoordinator,
) : ViewModel() {

    /** Resolved once, so later settings changes do not rebuild the navigation graph. */
    val startDestination: StateFlow<String?> = flow {
        val onboarded = settingsRepository.settings.first().onboardingCompleted
        emit(if (onboarded) Routes.DASHBOARD else Routes.ONBOARDING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lockdownActive = stateRepository.observeState()
        .map { it.isLocked }
        .distinctUntilChanged()

    fun onForeground() {
        viewModelScope.launch { coordinator.reconcile(ReconcileTrigger.APP_FOREGROUND) }
    }
}
