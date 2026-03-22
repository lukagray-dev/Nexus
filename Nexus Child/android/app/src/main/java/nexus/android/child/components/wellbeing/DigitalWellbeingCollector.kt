package nexus.android.child.components.wellbeing

import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.createBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Collects digital wellbeing data including screen time, app usage, and device statistics
 */
class DigitalWellbeingCollector(context: Context) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val packageManager = context.packageManager

    companion object {
        private const val TAG = "DigitalWellbeing"
    }

    /**
     * Get comprehensive wellbeing data for the last 24 hours
     */
    fun getWellbeingData(): JSONObject {
        val data = JSONObject()
        data.put("type", "WELLBEING_DATA")
        data.put("timestamp", System.currentTimeMillis())

        try {

            // Get time range (last 24 hours)
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            // Get usage stats
            val usageStats = usageStatsManager?.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            ) ?: emptyList()

            // Calculate total screen time
            var totalScreenTime = 0L
            val appsList = mutableListOf<JSONObject>()
            val categoriesMap = mutableMapOf<String, Long>()

            for (stat in usageStats) {
                if (stat.packageName == "android" || stat.packageName.isEmpty()) continue

                val foregroundTime = stat.totalTimeInForeground
                totalScreenTime += foregroundTime

                if (foregroundTime > 0) {
                    val appData = JSONObject()
                    appData.put("package_name", stat.packageName)
                    appData.put("app_name", getAppName(stat.packageName))
                    appData.put("screen_time_ms", foregroundTime)
                    appData.put("launch_count", stat.lastTimeUsed - stat.firstTimeStamp) // Approximate
                    appData.put("last_used_ms", stat.lastTimeUsed)

                    // Try to get app icon
                    try {
                        val icon = getAppIcon(stat.packageName)
                        if (icon != null) {
                            appData.put("icon_base64", drawableToBase64(icon))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to get icon for ${stat.packageName}", e)
                    }

                    // Categorize app
                    val category = categorizeApp(stat.packageName)
                    appData.put("category", category)
                    categoriesMap[category] = (categoriesMap[category] ?: 0L) + foregroundTime

                    appsList.add(appData)
                }
            }

            // Sort apps by screen time
            appsList.sortByDescending { it.getLong("screen_time_ms") }

            // Build categories array
            val categoriesArray = JSONArray()
            for ((category, time) in categoriesMap.toList().sortedByDescending { it.second }) {
                val catObj = JSONObject()
                catObj.put("category", category)
                catObj.put("screen_time_ms", time)
                categoriesArray.put(catObj)
            }

            // Get device unlock count (approximate from usage stats)
            val deviceUnlocks = getApproximateUnlocks(usageStats)

            // Build response
            data.put("total_screen_time_ms", totalScreenTime)
            data.put("device_unlocks", deviceUnlocks)
            data.put("apps", appsList)
            data.put("categories", categoriesArray)
            data.put("period_start_ms", startTime)
            data.put("period_end_ms", endTime)

            // Add notifications count
            data.put("notifications_count", getNotificationCount())

            Log.d(TAG, "Wellbeing data collected: ${totalScreenTime}ms screen time, ${appsList.size} apps")

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: PACKAGE_USAGE_STATS permission not granted. User must enable 'Permit usage access' in Settings > Apps > [App Name]", e)
            data.put("error", "PERMISSION_DENIED")
            data.put("reason", "PACKAGE_USAGE_STATS permission not granted. Enable 'Permit usage access' in Settings")
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting wellbeing data", e)
            data.put("error", e.javaClass.simpleName)
            data.put("reason", e.message)
        }

        return data
    }

    /**
     * Get app name from package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    /**
     * Get app icon drawable
     */
    private fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Convert drawable to base64 string
     */
    private fun drawableToBase64(drawable: Drawable): String {
        return try {
            val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            val outputStream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            val imageBytes = outputStream.toByteArray()
            android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert drawable to base64", e)
            ""
        }
    }

    /**
     * Categorize app based on package name
     */
    private fun categorizeApp(packageName: String): String {
        return when {
            // Social Media
            packageName.contains("facebook") || packageName.contains("instagram") ||
            packageName.contains("twitter") || packageName.contains("whatsapp") ||
            packageName.contains("telegram") || packageName.contains("snapchat") -> "Social"

            // Entertainment
            packageName.contains("youtube") || packageName.contains("netflix") ||
            packageName.contains("prime") || packageName.contains("spotify") ||
            packageName.contains("music") -> "Entertainment"

            // Games
            packageName.contains("game") || packageName.contains("gaming") -> "Games"

            // Productivity
            packageName.contains("office") || packageName.contains("docs") ||
            packageName.contains("sheets") || packageName.contains("drive") ||
            packageName.contains("notion") -> "Productivity"

            // Communication
            packageName.contains("mail") || packageName.contains("gmail") ||
            packageName.contains("outlook") -> "Communication"

            // Shopping
            packageName.contains("amazon") || packageName.contains("ebay") ||
            packageName.contains("shop") -> "Shopping"

            // News
            packageName.contains("news") || packageName.contains("reddit") -> "News"

            // Health
            packageName.contains("health") || packageName.contains("fitness") ||
            packageName.contains("workout") -> "Health"

            // Education
            packageName.contains("edu") || packageName.contains("learn") ||
            packageName.contains("course") -> "Education"

            else -> "Other"
        }
    }

    /**
     * Get approximate device unlock count
     */
    private fun getApproximateUnlocks(usageStats: List<android.app.usage.UsageStats>): Int {
        // This is an approximation based on the number of unique apps used
        // A more accurate method would require additional permissions
        return usageStats.filter { it.totalTimeInForeground > 0 }.size
    }

    /**
     * Get notification count (requires NotificationListener permission)
     */
    private fun getNotificationCount(): Int {
        // This would require NotificationListenerService
        // For now, return 0 as placeholder
        return 0
    }

}
