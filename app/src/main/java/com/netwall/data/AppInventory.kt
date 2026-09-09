package com.netwall.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val icon: Drawable?,
)

class AppInventory(private val context: Context) {

    suspend fun load(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm: PackageManager = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        apps.mapNotNull { ai ->
            // Skip our own packages (main + debug suffix).
            if (ai.packageName == context.packageName) return@mapNotNull null
            val label = try {
                pm.getApplicationLabel(ai)?.toString() ?: ai.packageName
            } catch (_: Exception) {
                ai.packageName
            }
            val icon = try {
                pm.getApplicationIcon(ai)
            } catch (_: Exception) {
                null
            }
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            InstalledApp(
                packageName = ai.packageName,
                label = label,
                uid = ai.uid,
                isSystem = isSystem,
                icon = icon,
            )
        }.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }
}
