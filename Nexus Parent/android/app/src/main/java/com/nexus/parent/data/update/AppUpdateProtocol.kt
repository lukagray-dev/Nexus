package com.nexus.parent.data.update

import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.AppUpdateUiState
import org.json.JSONArray
import java.security.MessageDigest
import java.util.Locale

data class GitHubReleaseAssetDto(
    val name: String,
    val downloadUrl: String
)

data class GitHubReleaseDto(
    val tagName: String,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<GitHubReleaseAssetDto>
)

data class ParsedAppSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val isPrerelease: Boolean
) : Comparable<ParsedAppSemVer> {
    override fun compareTo(other: ParsedAppSemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // Stable (isPrerelease=false) outranks prerelease (isPrerelease=true) for identical core numbers
        return when {
            isPrerelease == other.isPrerelease -> 0
            !isPrerelease && other.isPrerelease -> 1
            else -> -1
        }
    }
}

/**
 * AppUpdateProtocol.kt - Pure Kotlin release selection, SemVer comparison, and integrity helpers
 * mirroring Desktop `src-tauri/src/updater.rs` & `auto-update.ts`.
 *
 * Hey friend! Keeping all JSON parsing, SemVer comparisons, APK asset matching (`nexus-parent-*.apk`,
 * never `nexus-child-*.apk`), and SHA-256 verification in pure Kotlin lets us verify every edge case
 * deterministically in fast JVM unit tests!
 */
object AppUpdateProtocol {

    const val GITHUB_OWNER = "lukagray-dev"
    const val GITHUB_REPO = "Nexus"
    const val GITHUB_RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=10"
    const val REPOSITORY_RELEASES_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"

    const val STARTUP_CHECK_DELAY_MS = 4_000L
    const val PERIODIC_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

    fun formatUpdateStatusBadge(state: AppUpdateUiState): String {
        return when (state.status) {
            AppUpdateStatusKind.IDLE -> "Ready"
            AppUpdateStatusKind.CHECKING -> "Checking..."
            AppUpdateStatusKind.DOWNLOADING -> if (state.downloadProgressPercent > 0) {
                "Downloading ${state.downloadProgressPercent}%"
            } else {
                "Downloading..."
            }
            AppUpdateStatusKind.READY -> state.availableVersion?.let { "v$it ready" } ?: "Update ready"
            AppUpdateStatusKind.UP_TO_DATE -> "v${state.currentVersion} (Latest)"
            AppUpdateStatusKind.ERROR -> "Check failed"
        }
    }

    fun parseAppSemVer(raw: String): ParsedAppSemVer? {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty()) return null

        val dashIndex = trimmed.indexOf('-')
        val (corePart, isPrerelease) = if (dashIndex >= 0) {
            val pre = trimmed.substring(dashIndex + 1).trim()
            trimmed.substring(0, dashIndex) to pre.isNotEmpty()
        } else {
            trimmed to false
        }

        val parts = corePart.split('.')
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        if (major < 0 || minor < 0 || patch < 0) return null

        return ParsedAppSemVer(major = major, minor = minor, patch = patch, isPrerelease = isPrerelease)
    }

    fun parseStableTag(tag: String): ParsedAppSemVer? {
        val trimmed = tag.trim().removePrefix("v").removePrefix("V")
        if (trimmed.isEmpty() || trimmed.contains('-') || trimmed.contains('+')) return null

        val parts = trimmed.split('.')
        if (parts.size != 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts[2].toIntOrNull() ?: return null
        if (major < 0 || minor < 0 || patch < 0) return null

        return ParsedAppSemVer(major = major, minor = minor, patch = patch, isPrerelease = false)
    }

    fun isNewerStableVersion(currentRaw: String, candidateTag: String): Boolean {
        val current = parseAppSemVer(currentRaw) ?: return false
        val candidate = parseStableTag(candidateTag) ?: return false
        return candidate > current
    }

    fun parseReleasesJson(rawJson: String): List<GitHubReleaseDto> {
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
                                add(GitHubReleaseAssetDto(name = name, downloadUrl = url))
                            }
                        }
                    }
                }
                add(
                    GitHubReleaseDto(
                        tagName = tagName,
                        htmlUrl = htmlUrl,
                        isDraft = isDraft,
                        isPrerelease = isPrerelease,
                        assets = assets
                    )
                )
            }
        }
    }

    fun selectLatestStableRelease(releases: List<GitHubReleaseDto>): GitHubReleaseDto? {
        return releases
            .asSequence()
            .filter { !it.isDraft && !it.isPrerelease }
            .mapNotNull { release ->
                parseStableTag(release.tagName)?.let { ver -> ver to release }
            }
            .maxByOrNull { (ver, _) -> ver }
            ?.second
    }

    /**
     * Picks the Nexus Parent Android APK (`nexus-parent-*.apk`, never `nexus-child-*.apk`)
     * plus its optional matching `.sha256` checksum asset.
     */
    fun selectParentAndroidAssets(
        release: GitHubReleaseDto
    ): Pair<GitHubReleaseAssetDto, GitHubReleaseAssetDto?>? {
        val apkAsset = release.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.US)
            lower.endsWith(".apk") && lower.contains("nexus-parent") && !lower.contains("nexus-child")
        } ?: return null

        val exactShaName = "${apkAsset.name}.sha256".lowercase(Locale.US)
        val shaAsset = release.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.US)
            lower == exactShaName ||
                (lower.endsWith(".apk.sha256") && lower.contains("nexus-parent"))
        }

        return apkAsset to shaAsset
    }

    fun parseSha256Text(raw: String): String? {
        val firstToken = raw.trim().split(Regex("\\s+")).firstOrNull()?.lowercase(Locale.US) ?: return null
        if (firstToken.length != 64 || !firstToken.all { it in '0'..'9' || it in 'a'..'f' }) {
            return null
        }
        return firstToken
    }

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(Locale.US, it) }
    }

    /**
     * Verifies minimum ZIP/APK local file header magic bytes (`PK\u0003\u0004`).
     */
    fun verifyApkMagicHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() &&
            bytes[3] == 0x04.toByte()
    }
}
