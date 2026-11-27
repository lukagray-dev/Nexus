package com.nexus.child.update

import android.os.Build
import android.util.Log
import com.nexus.child.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class ChildGitHubAssetDto(
    val name: String,
    val downloadUrl: String
)

data class ChildGitHubReleaseDto(
    val tagName: String,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<ChildGitHubAssetDto>
)

data class ParsedChildSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isPrerelease: Boolean
) : Comparable<ParsedChildSemVer> {
    override fun compareTo(other: ParsedChildSemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            isPrerelease == other.isPrerelease -> 0
            !isPrerelease && other.isPrerelease -> 1
            else -> -1
        }
    }
}

data class ChildUpdateStatus(
    val currentVersion: String,
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val deviceAbi: String? = null,
    val recommendedAssetName: String? = null,
    val recommendedAssetUrl: String? = null,
    val checkedAtMs: Long = 0L
) {
    fun toDataChannelJson(): String {
        return JSONObject().apply {
            put("type", ChildUpdateProtocol.PACKET_CHILD_UPDATE_STATUS)
            put("updateAvailable", updateAvailable)
            put("currentVersion", currentVersion)
            if (latestVersion != null) put("latestVersion", latestVersion)
            if (releaseUrl != null) put("releaseUrl", releaseUrl)
            if (deviceAbi != null) put("deviceAbi", deviceAbi)
            if (recommendedAssetName != null) put("recommendedAssetName", recommendedAssetName)
            if (recommendedAssetUrl != null) put("recommendedAssetUrl", recommendedAssetUrl)
        }.toString()
    }
}

/**
 * ChildUpdateProtocol - Pure JVM-testable GitHub stable release parser and SemVer comparator.
 *
 * Hey friend! Unlike Parent Android, Nexus Child strictly checks metadata JSON to detect
 * newer compatible `nexus-child-*.apk` releases and notifies the Parent dashboard over DataChannel —
 * it NEVER downloads or runs an interactive installer silently on the child phone so active
 * background surveillance is never interrupted.
 */
object ChildUpdateProtocol {

    const val GITHUB_OWNER = "lukagray-dev"
    const val GITHUB_REPO = "Nexus"
    const val GITHUB_RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=10"

    const val PACKET_CHILD_UPDATE_STATUS = "CHILD_UPDATE_STATUS"
    const val STARTUP_CHECK_DELAY_MS = 5_000L
    const val PERIODIC_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    const val CONNECT_STALE_MS = 60L * 60L * 1000L

    private val KNOWN_ABI_TOKENS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    fun parseAppSemVer(raw: String): ParsedChildSemVer? {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null
        val dashIdx = trimmed.indexOf('-')
        val (core, isPre) = if (dashIdx >= 0) {
            trimmed.substring(0, dashIdx) to trimmed.substring(dashIdx + 1).trim().isNotEmpty()
        } else {
            trimmed to false
        }
        val parts = core.split('.')
        if (parts.size != 3) return null
        val maj = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        val pat = parts[2].toIntOrNull() ?: return null
        if (maj < 0 || min < 0 || pat < 0) return null
        return ParsedChildSemVer(maj, min, pat, isPre)
    }

