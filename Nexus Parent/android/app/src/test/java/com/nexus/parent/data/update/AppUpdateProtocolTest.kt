package com.nexus.parent.data.update

import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.AppUpdateUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AppUpdateProtocolTest.kt - Verifies GitHub release filtering, SemVer comparison,
 * `nexus-parent-*.apk` asset matching, SHA-256 verification, and APK magic header validation.
 */
class AppUpdateProtocolTest {

    @Test
    fun `parses semver and upgrades cleanly from alpha to stable while ignoring older or prerelease`() {
        assertEquals(ParsedAppSemVer(0, 1, 0, isPrerelease = true), AppUpdateProtocol.parseAppSemVer("0.1.0-alpha"))
        assertEquals(ParsedAppSemVer(1, 4, 2, isPrerelease = false), AppUpdateProtocol.parseAppSemVer("v1.4.2"))
        assertNull(AppUpdateProtocol.parseAppSemVer("invalid"))

        assertEquals(ParsedAppSemVer(0, 1, 0, isPrerelease = false), AppUpdateProtocol.parseStableTag("v0.1.0"))
        assertNull(AppUpdateProtocol.parseStableTag("v0.1.0-alpha"))
        assertNull(AppUpdateProtocol.parseStableTag("v0.1.0-alpha.2"))

        assertTrue(AppUpdateProtocol.isNewerStableVersion("0.1.0-alpha", "v0.1.0"))
        assertTrue(AppUpdateProtocol.isNewerStableVersion("0.1.0", "v0.1.1"))
        assertTrue(AppUpdateProtocol.isNewerStableVersion("0.9.9", "v1.0.0"))

        assertFalse(AppUpdateProtocol.isNewerStableVersion("0.1.0", "v0.1.0"))
        assertFalse(AppUpdateProtocol.isNewerStableVersion("0.2.0", "v0.1.9"))
        assertFalse(AppUpdateProtocol.isNewerStableVersion("0.1.0", "v0.2.0-alpha"))
    }

    @Test
    fun `parses GitHub releases json, picks highest stable release, and matches parent apk asset`() {
        val rawJson = """
            [
              {
                "tag_name": "v9.9.9",
                "html_url": "https://github.com/lukagray-dev/Nexus/releases/tag/v9.9.9",
                "draft": true,
                "prerelease": false,
                "assets": []
              },
              {
                "tag_name": "v0.3.0-alpha.1",
                "html_url": "https://github.com/lukagray-dev/Nexus/releases/tag/v0.3.0-alpha.1",
                "draft": false,
                "prerelease": true,
                "assets": []
              },
              {
                "tag_name": "v0.2.0",
                "html_url": "https://github.com/lukagray-dev/Nexus/releases/tag/v0.2.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "nexus-child-v0.2.0.apk",
                    "browser_download_url": "https://example.com/nexus-child-v0.2.0.apk"
                  },
                  {
                    "name": "nexus-parent-v0.2.0-windows-x86_64.exe",
                    "browser_download_url": "https://example.com/nexus-parent-v0.2.0-windows-x86_64.exe"
                  },
                  {
                    "name": "nexus-parent-v0.2.0.apk",
                    "browser_download_url": "https://example.com/nexus-parent-v0.2.0.apk"
                  },
                  {
                    "name": "nexus-parent-v0.2.0.apk.sha256",
                    "browser_download_url": "https://example.com/nexus-parent-v0.2.0.apk.sha256"
                  }
                ]
              }
            ]
        """.trimIndent()

        val releases = AppUpdateProtocol.parseReleasesJson(rawJson)
        assertEquals(3, releases.size)

        val latest = AppUpdateProtocol.selectLatestStableRelease(releases)
        assertNotNull(latest)
        assertEquals("v0.2.0", latest?.tagName)

        val selected = AppUpdateProtocol.selectParentAndroidAssets(latest!!)
        assertNotNull(selected)
        assertEquals("nexus-parent-v0.2.0.apk", selected?.first?.name)
        assertEquals("nexus-parent-v0.2.0.apk.sha256", selected?.second?.name)

        // Child-only release should never match parent APK
        val childOnly = GitHubReleaseDto(
            tagName = "v0.2.1",
            htmlUrl = "https://example.com/v021",
            isDraft = false,
            isPrerelease = false,
            assets = listOf(GitHubReleaseAssetDto("nexus-child-v0.2.1.apk", "https://example.com/c.apk"))
        )
        assertNull(AppUpdateProtocol.selectParentAndroidAssets(childOnly))
    }

