package com.example.focuslock.core.device

import com.example.focuslock.core.common.AllowDebugging
import com.example.focuslock.core.common.FocusLogger
import com.example.focuslock.core.common.OwnPackage
import com.example.focuslock.domain.model.EnforcementLevel
import javax.inject.Inject

data class AppliedPolicy(
    val enforcement: EnforcementLevel,
    val restrictions: Set<String>,
)

/** Applies, verifies and releases the complete device configuration of a lockdown session. */
interface PolicyEnforcer {
    fun isDeviceOwner(): Boolean
    fun isLockTaskActive(): Boolean
    fun apply(policy: LockdownPolicy, alreadyApplied: Set<String>): AppliedPolicy
    fun release(applied: Set<String>)

    /** True when the device no longer matches [policy] (e.g. after a crash or external change). */
    fun needsRepair(policy: LockdownPolicy, enforcement: EnforcementLevel?): Boolean

    /** True when session configuration is still present although no session is active. */
    fun hasLeftovers(applied: Set<String>): Boolean
}

class LockdownPolicyEnforcer @Inject constructor(
    private val controller: DevicePolicyController,
    private val logger: FocusLogger,
    @AllowDebugging private val allowDebugging: Boolean,
    @OwnPackage private val ownPackage: String,
) : PolicyEnforcer {

    override fun isDeviceOwner(): Boolean = controller.isDeviceOwner()

    override fun isLockTaskActive(): Boolean = controller.isInLockTaskMode()

    override fun apply(policy: LockdownPolicy, alreadyApplied: Set<String>): AppliedPolicy {
        if (!controller.isDeviceOwner()) {
            logger.warn(TAG, "Device Owner unavailable; falling back to screen pinning")
            return AppliedPolicy(EnforcementLevel.SCREEN_PINNING, alreadyApplied)
        }
        controller.setLockTaskPackages(expectedPackages(policy))
        controller.setLockTaskFeatures(RestrictionPolicy.lockTaskFeatures(policy.strictMode))
        controller.setHomeOverride(enabled = true)
        controller.setUninstallBlocked(blocked = true)
        val added = controller.applyRestrictions(RestrictionPolicy.userRestrictions(policy.strictMode, allowDebugging))
        logger.info(TAG, "Restrictions applied (${added.size} added, strict=${policy.strictMode})")
        return AppliedPolicy(EnforcementLevel.DEVICE_OWNER, alreadyApplied + added)
    }

    override fun release(applied: Set<String>) {
        if (!controller.isDeviceOwner()) return
        controller.removeRestrictions(applied)
        controller.setUninstallBlocked(blocked = false)
        controller.setHomeOverride(enabled = false)
        controller.stopLockTaskMode()
        logger.info(TAG, "Restrictions released")
    }

    override fun needsRepair(policy: LockdownPolicy, enforcement: EnforcementLevel?): Boolean {
        val state = controller.readPolicyState()
        if (!state.isDeviceOwner) return false
        // Device Owner became available during a pinned session: upgrade enforcement.
        if (enforcement != EnforcementLevel.DEVICE_OWNER) return true
        val expectedRestrictions = RestrictionPolicy.userRestrictions(policy.strictMode, allowDebugging)
        return state.lockTaskPackages != expectedPackages(policy).toSet() ||
            !state.homeOverrideEnabled ||
            !state.uninstallBlocked ||
            !state.activeRestrictions.containsAll(expectedRestrictions)
    }

    override fun hasLeftovers(applied: Set<String>): Boolean {
        val state = controller.readPolicyState()
        if (!state.isDeviceOwner) return false
        return applied.isNotEmpty() ||
            state.lockTaskPackages.isNotEmpty() ||
            state.homeOverrideEnabled ||
            state.uninstallBlocked
    }

    private fun expectedPackages(policy: LockdownPolicy): List<String> =
        RestrictionPolicy.lockTaskPackages(ownPackage, controller.essentialPackages(), policy.allowedPackages)

    private companion object {
        const val TAG = "LOCKDOWN_ENGINE"
    }
}
