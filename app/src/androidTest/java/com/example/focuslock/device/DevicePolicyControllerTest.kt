package com.example.focuslock.device

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.focuslock.core.common.AndroidFocusLogger
import com.example.focuslock.core.device.AndroidDevicePolicyController
import com.example.focuslock.core.device.LockdownPolicy
import com.example.focuslock.core.device.LockdownPolicyEnforcer
import com.example.focuslock.domain.model.EnforcementLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real DevicePolicyManager. Device Owner cases are skipped unless the debug app
 * has been provisioned (`adb shell dpm set-device-owner ...`); see docs/testing.md.
 */
@RunWith(AndroidJUnit4::class)
class DevicePolicyControllerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val controller = AndroidDevicePolicyController(context, launcher = {}, logger = AndroidFocusLogger())
    private val enforcer = LockdownPolicyEnforcer(controller, AndroidFocusLogger(), allowDebugging = true, ownPackage = context.packageName)

    @After
    fun cleanUp() {
        if (controller.isDeviceOwner()) enforcer.release(appliedByTest)
    }

    private var appliedByTest: Set<String> = emptySet()

    @Test
    fun deviceOwnerDetectionMatchesSystem() {
        assertEquals(dpm.isDeviceOwnerApp(context.packageName), controller.isDeviceOwner())
        val state = controller.readPolicyState()
        assertEquals(controller.isDeviceOwner(), state.isDeviceOwner)
        assertFalse(state.homeOverrideEnabled)
    }

    @Test
    fun withoutDeviceOwnerEverythingDegradesSafely() {
        assumeFalse(controller.isDeviceOwner())
        val applied = enforcer.apply(LockdownPolicy(setOf("com.android.settings"), strictMode = true), emptySet())
        assertEquals(EnforcementLevel.SCREEN_PINNING, applied.enforcement)
        assertTrue(applied.restrictions.isEmpty())
        assertTrue(controller.readPolicyState().lockTaskPackages.isEmpty())
        assertFalse(controller.isLockTaskPermitted())
        assertFalse(enforcer.hasLeftovers(emptySet()))
    }

    @Test
    fun deviceOwnerAppliesAndReleasesLockdownPolicy() {
        assumeTrue(controller.isDeviceOwner())
        val policy = LockdownPolicy(emptySet(), strictMode = true)
        val applied = enforcer.apply(policy, emptySet())
        appliedByTest = applied.restrictions
        assertEquals(EnforcementLevel.DEVICE_OWNER, applied.enforcement)

        val state = controller.readPolicyState()
        assertTrue(controller.isLockTaskPermitted())
        assertTrue(context.packageName in state.lockTaskPackages)
        assertTrue(state.homeOverrideEnabled)
        assertTrue(state.uninstallBlocked)
        assertTrue(UserManager.DISALLOW_SAFE_BOOT in state.activeRestrictions)
        assertTrue(UserManager.DISALLOW_APPS_CONTROL in state.activeRestrictions)
        // Debug builds never disable ADB.
        assertFalse(UserManager.DISALLOW_DEBUGGING_FEATURES in state.activeRestrictions)
        assertFalse(enforcer.needsRepair(policy, EnforcementLevel.DEVICE_OWNER))

        enforcer.release(applied.restrictions)
        appliedByTest = emptySet()
        val released = controller.readPolicyState()
        assertTrue(released.lockTaskPackages.isEmpty())
        assertFalse(released.homeOverrideEnabled)
        assertFalse(released.uninstallBlocked)
        assertTrue(released.activeRestrictions.intersect(applied.restrictions).isEmpty())
        assertFalse(enforcer.hasLeftovers(emptySet()))
    }

    @Test
    fun deviceOwnerDetectsPolicyDrift() {
        assumeTrue(controller.isDeviceOwner())
        val policy = LockdownPolicy(emptySet(), strictMode = false)
        appliedByTest = enforcer.apply(policy, emptySet()).restrictions
        controller.removeRestrictions(setOf(UserManager.DISALLOW_SAFE_BOOT))
        assertTrue(enforcer.needsRepair(policy, EnforcementLevel.DEVICE_OWNER))
    }
}