    @Test
    fun `matches exact supported ABI APK in priority order, falls back to universal, and requires strict sha256`() {
        val multiAbiRelease = GitHubReleaseDto(
            tagName = "v0.2.0",
            htmlUrl = "https://example.com/v020",
            isDraft = false,
            isPrerelease = false,
            assets = listOf(
                GitHubReleaseAssetDto("nexus-child-v0.2.0-arm64-v8a.apk", "https://example.com/c-arm64.apk"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-arm64-v8a.apk", "https://example.com/p-arm64.apk"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-arm64-v8a.apk.sha256", "https://example.com/p-arm64.apk.sha256"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-armeabi-v7a.apk", "https://example.com/p-v7a.apk"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-x86_64.apk", "https://example.com/p-x64.apk"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-x86_64.apk.sha256", "https://example.com/p-x64.apk.sha256"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-universal.apk", "https://example.com/p-uni.apk"),
                GitHubReleaseAssetDto("nexus-parent-v0.2.0-universal.apk.sha256", "https://example.com/p-uni.apk.sha256")
            )
        )

        val arm64 = AppUpdateProtocol.selectParentAndroidAssets(multiAbiRelease, listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals("nexus-parent-v0.2.0-arm64-v8a.apk", arm64?.first?.name)
        assertEquals("nexus-parent-v0.2.0-arm64-v8a.apk.sha256", arm64?.second?.name)

        // armeabi-v7a has no .sha256 in fixture -> must NOT mismatch arm64's .sha256
        val v7a = AppUpdateProtocol.selectParentAndroidAssets(multiAbiRelease, listOf("armeabi-v7a"))
        assertEquals("nexus-parent-v0.2.0-armeabi-v7a.apk", v7a?.first?.name)
        assertNull(v7a?.second)

        val x64 = AppUpdateProtocol.selectParentAndroidAssets(multiAbiRelease, listOf("x86_64"))
        assertEquals("nexus-parent-v0.2.0-x86_64.apk", x64?.first?.name)
        assertEquals("nexus-parent-v0.2.0-x86_64.apk.sha256", x64?.second?.name)

        // Unknown ABI falls back to universal APK
        val fallbackUni = AppUpdateProtocol.selectParentAndroidAssets(multiAbiRelease, listOf("mips64"))
        assertEquals("nexus-parent-v0.2.0-universal.apk", fallbackUni?.first?.name)
        assertEquals("nexus-parent-v0.2.0-universal.apk.sha256", fallbackUni?.second?.name)
    }

    @Test
    fun `validates apk zip magic bytes, sha256 parsing, and badge formatting`() {
        assertTrue(AppUpdateProtocol.verifyApkMagicHeader(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)))
        assertFalse(AppUpdateProtocol.verifyApkMagicHeader(byteArrayOf(0x4D, 0x5A, 0x90.toByte(), 0x00)))

        val expected = AppUpdateProtocol.computeSha256Hex("nexus-apk-bytes".toByteArray())
        assertEquals(expected, AppUpdateProtocol.parseSha256Text("${expected.uppercase()}  nexus-parent-v0.1.0.apk\n"))
        assertNull(AppUpdateProtocol.parseSha256Text("invalid-digest"))

        assertEquals("Checking...", AppUpdateProtocol.formatUpdateStatusBadge(AppUpdateUiState(status = AppUpdateStatusKind.CHECKING)))
        assertEquals("Downloading 45%", AppUpdateProtocol.formatUpdateStatusBadge(AppUpdateUiState(status = AppUpdateStatusKind.DOWNLOADING, downloadProgressPercent = 45)))
        assertEquals("v0.2.0 ready", AppUpdateProtocol.formatUpdateStatusBadge(AppUpdateUiState(status = AppUpdateStatusKind.READY, availableVersion = "0.2.0")))
        assertEquals("v0.1.0 (Latest)", AppUpdateProtocol.formatUpdateStatusBadge(AppUpdateUiState(status = AppUpdateStatusKind.UP_TO_DATE, currentVersion = "0.1.0")))
    }
}
