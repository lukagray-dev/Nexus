package com.nexus.parent.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.nexus.parent.BuildConfig
import com.nexus.parent.state.AppUpdateStatusKind
import com.nexus.parent.state.AppUpdateUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/**
 * AppUpdateManager.kt - Background GitHub Stable Release checker, SHA-256 verifier, and
 * FileProvider APK installer matching Desktop `src-tauri/src/updater.rs` & `auto-update.ts`.
 *
 * Hey friend! Here is how Android self-updates work safely:
 * 1. If `autoCheckUpdates` is enabled, checks GitHub 4s after launch and every 6 hours cleanly.
 * 2. Downloads `nexus-parent-vX.Y.Z.apk` into private `filesDir/updates/` using atomic `.part` writes.
 * 3. Validates APK ZIP header, `.sha256` checksum (when published), and Archive `packageName` match.
 * 4. Surfaces `AppUpdateStatusKind.READY` so both Settings and the top shell show `"Restart to update"`,
 *    which disconnects WebRTC cleanly and launches Android's PackageInstaller (or Unknown Sources prompt).
 */
class AppUpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "ParentAppUpdater"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
    }

    private val appContext = context.applicationContext
    val updatesDir: File = File(appContext.filesDir, "updates")

    private val _state = MutableStateFlow(
        AppUpdateUiState(
            currentVersion = BuildConfig.VERSION_NAME,
            updatesDirPath = updatesDir.absolutePath
        )
    )
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    private var stagedTag: String? = null
    private var stagedFile: File? = null
    private var schedulerJob: Job? = null
    private var activeCheckJob: Job? = null

    fun syncAutoCheckPreference(enabled: Boolean, triggerImmediateIfStartup: Boolean = false) {
        if (!enabled) {
            schedulerJob?.cancel()
            schedulerJob = null
            return
        }
        if (schedulerJob?.isActive == true) return

        schedulerJob = scope.launch {
            if (triggerImmediateIfStartup) {
                delay(AppUpdateProtocol.STARTUP_CHECK_DELAY_MS)
                if (isActive) {
                    checkForUpdates(manual = false)
                }
            }
            while (isActive) {
                delay(AppUpdateProtocol.PERIODIC_CHECK_INTERVAL_MS)
                if (isActive) {
                    checkForUpdates(manual = false)
                }
            }
        }
    }

    fun launchCheckForUpdates(
        manual: Boolean,
        onResult: ((AppUpdateUiState) -> Unit)? = null
    ) {
        val currentStatus = _state.value.status
        if (currentStatus == AppUpdateStatusKind.CHECKING || currentStatus == AppUpdateStatusKind.DOWNLOADING) {
            onResult?.invoke(_state.value)
            return
        }
        activeCheckJob?.cancel()
        activeCheckJob = scope.launch {
            val result = checkForUpdates(manual = manual)
            onResult?.invoke(result)
        }
    }

    suspend fun checkForUpdates(manual: Boolean): AppUpdateUiState {
        val currentVersion = BuildConfig.VERSION_NAME
        val existingStatus = _state.value.status
        if (existingStatus == AppUpdateStatusKind.CHECKING || existingStatus == AppUpdateStatusKind.DOWNLOADING) {
            return _state.value
        }

        _state.value = _state.value.copy(
            status = AppUpdateStatusKind.CHECKING,
            currentVersion = currentVersion,
            downloadProgressPercent = 0,
            errorMessage = null
        )

        return withContext(ioDispatcher) {
            try {
                val releasesRaw = fetchText(AppUpdateProtocol.GITHUB_RELEASES_API_URL, currentVersion)
                val releases = AppUpdateProtocol.parseReleasesJson(releasesRaw)
                val latest = AppUpdateProtocol.selectLatestStableRelease(releases)
                if (latest == null || !AppUpdateProtocol.isNewerStableVersion(currentVersion, latest.tagName)) {
                    stagedTag = null
                    stagedFile = null
                    val upToDate = _state.value.copy(
                        status = AppUpdateStatusKind.UP_TO_DATE,
                        availableVersion = null,
                        releaseUrl = null,
                        downloadProgressPercent = 0,
                        sha256Verified = false,
                        errorMessage = null
                    )
                    _state.value = upToDate
                    return@withContext upToDate
                }

                val cleanVersion = latest.tagName.trim().removePrefix("v").removePrefix("V")
                val existingFile = stagedFile
                if (stagedTag == latest.tagName && existingFile != null && existingFile.exists()) {
                    val alreadyReady = _state.value.copy(
                        status = AppUpdateStatusKind.READY,
                        availableVersion = cleanVersion,
                        releaseUrl = latest.htmlUrl,
                        downloadProgressPercent = 100,
                        errorMessage = null
                    )
                    _state.value = alreadyReady
                    return@withContext alreadyReady
                }

                val assets = AppUpdateProtocol.selectParentAndroidAssets(latest)
                if (assets == null) {
                    val msg = "Release ${latest.tagName} does not include a Nexus Parent Android APK"
                    val errState = _state.value.copy(
                        status = AppUpdateStatusKind.ERROR,
                        availableVersion = cleanVersion,
                        releaseUrl = latest.htmlUrl,
                        errorMessage = msg
                    )
                    _state.value = errState
                    return@withContext errState
                }

                val (apkAsset, shaAsset) = assets
                _state.value = _state.value.copy(
                    status = AppUpdateStatusKind.DOWNLOADING,
                    availableVersion = cleanVersion,
                    releaseUrl = latest.htmlUrl,
                    downloadProgressPercent = 0,
                    errorMessage = null
                )

                if (!updatesDir.exists()) {
                    updatesDir.mkdirs()
                }
                val partFile = File(updatesDir, "${apkAsset.name}.part")
                val finalFile = File(updatesDir, apkAsset.name)

                val downloaded = downloadFileWithProgress(apkAsset.downloadUrl, currentVersion, partFile) { pct ->
                    _state.value = _state.value.copy(downloadProgressPercent = pct)
                }

                if (!AppUpdateProtocol.verifyApkMagicHeader(downloaded.headerBytes)) {
                    partFile.delete()
                    val msg = "Downloaded update failed APK archive header verification"
                    val errState = _state.value.copy(
                        status = AppUpdateStatusKind.ERROR,
                        errorMessage = msg
                    )
                    _state.value = errState
                    return@withContext errState
                }

                var shaVerified = false
                if (shaAsset != null) {
                    val shaRaw = fetchText(shaAsset.downloadUrl, currentVersion)
                    val expectedHex = AppUpdateProtocol.parseSha256Text(shaRaw)
                    if (expectedHex != null) {
                        if (downloaded.sha256Hex != expectedHex) {
                            partFile.delete()
                            val msg = "Update checksum verification failed (SHA-256 mismatch)"
                            val errState = _state.value.copy(
                                status = AppUpdateStatusKind.ERROR,
                                errorMessage = msg
                            )
                            _state.value = errState
                            return@withContext errState
                        }
                        shaVerified = true
                    }
                }

                val parsedPackageName = readArchivePackageName(partFile)
                if (parsedPackageName != null && parsedPackageName != appContext.packageName) {
                    partFile.delete()
                    val msg = "Update package ID mismatch ($parsedPackageName)"
                    val errState = _state.value.copy(
                        status = AppUpdateStatusKind.ERROR,
                        errorMessage = msg
                    )
                    _state.value = errState
                    return@withContext errState
                }

                // Prune older staged APKs before renaming the verified release cleanly
                updatesDir.listFiles()?.forEach { candidate ->
                    if (candidate.name != partFile.name && candidate.name != finalFile.name) {
                        candidate.delete()
                    }
                }
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }

                stagedTag = latest.tagName
                stagedFile = finalFile

                val ready = _state.value.copy(
                    status = AppUpdateStatusKind.READY,
                    availableVersion = cleanVersion,
                    releaseUrl = latest.htmlUrl,
                    downloadProgressPercent = 100,
                    sha256Verified = shaVerified,
                    errorMessage = null
                )
                _state.value = ready
                ready
            } catch (err: Exception) {
                Log.w(TAG, "Update check failed (manual=$manual): ${err.message}")
                val msg = err.message?.takeIf { it.isNotBlank() } ?: "Could not reach GitHub releases"
                val errState = _state.value.copy(
                    status = AppUpdateStatusKind.ERROR,
                    downloadProgressPercent = 0,
                    errorMessage = msg
                )
                _state.value = errState
                errState
            }
        }
    }

    fun canRequestPackageInstalls(): Boolean {
        return appContext.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${appContext.packageName}".toUri()
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallApkIntent(): Intent? {
        val file = stagedFile?.takeIf { it.exists() } ?: return null
        val contentUri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun clearStagedUpdates() {
        stagedTag = null
        stagedFile = null
        runCatching {
            updatesDir.listFiles()?.forEach { it.delete() }
        }
        _state.value = AppUpdateUiState(
            currentVersion = BuildConfig.VERSION_NAME,
            updatesDirPath = updatesDir.absolutePath
        )
    }

    fun computeUpdatesCacheBytes(): Long {
        if (!updatesDir.exists()) return 0L
        return updatesDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun readArchivePackageName(apkFile: File): String? {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            }
            info?.packageName
        }.getOrNull()
    }

    private fun fetchText(urlString: String, currentVersion: String): String {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Nexus-Parent-Android/$currentVersion")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("GitHub returned HTTP $code")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private data class DownloadedApkDigest(
        val headerBytes: ByteArray,
        val sha256Hex: String
    )

    private fun downloadFileWithProgress(
        urlString: String,
        currentVersion: String,
        destPartFile: File,
        onProgress: (Int) -> Unit
    ): DownloadedApkDigest {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Nexus-Parent-Android/$currentVersion")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Update download failed (HTTP $code)")
            }
            val contentLength = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            val header = ByteArray(4)
            var headerLen = 0
            val chunk = ByteArray(16 * 1024)
            var downloaded = 0L
            var lastReportedPct = -1

            FileOutputStream(destPartFile).use { fileOut ->
                conn.inputStream.use { input ->
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        fileOut.write(chunk, 0, read)
                        digest.update(chunk, 0, read)
                        if (headerLen < 4) {
                            val toCopy = minOf(read, 4 - headerLen)
                            System.arraycopy(chunk, 0, header, headerLen, toCopy)
                            headerLen += toCopy
                        }
                        downloaded += read
                        if (contentLength > 0L) {
                            val pct = ((downloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPct && (pct % 5 == 0 || pct == 100)) {
                                lastReportedPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
            return DownloadedApkDigest(
                headerBytes = header.copyOf(headerLen),
                sha256Hex = hex
            )
        } catch (err: Exception) {
            destPartFile.delete()
            throw err
        } finally {
            conn.disconnect()
        }
    }
}
