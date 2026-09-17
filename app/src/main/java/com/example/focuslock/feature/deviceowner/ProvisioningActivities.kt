package com.example.focuslock.feature.deviceowner

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

/**
 * Answers the system's managed-provisioning flow (QR code, NFC, zero-touch) on Android 10+:
 * FocusLock only supports fully managed (Device Owner) mode.
 *
 * Both activities are protected by BIND_DEVICE_ADMIN, so only the system can start them.
 * The system only uses these actions on Android 10 (API 29) and later.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class ProvisioningModeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().putExtra(
            DevicePolicyManager.EXTRA_PROVISIONING_MODE,
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
        )
        setResult(RESULT_OK, result)
        finish()
    }
}

/** Called once provisioning finished; no additional compliance steps are required. */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}
