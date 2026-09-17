package com.example.focuslock.feature.lockdown

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.device.DevicePolicyController
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import com.example.focuslock.ui.theme.LockdownTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fullscreen lockdown surface and the lock task owner.
 *
 * startLockTask()/stopLockTask() are Activity-bound APIs, so they live here; every decision about
 * *whether* a session is active comes from the persisted state via [LockdownViewModel].
 */
@AndroidEntryPoint
class LockdownActivity : ComponentActivity() {

    @Inject lateinit var policyController: DevicePolicyController

    @Inject lateinit var coordinator: LockdownCoordinator

    @Inject lateinit var logger: FocusLogger

    private val viewModel: LockdownViewModel by viewModels()

    private var launchingAllowedApp = false
    private var pinningRequested = false
    private var isLocked = false
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = viewModel.onExitAttempt()
            },
        )
        setContent {
            LockdownTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                LockdownScreen(uiState = uiState, onLaunchApp = ::launchAllowedApp)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LockdownUiState.Locked -> {
                            isLocked = true
                            enterLockTaskIfNeeded()
                        }
                        LockdownUiState.NotLocked -> leaveLockdown()
                        LockdownUiState.Loading -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchingAllowedApp = false
        hideSystemBars()
        viewModel.onResumed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Reached only when Android lets the user navigate away (screen pinning fallback).
        if (isLocked && !launchingAllowedApp) viewModel.onExitAttempt()
    }

    private fun enterLockTaskIfNeeded() {
        if (policyController.isInLockTaskMode()) return
        val permitted = policyController.isLockTaskPermitted()
        // Without Device Owner, startLockTask() shows the screen-pinning prompt; ask once per screen.
        if (!permitted && pinningRequested) return
        pinningRequested = true
        try {
            startLockTask()
            logger.info(TAG, "Lock task entered (permitted=$permitted)")
        } catch (e: IllegalStateException) {
            logger.error(TAG, "startLockTask failed", e)
        } catch (e: SecurityException) {
            logger.error(TAG, "startLockTask rejected", e)
        }
    }

    private fun leaveLockdown() {
        isLocked = false
        if (leaving) return
        leaving = true
        lifecycleScope.launch {
            // Reconcile first so any leftover HOME override is cleared before we hand Home back.
            coordinator.reconcile(ReconcileTrigger.SESSION_TIMER)
            if (isInOwnLockTask()) {
                runCatching { stopLockTask() }
            }
            val launchedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
            finish()
            if (launchedAsHome) openSystemHome()
        }
    }

    private fun launchAllowedApp(packageName: String) {
        if (!viewModel.isLaunchAllowed(packageName)) return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchingAllowedApp = true
        try {
            startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            launchingAllowedApp = false
            logger.warn(TAG, "Allowed app could not be launched")
        } catch (e: SecurityException) {
            launchingAllowedApp = false
            logger.warn(TAG, "Allowed app launch blocked by lock task policy")
        }
    }

    private fun openSystemHome() {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(home) }
    }

    private fun isInOwnLockTask(): Boolean {
        val manager = getSystemService(ActivityManager::class.java)
        return manager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val TAG = "LOCKDOWN_UI"
    }
}
