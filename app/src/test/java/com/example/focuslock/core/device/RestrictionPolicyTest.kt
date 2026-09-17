package com.example.focuslock.core.device

import android.app.admin.DevicePolicyManager
import android.os.UserManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestrictionPolicyTest {

    @Test
    fun `standard sessions apply the baseline restrictions only`() {
        val restrictions = RestrictionPolicy.userRestrictions(strictMode = false, allowDebugging = false)
        assertEquals(RestrictionPolicy.STANDARD_RESTRICTIONS, restrictions)
        assertTrue(UserManager.DISALLOW_SAFE_BOOT in restrictions)
        assertTrue(UserManager.DISALLOW_APPS_CONTROL in restrictions)
        assertTrue(UserManager.DISALLOW_UNINSTALL_APPS in restrictions)
        assertTrue(UserManager.DISALLOW_CONFIG_DATE_TIME in restrictions)
    }

    @Test
    fun `strict release sessions disable debugging but debug builds keep it`() {
        assertTrue(UserManager.DISALLOW_DEBUGGING_FEATURES in RestrictionPolicy.userRestrictions(true, allowDebugging = false))
        assertFalse(UserManager.DISALLOW_DEBUGGING_FEATURES in RestrictionPolicy.userRestrictions(true, allowDebugging = true))
        assertTrue(UserManager.DISALLOW_INSTALL_APPS in RestrictionPolicy.userRestrictions(true, allowDebugging = true))
    }

    @Test
    fun `factory reset and emergency paths are never restricted`() {
        val strict = RestrictionPolicy.userRestrictions(strictMode = true, allowDebugging = false)
        assertFalse(UserManager.DISALLOW_FACTORY_RESET in strict)
        assertFalse(UserManager.DISALLOW_OUTGOING_CALLS in strict)
        for (strictMode in listOf(true, false)) {
            val features = RestrictionPolicy.lockTaskFeatures(strictMode)
            assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS != 0)
            assertTrue(features and DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD != 0)
        }
    }

    @Test
    fun `notifications and recents are never available in lock task`() {
        for (strictMode in listOf(true, false)) {
            val features = RestrictionPolicy.lockTaskFeatures(strictMode)
            assertEquals(0, features and DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS)
            assertEquals(0, features and DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW)
        }
        assertEquals(0, RestrictionPolicy.lockTaskFeatures(true) and DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO)
    }

    @Test
    fun `lock task allowlist always contains the app and essential packages`() {
        val packages = RestrictionPolicy.lockTaskPackages("me", setOf("dialer"), setOf("notes", "dialer", ""))
        assertEquals(listOf("me", "dialer", "notes"), packages)
    }
}
