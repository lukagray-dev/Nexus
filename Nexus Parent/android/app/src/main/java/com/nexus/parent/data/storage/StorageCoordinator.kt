package com.nexus.parent.data.storage

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.nexus.parent.state.ParentStateHolder
import com.nexus.parent.state.StorageFileItem
import com.nexus.parent.state.StorageTransferProgress
import com.nexus.parent.state.StorageUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

data class PendingStorageDownloadRequest(
    val fileName: String,
    val mimeType: String
)

/**
 * StorageCoordinator.kt - Remote Storage Explorer & File Transfer coordinator matching Desktop
 * `storage.ts`, `state.ts`, and `download-manager.ts`.
 *
 * Hey friend! Here is how this engine keeps browsing snappy and downloads crash-free:
 * 1. Directory Cache + Smart Invalidation (`STORAGE_CHECK_DIR`): Revisiting any folder renders
 *    cached items immediately in 0ms while Child validates modified timestamp & item count.
 * 2. Progressive 40-Item Pagination (`Load More Files`): Only 40 thumbnails decode at a time.
 * 3. Image Preview Cache: Full-resolution previewed images are saved to local preview cache so
 *    reopening or saving a previewed photo never transfers chunks twice.
 * 4. Sequential O(1)-RAM Transfer Writer: Incoming 64KB Base64 chunks write immediately to
 *    `.nexuspart` via a serialized IO dispatcher, supporting SAF picker (`askBeforeDownload`) or
 *    automatic `Downloads/NexusParent/` saving.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageCoordinator(
    context: Context?,
    private val scope: CoroutineScope,
    private val sendCommand: (String) -> Boolean,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheRootDir: File? = null,
    base64Decoder: (String) -> ByteArray = { Base64.decode(it, Base64.DEFAULT) }
) {

    companion object {
        private const val TAG = "StorageCoordinator"
    }

    private data class DirectoryCacheEntry(
        val path: String,
        val items: List<StorageFileItem>,
        val totalCount: Int,
        val hasMorePages: Boolean,
        val nextOffset: Int,
        val dirModified: Long
    )

    private val fileSaver = StorageFileSaver(
        context = context,
        cacheRootDir = cacheRootDir ?: context?.cacheDir
            ?: File(System.getProperty("java.io.tmpdir"), "nexus_parent_storage_cache"),
        ioDispatcher = ioDispatcher,
        base64Decoder = base64Decoder
    )

    // Serializes all chunk writes & file finalization so out-of-order IO completion never races
    private val transferIoDispatcher = ioDispatcher.limitedParallelism(1)

    private var stateHolder: ParentStateHolder? = null
    private val directoryCache = HashMap<String, DirectoryCacheEntry>()
    private val imagePreviewFileCache = HashMap<String, File>()
    private val activeWriters = HashMap<String, StorageFileSaver.StagedTransferWriter>()
    private val pendingDownloadUris = HashMap<String, ArrayDeque<Uri>>()

    private var pendingPickerItem: StorageFileItem? = null
    private var pendingPickerCachedPreview: Pair<StorageFileItem, File>? = null
    private var nextOffset: Int = 0
    private var currentDirModified: Long = 0L

    private val _pendingDownloadRequests = MutableSharedFlow<PendingStorageDownloadRequest>(extraBufferCapacity = 2)
    val pendingDownloadRequests: SharedFlow<PendingStorageDownloadRequest> = _pendingDownloadRequests.asSharedFlow()

    fun attachStateHolder(holder: ParentStateHolder) {
        stateHolder = holder
    }

    fun syncTelemetryGauge(freeBytes: Long, totalBytes: Long) {
        val holder = stateHolder ?: return
        val (label, fraction) = StorageProtocol.formatStorageCapacityStrip(freeBytes, totalBytes)
        holder.updateStorageState {
            it.copy(storageUsageText = label, storageUsedPercent = fraction)
        }
    }

    // -------------------------------------------------------------------------
    // DIRECTORY NAVIGATION, CACHING & PAGINATION
    // -------------------------------------------------------------------------

    fun openStorageMonitor() {
        val holder = stateHolder ?: return
        holder.uiState.value.connectedDevice?.telemetry?.let { t ->
            syncTelemetryGauge(t.freeStorageBytes, t.totalStorageBytes)
        }
        val path = holder.uiState.value.storage.currentPath.ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        navigateTo(path, forceFresh = false)
    }

    fun refreshCurrentDirectory() {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.storage.currentPath.ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        directoryCache.remove(current)
        navigateTo(current, forceFresh = true)
    }

    fun navigateUp() {
        val holder = stateHolder ?: return
        val current = holder.uiState.value.storage.currentPath
        val parent = StorageProtocol.computeParentPath(current) ?: return
        navigateTo(parent, forceFresh = false)
    }

    fun navigateToBreadcrumb(index: Int) {
        val holder = stateHolder ?: return
        val crumbs = holder.uiState.value.storage.breadcrumbs
        val target = crumbs.getOrNull(index) ?: return
        if (target.fullPath == holder.uiState.value.storage.currentPath) return
        navigateTo(target.fullPath, forceFresh = false)
    }

    fun openDirectory(item: StorageFileItem) {
        if (!item.isDirectory) return
        navigateTo(item.path, forceFresh = false)
    }

    fun navigateTo(path: String, forceFresh: Boolean = false) {
        val holder = stateHolder ?: return
        val normalized = path.trimEnd('/').ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        val crumbs = StorageProtocol.buildBreadcrumbs(normalized)

        val cached = if (!forceFresh) directoryCache[normalized] else null
        if (cached != null) {
            nextOffset = cached.nextOffset
            currentDirModified = cached.dirModified
            holder.updateStorageState {
                it.copy(
                    currentPath = normalized,
                    breadcrumbs = crumbs,
                    selectedItem = null,
                    items = cached.items,
                    totalCount = cached.totalCount,
                    hasMorePages = cached.hasMorePages,
                    isLoadingDirectory = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    isImagePreviewCached = false
                )
            }
            sendCommand(
                StorageProtocol.buildCheckDirCommand(
                    path = normalized,
                    cachedModified = cached.dirModified,
                    cachedCount = cached.totalCount
                )
            )
            return
        }

        nextOffset = 0
        currentDirModified = 0L
        holder.updateStorageState {
            it.copy(
                currentPath = normalized,
                breadcrumbs = crumbs,
                selectedItem = null,
                items = emptyList(),
                totalCount = 0,
                hasMorePages = false,
                isLoadingDirectory = true,
                isLoadingMore = false,
                errorMessage = null,
                isImagePreviewCached = false
            )
        }

        val sent = sendCommand(
            StorageProtocol.buildListCommand(
                path = normalized,
                offset = 0,
                limit = StorageProtocol.DEFAULT_PAGE_LIMIT
            )
        )
        if (!sent) {
            holder.updateStorageState {
                it.copy(
                    isLoadingDirectory = false,
                    errorMessage = "DataChannel not connected"
                )
            }
            holder.showToast("Cannot browse storage: DataChannel not connected")
        }
    }

    fun loadMore() {
        val holder = stateHolder ?: return
        val storage = holder.uiState.value.storage
        if (!storage.hasMorePages || storage.isLoadingMore) return

        holder.updateStorageState { it.copy(isLoadingMore = true) }
        val sent = sendCommand(
            StorageProtocol.buildListCommand(
                path = storage.currentPath,
                offset = nextOffset,
                limit = StorageProtocol.DEFAULT_PAGE_LIMIT
            )
        )
        if (!sent) {
            holder.updateStorageState { it.copy(isLoadingMore = false) }
            holder.showToast("Cannot load more files: DataChannel not connected")
        }
    }

    fun selectItem(item: StorageFileItem?) {
        val holder = stateHolder ?: return
        val cached = if (item != null && !item.isDirectory) {
            imagePreviewFileCache[item.path]?.exists() == true
        } else {
            false
        }
        holder.updateStorageState {
            it.copy(
                selectedItem = item,
                isImagePreviewCached = cached
            )
        }
    }

    // -------------------------------------------------------------------------
    // FULL IMAGE PREVIEW & DOWNLOAD PIPELINE
    // -------------------------------------------------------------------------

    fun loadFullImagePreview(item: StorageFileItem) {
        val holder = stateHolder ?: return
        val cachedFile = imagePreviewFileCache[item.path]
        if (cachedFile != null && cachedFile.exists() && cachedFile.length() > 0L) {
            holder.updateStorageState {
                it.copy(
                    imagePreviewItem = item,
                    imagePreviewLocalPath = cachedFile.absolutePath,
                    isImagePreviewCached = true
                )
            }
            holder.showToast("Loaded from cache: ${item.name}")
            return
        }

        val transferId = "prev-${UUID.randomUUID().toString().take(8)}"
        val cmd = StorageProtocol.buildGetFileCommand(
            path = item.path,
            transferId = transferId,
            purpose = StorageProtocol.PURPOSE_PREVIEW
        )
        if (sendCommand(cmd)) {
            holder.showToast("Streaming full image: ${item.name}...")
        } else {
            holder.showToast("Cannot preview image: DataChannel not connected")
        }
    }

    fun downloadPreviewedImage() {
        val holder = stateHolder ?: return
        val item = holder.uiState.value.storage.imagePreviewItem ?: return
        val localPath = holder.uiState.value.storage.imagePreviewLocalPath ?: return
        val localFile = File(localPath)
        if (!localFile.exists()) {
            holder.showToast("Preview file is no longer cached")
            return
        }

        if (holder.uiState.value.settings.askBeforeDownload) {
            pendingPickerItem = null
            pendingPickerCachedPreview = item to localFile
            _pendingDownloadRequests.tryEmit(
                PendingStorageDownloadRequest(
                    fileName = item.name,
                    mimeType = item.mimeType
                )
            )
            return
        }

        val settings = holder.uiState.value.settings
        scope.launch {
            val savedPath = fileSaver.saveStagedFileToDownloads(
                stagedFile = localFile,
                fileName = item.name,
                mimeType = item.mimeType,
                deleteSource = false,
                customTreeUri = settings.downloadsTreeUri,
                directoryLabel = settings.downloadsDirectoryLabel
            )
            holder.showToast(
                if (savedPath != null) "Saved to: $savedPath" else "Failed to save preview image"
            )
        }
    }

    fun requestDownload(item: StorageFileItem) {
        val holder = stateHolder ?: return
        if (item.isDirectory) {
            holder.showToast("Folders cannot be downloaded directly")
            return
        }

        if (holder.uiState.value.settings.askBeforeDownload) {
            pendingPickerCachedPreview = null
            pendingPickerItem = item
            _pendingDownloadRequests.tryEmit(
                PendingStorageDownloadRequest(
                    fileName = item.name,
                    mimeType = item.mimeType
                )
            )
            return
        }

        startDownloadTransfer(item, destinationUri = null)
    }

    fun completeCreateDocumentSelection(uri: Uri?) {
        val holder = stateHolder ?: return

        val previewSave = pendingPickerCachedPreview
        if (previewSave != null) {
            pendingPickerCachedPreview = null
            val (item, localFile) = previewSave
            if (uri == null) {
                holder.showToast("Download cancelled")
                return
            }
            scope.launch {
                val ok = fileSaver.copyFileToUri(localFile, uri, deleteSource = false)
                holder.showToast(if (ok) "Saved cleanly: ${item.name}" else "Failed saving ${item.name}")
            }
            return
        }

        val item = pendingPickerItem ?: return
        pendingPickerItem = null
        if (uri == null) {
            holder.showToast("Download cancelled")
            return
        }
        startDownloadTransfer(item, destinationUri = uri)
    }

    fun cancelActiveTransfer() {
        val holder = stateHolder ?: return
        val active = holder.uiState.value.storage.activeTransfer ?: return
        sendCommand(StorageProtocol.buildCancelTransferCommand(active.transferId))
        holder.updateStorageState { it.copy(activeTransfer = null) }
        holder.showToast("Transfer cancelled: ${active.fileName}")

        scope.launch(transferIoDispatcher) {
            activeWriters.remove(active.transferId)?.abort()
        }
    }

    // -------------------------------------------------------------------------
    // REMOTE FILE DELETION (TWO-STEP TACTICAL CONFIRMATION)
    // -------------------------------------------------------------------------

    fun requestDeleteConfirmation(item: StorageFileItem) {
        stateHolder?.updateStorageState {
            it.copy(deleteConfirmTarget = item)
        }
    }

    fun dismissDeleteConfirmation() {
        stateHolder?.updateStorageState {
            it.copy(deleteConfirmTarget = null)
        }
    }

    fun confirmDelete() {
        val holder = stateHolder ?: return
        val target = holder.uiState.value.storage.deleteConfirmTarget ?: return
        holder.updateStorageState { it.copy(deleteConfirmTarget = null) }

        val sent = sendCommand(StorageProtocol.buildDeleteFileCommand(target.path))
        if (sent) {
            holder.showToast("Deleting ${target.name} from child device...")
        } else {
            holder.showToast("Cannot delete file: DataChannel not connected")
        }
    }

    // -------------------------------------------------------------------------
    // INCOMING DATACHANNEL MESSAGE DISPATCHER
    // -------------------------------------------------------------------------

    fun handleIncomingMessage(rawMessage: String): Boolean {
        val packet = StoragePayloadParser.parse(rawMessage) ?: return false
        val holder = stateHolder ?: return true

        when (packet) {
            is StorageIncomingPacket.DirUnchanged -> {
                holder.updateStorageState { it.copy(isLoadingDirectory = false, errorMessage = null) }
            }

            is StorageIncomingPacket.DirChanged -> {
                holder.updateStorageState { it.copy(isLoadingDirectory = true, errorMessage = null) }
            }

            is StorageIncomingPacket.ListBatch -> handleListBatch(holder, packet)

            is StorageIncomingPacket.ListError -> {
                holder.updateStorageState {
                    it.copy(
                        isLoadingDirectory = false,
                        isLoadingMore = false,
                        errorMessage = packet.reason
                    )
                }
                holder.showToast("Storage error: ${packet.reason}")
            }

            is StorageIncomingPacket.FileStart -> handleFileStart(holder, packet)
            is StorageIncomingPacket.FileChunk -> handleFileChunk(holder, packet)
            is StorageIncomingPacket.FileComplete -> handleFileComplete(holder, packet)

            is StorageIncomingPacket.FileError -> {
                holder.updateStorageState { it.copy(activeTransfer = null) }
                holder.showToast("Transfer failed: ${packet.reason}")
                scope.launch(transferIoDispatcher) {
                    activeWriters.remove(packet.transferId)?.abort()
                }
            }

            is StorageIncomingPacket.DeleteSuccess -> handleDeleteSuccess(holder, packet.path)

            is StorageIncomingPacket.DeleteError -> {
                holder.updateStorageState { it.copy(deleteConfirmTarget = null) }
                holder.showToast("Deletion failed: ${packet.reason}")
            }
        }
        return true
    }

    fun onPeerDisconnected() {
        directoryCache.clear()
        imagePreviewFileCache.clear()
        pendingDownloadUris.clear()
        pendingPickerItem = null
        pendingPickerCachedPreview = null
        nextOffset = 0
        currentDirModified = 0L

        scope.launch(transferIoDispatcher) {
            activeWriters.values.forEach { it.abort() }
            activeWriters.clear()
            fileSaver.clearAllCachedFiles()
        }
        stateHolder?.updateStorageState { StorageUiState() }
    }

    // -------------------------------------------------------------------------
    // PRIVATE HELPERS
    // -------------------------------------------------------------------------

    private fun startDownloadTransfer(item: StorageFileItem, destinationUri: Uri?) {
        val holder = stateHolder ?: return
        if (destinationUri != null) {
            val queue = pendingDownloadUris.getOrPut(item.path) { ArrayDeque() }
            queue.addLast(destinationUri)
        }
        val transferId = "dl-${UUID.randomUUID().toString().take(8)}"
        val cmd = StorageProtocol.buildGetFileCommand(
            path = item.path,
            transferId = transferId,
            purpose = StorageProtocol.PURPOSE_DOWNLOAD
        )
        if (sendCommand(cmd)) {
            holder.showToast("Starting download: ${item.name}...")
        } else {
            if (destinationUri != null) {
                pendingDownloadUris[item.path]?.removeLastOrNull()
            }
            holder.showToast("Cannot download file: DataChannel not connected")
        }
    }

    private fun handleListBatch(holder: ParentStateHolder, batch: StorageIncomingPacket.ListBatch) {
        val normalizedPath = batch.path.trimEnd('/').ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        val isAppend = batch.offset > 0
        val existingEntry = directoryCache[normalizedPath]

        val mergedItems = if (!isAppend) {
            batch.items
        } else {
            val base = existingEntry?.items ?: holder.uiState.value.storage.items
            val seen = HashSet<String>(base.size + batch.items.size)
            buildList {
                base.forEach {
                    if (seen.add(it.path)) add(it)
                }
                batch.items.forEach {
                    if (seen.add(it.path)) add(it)
                }
            }
        }

        val resolvedDirModified = if (batch.dirModified > 0L) batch.dirModified else currentDirModified
        val cacheEntry = DirectoryCacheEntry(
            path = normalizedPath,
            items = mergedItems,
            totalCount = batch.totalCount,
            hasMorePages = batch.hasMore,
            nextOffset = batch.nextOffset,
            dirModified = resolvedDirModified
        )
        directoryCache[normalizedPath] = cacheEntry

        val activePath = holder.uiState.value.storage.currentPath.trimEnd('/').ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        if (normalizedPath == activePath) {
            nextOffset = batch.nextOffset
            currentDirModified = resolvedDirModified
            holder.updateStorageState {
                it.copy(
                    currentPath = normalizedPath,
                    breadcrumbs = StorageProtocol.buildBreadcrumbs(normalizedPath),
                    items = mergedItems,
                    totalCount = batch.totalCount,
                    hasMorePages = batch.hasMore,
                    isLoadingDirectory = false,
                    isLoadingMore = false,
                    errorMessage = null
                )
            }
        }
    }

    private fun handleFileStart(holder: ParentStateHolder, packet: StorageIncomingPacket.FileStart) {
        val destinationUri = if (packet.purpose == StorageProtocol.PURPOSE_DOWNLOAD) {
            val queue = pendingDownloadUris[packet.path]
            val popped = queue?.removeFirstOrNull()
            if (queue != null && queue.isEmpty()) pendingDownloadUris.remove(packet.path)
            popped
        } else {
            null
        }

        holder.updateStorageState {
            it.copy(
                activeTransfer = StorageTransferProgress(
                    transferId = packet.transferId,
                    fileName = packet.fileName,
                    purpose = packet.purpose,
                    progressPercent = 0,
                    receivedBytes = 0L,
                    totalBytes = packet.size
                )
            )
        }

        scope.launch(transferIoDispatcher) {
            val writer = runCatching {
                fileSaver.openWriter(
                    transferId = packet.transferId,
                    path = packet.path,
                    fileName = packet.fileName,
                    expectedSize = packet.size,
                    totalChunks = packet.totalChunks,
                    mimeType = packet.mimeType,
                    purpose = packet.purpose,
                    destinationUri = destinationUri
                )
            }.onFailure { err ->
                Log.e(TAG, "Failed opening transfer writer: ${err.message}")
            }.getOrNull() ?: return@launch

            activeWriters[packet.transferId] = writer
        }
    }

    private fun handleFileChunk(holder: ParentStateHolder, packet: StorageIncomingPacket.FileChunk) {
        scope.launch(transferIoDispatcher) {
            val writer = activeWriters[packet.transferId] ?: return@launch
            runCatching {
                writer.appendChunk(packet.chunkIndex, packet.dataBase64)
            }.onFailure { err ->
                Log.e(TAG, "Failed appending chunk ${packet.chunkIndex}: ${err.message}")
                activeWriters.remove(packet.transferId)?.abort()
                scope.launch {
                    holder.updateStorageState { it.copy(activeTransfer = null) }
                    holder.showToast("Transfer failed decoding chunk: ${writer.fileName}")
                }
                return@launch
            }

            val pct = ((writer.receivedChunkCount.toDouble() / packet.totalChunks.coerceAtLeast(1)) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)

            scope.launch {
                val current = holder.uiState.value.storage.activeTransfer
                if (current != null && current.transferId == packet.transferId && current.progressPercent != pct) {
                    holder.updateStorageState {
                        it.copy(
                            activeTransfer = current.copy(
                                progressPercent = pct,
                                receivedBytes = writer.receivedBytes
                            )
                        )
                    }
                }
            }
        }
    }

    private fun handleFileComplete(holder: ParentStateHolder, packet: StorageIncomingPacket.FileComplete) {
        scope.launch(transferIoDispatcher) {
            val writer = activeWriters.remove(packet.transferId)
            if (writer == null) {
                scope.launch {
                    holder.updateStorageState { it.copy(activeTransfer = null) }
                    holder.showToast("Failed to reassemble file chunks")
                }
                return@launch
            }

            val stagedFile = runCatching { writer.finish() }.getOrNull()
            if (stagedFile == null || !stagedFile.exists()) {
                writer.abort()
                scope.launch {
                    holder.updateStorageState { it.copy(activeTransfer = null) }
                    holder.showToast("Failed finalizing ${writer.fileName}")
                }
                return@launch
            }

            if (writer.purpose == StorageProtocol.PURPOSE_PREVIEW) {
                val previewFile = fileSaver.cachePreviewFile(stagedFile, writer.path, writer.fileName)
                imagePreviewFileCache[writer.path] = previewFile
                scope.launch {
                    val matchedItem = holder.uiState.value.storage.items.firstOrNull { it.path == writer.path }
                        ?: holder.uiState.value.storage.selectedItem?.takeIf { it.path == writer.path }
                        ?: StorageFileItem(
                            path = writer.path,
                            name = writer.fileName,
                            isDirectory = false,
                            category = com.nexus.parent.state.StorageCategory.IMAGE,
                            rawBytes = writer.expectedSize,
                            sizeLabel = StorageProtocol.formatBytes(writer.expectedSize),
                            modifiedMillis = 0L,
                            modifiedLabel = "Just now",
                            extension = writer.fileName.substringAfterLast('.', "").uppercase(),
                            mimeType = writer.mimeType
                        )

                    holder.updateStorageState {
                        it.copy(
                            activeTransfer = null,
                            imagePreviewItem = matchedItem,
                            imagePreviewLocalPath = previewFile.absolutePath,
                            isImagePreviewCached = true
                        )
                    }
                    holder.showToast("Image loaded: ${writer.fileName}")
                }
                return@launch
            }

            val destinationUri = writer.destinationUri
            if (destinationUri != null) {
                val ok = fileSaver.copyFileToUri(stagedFile, destinationUri, deleteSource = true)
                scope.launch {
                    holder.updateStorageState { it.copy(activeTransfer = null) }
                    holder.showToast(if (ok) "Saved cleanly: ${writer.fileName}" else "Failed saving ${writer.fileName}")
                }
            } else {
                val settings = holder.uiState.value.settings
                val relativePath = fileSaver.saveStagedFileToDownloads(
                    stagedFile = stagedFile,
                    fileName = writer.fileName,
                    mimeType = writer.mimeType,
                    deleteSource = true,
                    customTreeUri = settings.downloadsTreeUri,
                    directoryLabel = settings.downloadsDirectoryLabel
                )
                scope.launch {
                    holder.updateStorageState { it.copy(activeTransfer = null) }
                    holder.showToast(
                        if (relativePath != null) "Saved to: $relativePath"
                        else "Failed saving ${writer.fileName} to Downloads"
                    )
                }
            }
        }
    }

    private fun handleDeleteSuccess(holder: ParentStateHolder, deletedPath: String) {
        imagePreviewFileCache.remove(deletedPath)?.delete()

        val activePath = holder.uiState.value.storage.currentPath.trimEnd('/').ifBlank { StorageProtocol.DEFAULT_STORAGE_ROOT }
        directoryCache[activePath]?.let { entry ->
            val filtered = entry.items.filterNot { it.path == deletedPath }
            directoryCache[activePath] = entry.copy(
                items = filtered,
                totalCount = (entry.totalCount - 1).coerceAtLeast(filtered.size)
            )
        }

        holder.updateStorageState { s ->
            val updatedItems = s.items.filterNot { it.path == deletedPath }
            s.copy(
                items = updatedItems,
                totalCount = (s.totalCount - 1).coerceAtLeast(updatedItems.size),
                selectedItem = if (s.selectedItem?.path == deletedPath) null else s.selectedItem,
                deleteConfirmTarget = if (s.deleteConfirmTarget?.path == deletedPath) null else s.deleteConfirmTarget,
                imagePreviewItem = if (s.imagePreviewItem?.path == deletedPath) null else s.imagePreviewItem,
                imagePreviewLocalPath = if (s.imagePreviewItem?.path == deletedPath) null else s.imagePreviewLocalPath
            )
        }
        holder.showToast("File deleted successfully from child device")
    }
}
