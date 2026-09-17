package com.example.focuslock

import android.app.Application
import com.example.focuslock.core.common.ApplicationScope
import com.example.focuslock.core.notification.SessionNotifier
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FocusLockApp : Application() {

    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject lateinit var notifier: SessionNotifier

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        // Process-death recovery: every new process restores state from disk and re-verifies policy.
        applicationScope.launch { coordinator.reconcile(ReconcileTrigger.PROCESS_START) }
    }
}