    fun parseStableTag(tag: String): ParsedChildSemVer? {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty() || trimmed.contains('-') || trimmed.contains('+')) return null
        val parts = trimmed.split('.')
        if (parts.size != 3) return null
        val maj = parts[0].toIntOrNull() ?: return null
        val min = parts[1].toIntOrNull() ?: return null
        val pat = parts[2].toIntOrNull() ?: return null
        if (maj < 0 || min < 0 || pat < 0) return null
        return ParsedChildSemVer(maj, min, pat, isPrerelease = false)
    }

    fun isNewerStableVersion(currentRaw: String, candidateTag: String): Boolean {
        val current = parseAppSemVer(currentRaw) ?: return false
        val candidate = parseStableTag(candidateTag) ?: return false
        return candidate > current
    }

    fun parseReleasesJson(rawJson: String): List<ChildGitHubReleaseDto> {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = JSONArray(trimmed)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val tagName = obj.optString("tag_name").trim()
                if (tagName.isEmpty()) continue
                val htmlUrl = obj.optString("html_url").trim()
                val isDraft = obj.optBoolean("draft", false)
                val isPrerelease = obj.optBoolean("prerelease", false)
                val assetsArr = obj.optJSONArray("assets")
                val assets = buildList {
                    if (assetsArr != null) {
                        for (j in 0 until assetsArr.length()) {
                            val assetObj = assetsArr.optJSONObject(j) ?: continue
                            val name = assetObj.optString("name").trim()
                            val url = assetObj.optString("browser_download_url").trim()
                            if (name.isNotEmpty() && url.isNotEmpty()) {
                                add(ChildGitHubAssetDto(name, url))
                            }
                        }
                    }
                }
                add(ChildGitHubReleaseDto(tagName, htmlUrl, isDraft, isPrerelease, assets))
            }
        }
    }

    fun selectLatestStableRelease(releases: List<ChildGitHubReleaseDto>): ChildGitHubReleaseDto? {
        return releases
            .asSequence()
            .filter { !it.isDraft && !it.isPrerelease }
            .mapNotNull { rel -> parseStableTag(rel.tagName)?.let { ver -> ver to rel } }
            .maxByOrNull { (ver, _) -> ver }
            ?.second
    }

    /**
     * Picks the best matching `nexus-child-*.apk` asset for `supportedAbis`,
     * falling back to `-universal.apk` or a legacy un-suffixed APK.
     */
    fun selectChildAndroidAsset(
        release: ChildGitHubReleaseDto,
        supportedAbis: List<String> = emptyList()
    ): ChildGitHubAssetDto? {
        val candidates = release.assets.filter { asset ->
            val lower = asset.name.lowercase(Locale.US)
            lower.endsWith(".apk") && lower.contains("nexus-child") && !lower.contains("nexus-parent")
        }
        if (candidates.isEmpty()) return null

        for (rawAbi in supportedAbis) {
            val abi = rawAbi.trim().lowercase(Locale.US)
            if (abi.isEmpty()) continue
            val match = candidates.firstOrNull { asset ->
                asset.name.lowercase(Locale.US).endsWith("-$abi.apk")
            }
            if (match != null) return match
        }

        val universal = candidates.firstOrNull { asset ->
            asset.name.lowercase(Locale.US).endsWith("-universal.apk")
        }
        if (universal != null) return universal

        val legacy = candidates.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.US)
            KNOWN_ABI_TOKENS.none { abi -> lower.endsWith("-$abi.apk") }
        }
        if (legacy != null) return legacy

        return if (supportedAbis.isEmpty()) candidates.firstOrNull() else null
    }

    fun hasChildApkAsset(
        release: ChildGitHubReleaseDto,
        supportedAbis: List<String> = emptyList()
    ): Boolean = selectChildAndroidAsset(release, supportedAbis) != null

    fun evaluateReleases(
        releases: List<ChildGitHubReleaseDto>,
        currentVersion: String,
        nowMs: Long,
        supportedAbis: List<String> = emptyList()
    ): ChildUpdateStatus {
        val latest = selectLatestStableRelease(releases)
        val matchedAsset = latest?.let { selectChildAndroidAsset(it, supportedAbis) }
        if (latest == null ||
            !isNewerStableVersion(currentVersion, latest.tagName) ||
            matchedAsset == null
        ) {
            return ChildUpdateStatus(
                currentVersion = currentVersion,
                updateAvailable = false,
                latestVersion = null,
                releaseUrl = null,
                deviceAbi = supportedAbis.firstOrNull(),
                recommendedAssetName = null,
                recommendedAssetUrl = null,
                checkedAtMs = nowMs
            )
        }
        val cleanVersion = latest.tagName.trim().removePrefix("v").removePrefix("V")
        return ChildUpdateStatus(
            currentVersion = currentVersion,
            updateAvailable = true,
            latestVersion = cleanVersion,
            releaseUrl = latest.htmlUrl,
            deviceAbi = supportedAbis.firstOrNull(),
            recommendedAssetName = matchedAsset.name,
            recommendedAssetUrl = matchedAsset.downloadUrl,
            checkedAtMs = nowMs
        )
    }
}

/**
 * ChildUpdateChecker - Metadata-only GitHub release check scheduler for Nexus Child.
 */
class ChildUpdateChecker(
    private val scope: CoroutineScope,
    private val sendToParent: (String) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "ChildUpdateChecker"
        private const val TIMEOUT_MS = 15_000
    }

    @Volatile
    private var latestStatus: ChildUpdateStatus = ChildUpdateStatus(
        currentVersion = BuildConfig.VERSION_NAME
    )

    private var schedulerJob: Job? = null
    private var checkJob: Job? = null

    fun start() {
        if (schedulerJob?.isActive == true) return
        schedulerJob = scope.launch {
            delay(ChildUpdateProtocol.STARTUP_CHECK_DELAY_MS)
            while (isActive) {
                runCatching { checkNowAndNotify() }
                delay(ChildUpdateProtocol.PERIODIC_CHECK_INTERVAL_MS)
            }
        }
    }

    fun onDataChannelOpened() {
        val current = latestStatus
        // Send cached knowledge immediately (also clears stale Parent flags if up to date)
        if (current.checkedAtMs > 0L) {
            sendToParent(current.toDataChannelJson())
        }
        val now = System.currentTimeMillis()
        val isStale = current.checkedAtMs == 0L ||
            (now - current.checkedAtMs) >= ChildUpdateProtocol.CONNECT_STALE_MS
        if (isStale) {
            launchAsyncCheck()
        }
    }

    fun launchAsyncCheck() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            runCatching { checkNowAndNotify() }
        }
    }

    fun stop() {
        schedulerJob?.cancel()
        schedulerJob = null
        checkJob?.cancel()
        checkJob = null
    }

    private suspend fun checkNowAndNotify() {
        val currentVersion = BuildConfig.VERSION_NAME
        val resolved = withContext(ioDispatcher) {
            try {
                val raw = fetchReleasesJson(currentVersion)
                val releases = ChildUpdateProtocol.parseReleasesJson(raw)
                ChildUpdateProtocol.evaluateReleases(
                    releases = releases,
                    currentVersion = currentVersion,
                    nowMs = System.currentTimeMillis(),
                    supportedAbis = Build.SUPPORTED_ABIS.toList()
                )
            } catch (err: Exception) {
                Log.w(TAG, "Child update check failed: ${err.message}")
                null
            }
        } ?: return

        latestStatus = resolved
        if (resolved.updateAvailable) {
            Log.i(TAG, "Child update v${resolved.latestVersion} detected (current v$currentVersion)")
        }
        sendToParent(resolved.toDataChannelJson())
    }

    private fun fetchReleasesJson(currentVersion: String): String {
        val conn = (URL(ChildUpdateProtocol.GITHUB_RELEASES_API_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Nexus-Child-Android/$currentVersion")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub API returned HTTP $code")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
