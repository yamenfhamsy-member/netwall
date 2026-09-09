package com.netwall.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
)

class AppInventory(private val context: Context) {

    /**
     * Fast metadata-only scan (no icons). Icons are loaded lazily per row
     * by [com.netwall.ui.IconLoader] so the list appears instantly.
     */
    suspend fun loadNames(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm: PackageManager = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val out = ArrayList<InstalledApp>(apps.size)
        for (ai in apps) {
            if (ai.packageName == context.packageName) continue
            val label = try {
                pm.getApplicationLabel(ai)?.toString() ?: ai.packageName
            } catch (_: Exception) {
                ai.packageName
            }
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            out.add(InstalledApp(ai.packageName, label, ai.uid, isSystem))
        }
        out.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }

    /** Package names only, for the VPN service cache (cheapest possible scan). */
    fun packageNames(): Set<String> {
        return try {
            val pm: PackageManager = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
