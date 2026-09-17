package com.example.focuslock.domain.model

/** Snapshot of what this app has actually configured through DevicePolicyManager. */
data class DevicePolicyState(
    val isDeviceOwner: Boolean,
    val isAdminActive: Boolean,
    val lockTaskPackages: Set<String>,
    val lockTaskActive: Boolean,
    val activeRestrictions: Set<String>,
    val uninstallBlocked: Boolean,
    val homeOverrideEnabled: Boolean,
)

enum class CapabilityStatus { OK, WARNING, UNAVAILABLE }

/** One row of the device-setup diagnostics screen. */
data class Capability(
    val id: CapabilityId,
    val status: CapabilityStatus,
    val detail: String,
)

enum class CapabilityId {
    DEVICE_OWNER,
    LOCK_TASK,
    BOOT_RECOVERY,
    NOTIFICATIONS,
    EXACT_ALARMS,
    APPLICATION_MANAGEMENT,
    AUTOMATIC_TIME,
    BATTERY,

    /** Without Device Owner: Android's own "Ask for PIN before unpinning" option. */
    PINNING_LOCK,
}

data class DeviceCapabilities(
    val capabilities: List<Capability>,
    val supportedRestrictions: List<String>,
    val manufacturerNote: String?,
) {
    fun status(id: CapabilityId): CapabilityStatus =
        capabilities.firstOrNull { it.id == id }?.status ?: CapabilityStatus.UNAVAILABLE

    /** Ready means full Device Owner enforcement with reliable timing. */
    val isReady: Boolean
        get() = status(CapabilityId.DEVICE_OWNER) == CapabilityStatus.OK &&
            status(CapabilityId.LOCK_TASK) == CapabilityStatus.OK &&
            status(CapabilityId.EXACT_ALARMS) == CapabilityStatus.OK
}

/** High-level application mode shown to the user. */
enum class AppMode {
    UNPROVISIONED,
    NORMAL,
    SCHEDULED,
    COUNTDOWN,
    LOCKDOWN,
    COMPLETED,
}
