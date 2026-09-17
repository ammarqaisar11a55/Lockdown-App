# FocusLock R8 rules.
# Manifest components (DeviceAdminReceiver, receivers, activities) are kept by AAPT-generated rules.
# Room, Hilt and Compose ship their own consumer rules.

# Strip verbose/debug logging calls from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
