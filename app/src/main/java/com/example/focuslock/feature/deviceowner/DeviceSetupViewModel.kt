package com.example.focuslock.feature.deviceowner

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.focuslock.core.common.IoDispatcher
import com.example.focuslock.core.device.DeviceCapabilityChecker
import com.example.focuslock.core.device.FocusDeviceAdminReceiver
import com.example.focuslock.domain.model.DeviceCapabilities
import com.example.focuslock.domain.model.ReconcileTrigger
import com.example.focuslock.domain.repository.LockdownCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DeviceSetupUiState(
    val capabilities: DeviceCapabilities? = null,
    val packageName: String = "",
    val provisionCommand: String = "",
    val verifyCommand: String = "",
)

@HiltViewModel
class DeviceSetupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val checker: DeviceCapabilityChecker,
    private val coordinator: LockdownCoordinator,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DeviceSetupUiState(
            packageName = context.packageName,
            provisionCommand = "adb shell dpm set-device-owner " +
                FocusDeviceAdminReceiver.componentName(context).flattenToShortString(),
            verifyCommand = "adb shell dumpsys device_policy",
        ),
    )
    val uiState: StateFlow<DeviceSetupUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val capabilities = withContext(ioDispatcher) { checker.check() }
            _uiState.update { it.copy(capabilities = capabilities) }
            // Permissions may have changed while the user was in system settings.
            coordinator.reconcile(ReconcileTrigger.PERMISSION_CHANGED)
        }
    }
}
