package com.example.focuslock.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.example.focuslock.domain.model.InstalledApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Lists launchable apps. Uses a `<queries>` launcher intent declaration instead of the
 * broad QUERY_ALL_PACKAGES permission.
 */
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: DevicePolicyController,
) {
    fun launchableApplications(): List<InstalledApplication> {
        val packageManager = context.packageManager
        val essential = controller.essentialPackages()
        return queryLauncherActivities(packageManager)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { (pkg, _) -> pkg != context.packageName }
            .distinctBy { (pkg, _) -> pkg }
            .map { (pkg, label) -> InstalledApplication(pkg, label, essential = pkg in essential) }
            .sortedWith(compareByDescending<InstalledApplication> { it.essential }.thenBy { it.label.lowercase() })
            .toList()
    }

    fun isLaunchable(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    private fun queryLauncherActivities(packageManager: PackageManager): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
    }
}
