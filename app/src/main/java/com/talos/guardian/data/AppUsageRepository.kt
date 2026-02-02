package com.talos.guardian.data

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import java.util.Calendar

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val totalTimeInForeground: Long
)

class AppUsageRepository(private val context: Context) {

    fun getWeeklyUsage(): List<AppUsageItem> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startTime = calendar.timeInMillis

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_WEEKLY,
            startTime,
            endTime
        )

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return emptyList()
        }

        val pm = context.packageManager
        val appUsageMap = mutableMapOf<String, AppUsageItem>()

        for (usageStats in usageStatsList) {
            if (usageStats.totalTimeInForeground > 0) {
                val packageName = usageStats.packageName
                
                // Filter out common system apps to reduce noise
                if (isSystemApp(packageName)) continue

                val existing = appUsageMap[packageName]
                if (existing == null) {
                    val appName = try {
                        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
                    } catch (e: PackageManager.NameNotFoundException) {
                        packageName
                    }
                    appUsageMap[packageName] = AppUsageItem(packageName, appName, usageStats.totalTimeInForeground)
                } else {
                    // Aggregate if multiple entries exist (rare for INTERVAL_WEEKLY but possible)
                    appUsageMap[packageName] = existing.copy(
                        totalTimeInForeground = existing.totalTimeInForeground + usageStats.totalTimeInForeground
                    )
                }
            }
        }

        return appUsageMap.values.sortedByDescending { it.totalTimeInForeground }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return packageName.startsWith("com.android") || 
               packageName.startsWith("com.google.android") ||
               packageName == "android"
    }
}
