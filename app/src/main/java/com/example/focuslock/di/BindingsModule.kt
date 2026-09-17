package com.example.focuslock.di

import com.example.focuslock.core.common.AndroidFocusLogger
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.device.AndroidDevicePolicyController
import com.example.focuslock.core.device.DevicePolicyController
import com.example.focuslock.core.device.LockdownLauncher
import com.example.focuslock.core.device.LockdownPolicyEnforcer
import com.example.focuslock.core.device.PolicyEnforcer
import com.example.focuslock.core.notification.FocusNotifier
import com.example.focuslock.core.notification.SessionNotifier
import com.example.focuslock.core.scheduling.AlarmTransitionScheduler
import com.example.focuslock.core.scheduling.TransitionScheduler
import com.example.focuslock.core.security.PolicyReconciliationEngine
import com.example.focuslock.core.time.SystemTimeSource
import com.example.focuslock.core.time.TimeSource
import com.example.focuslock.data.preferences.DataStoreSettingsRepository
import com.example.focuslock.data.repository.RoomAllowedAppsRepository
import com.example.focuslock.data.repository.RoomHistoryRepository
import com.example.focuslock.data.repository.RoomLockdownStateRepository
import com.example.focuslock.data.repository.RoomScheduleRepository
import com.example.focuslock.domain.repository.AllowedAppsRepository
import com.example.focuslock.domain.repository.HistoryRepository
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.domain.repository.LockdownStateRepository
import com.example.focuslock.domain.repository.ScheduleRepository
import com.example.focuslock.domain.repository.SettingsRepository
import com.example.focuslock.feature.lockdown.ActivityLockdownLauncher
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.domain.usecase.DeviceOwnerStatus
import dagger.Binds
import dagger.Provides
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    companion object {
        @Provides
        fun provideDeviceOwnerStatus(
            controller: DevicePolicyController,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DeviceOwnerStatus = DeviceOwnerStatus { withContext(ioDispatcher) { controller.isDeviceOwner() } }
    }

    @Binds abstract fun bindLogger(impl: AndroidFocusLogger): FocusLogger
    @Binds abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource

    @Binds abstract fun bindDevicePolicyController(impl: AndroidDevicePolicyController): DevicePolicyController
    @Binds abstract fun bindPolicyEnforcer(impl: LockdownPolicyEnforcer): PolicyEnforcer
    @Binds abstract fun bindLockdownLauncher(impl: ActivityLockdownLauncher): LockdownLauncher
    @Binds abstract fun bindTransitionScheduler(impl: AlarmTransitionScheduler): TransitionScheduler
    @Binds abstract fun bindSessionNotifier(impl: FocusNotifier): SessionNotifier

    @Binds @Singleton abstract fun bindCoordinator(impl: PolicyReconciliationEngine): LockdownCoordinator

    @Binds abstract fun bindScheduleRepository(impl: RoomScheduleRepository): ScheduleRepository
    @Binds abstract fun bindAllowedAppsRepository(impl: RoomAllowedAppsRepository): AllowedAppsRepository
    @Binds abstract fun bindLockdownStateRepository(impl: RoomLockdownStateRepository): LockdownStateRepository
    @Binds abstract fun bindHistoryRepository(impl: RoomHistoryRepository): HistoryRepository
    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
