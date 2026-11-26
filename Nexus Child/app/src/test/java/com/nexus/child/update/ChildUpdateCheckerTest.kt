package com.nexus.child.update

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ChildUpdateCheckerTest.kt - Verifies GitHub release filtering, SemVer comparison,
 * `nexus-child-*.apk` asset matching, and DataChannel notification JSON formatting.
 */
class ChildUpdateCheckerTest {

    @Test
    fun `detects newer stable child apk release while ignoring prereleases, drafts, and parent-only releases`() {
        val rawJson = """
            [
              {
                "tag_name": "v9.9.9",
                "html_url": "https://example.com/draft",
                "draft": true,
                "prerelease": false,
                "assets": [
                  {"name": "nexus-child-v9.9.9.apk", "browser_download_url": "https://example.com/d.apk"}
                ]
              },
              {
                "tag_name": "v0.2.0-alpha.1",
                "html_url": "https://example.com/pre",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {"name": "nexus-child-v0.2.0-alpha.1.apk", "browser_download_url": "https://example.com/p.apk"}
                ]
              },
              {
                "tag_name": "v0.1.0",
                "html_url": "https://github.com/lukagray-dev/Nexus/releases/tag/v0.1.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {"name": "nexus-parent-v0.1.0-windows-x86_64.exe", "browser_download_url": "https://example.com/p.exe"},
                  {"name": "nexus-child-v0.1.0.apk", "browser_download_url": "https://example.com/nexus-child-v0.1.0.apk"}
                ]
              }
            ]
        """.trimIndent()

        val releases = ChildUpdateProtocol.parseReleasesJson(rawJson)
        val status = ChildUpdateProtocol.evaluateReleases(releases, currentVersion = "0.1.0-alpha", nowMs = 1000L)

        assertTrue(status.updateAvailable)
        assertEquals("0.1.0", status.latestVersion)
        assertEquals("https://github.com/lukagray-dev/Nexus/releases/tag/v0.1.0", status.releaseUrl)

        val packet = JSONObject(status.toDataChannelJson())
        assertEquals(ChildUpdateProtocol.PACKET_CHILD_UPDATE_STATUS, packet.getString("type"))
        assertTrue(packet.getBoolean("updateAvailable"))
        assertEquals("0.1.0-alpha", packet.getString("currentVersion"))
        assertEquals("0.1.0", packet.getString("latestVersion"))

        // Parent-only release without nexus-child-*.apk must NOT claim child update is available
        val parentOnly = ChildUpdateProtocol.evaluateReleases(
            releases = listOf(
                ChildGitHubReleaseDto(
                    tagName = "v0.2.0",
                    htmlUrl = "https://example.com/v020",
                    isDraft = false,
                    isPrerelease = false,
                    assets = listOf(ChildGitHubAssetDto("nexus-parent-v0.2.0.apk", "https://example.com/pa.apk"))
                )
            ),
            currentVersion = "0.1.0",
            nowMs = 2000L
        )
        assertFalse(parentOnly.updateAvailable)
        assertNull(parentOnly.latestVersion)

        // Already running v0.1.0 -> not available
        val upToDate = ChildUpdateProtocol.evaluateReleases(releases, currentVersion = "0.1.0", nowMs = 3000L)
        assertFalse(upToDate.updateAvailable)
    }
}
