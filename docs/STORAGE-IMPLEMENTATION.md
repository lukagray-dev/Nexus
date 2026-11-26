# Internal Storage Monitoring & Remote File Management Specification

Nexus provides on-demand, privacy-preserving remote internal storage surveillance, file exploration, metadata inspection, in-app image previewing, chunked file downloading, and remote file deletion from the Android Child device to the Desktop Parent console:
1. **Zero Cloud Database Persistence**: All filesystem traversal, metadata batches, thumbnails, file streams, and deletion commands flow end-to-end directly across the WebRTC `nexus-control` DataChannel (`ordered: true`).
2. **Progressive On-Demand Pagination**: Slices large directories into 40-item pages (`STORAGE_LIST_BATCH` with `offset`, `limit`, `hasMore`, `nextOffset`). When the parent scrolls to the bottom of a folder, a "Load More" button requests the next slice on demand, preventing Android CPU spikes and UI freezing.
3. **Parent Directory & Delta Caching**: Previously viewed directories are cached in Parent memory (`directoryCache`). Revisiting a folder renders the cached listing instantaneously while issuing a lightweight `STORAGE_CHECK_DIR` check to Android Child. If the folder timestamp or item count has not changed (`STORAGE_DIR_UNCHANGED`), zero re-fetching occurs. If modified (`STORAGE_DIR_CHANGED`), it refreshes seamlessly with fresh data.
4. **In-App Image Preview Caching**: High-resolution image preview streams are cached on Parent (`imagePreviewCache` Blob URLs on Desktop; `cacheDir/storage_previews/` disk files on Android). Closing and re-opening an image preview displays instantly without re-downloading bytes over the WebRTC DataChannel.
5. **Hardware Telemetry Storage Gauge**: Seamlessly integrates with `DeviceTelemetryManager.kt` hardware metrics (`freeStorageBytes` and `totalStorageBytes`), dynamically rendering used gigabytes, total capacity, and a responsive visual gauge.
6. **Lightweight Compressed Thumbnails**: Images and videos generate memory-safe 160x160 JPEG thumbnails (quality 50%) encoded into Base64 Data URLs, computed only for the requested 40-item slice.
7. **Two-Stage Inspection & In-App Photo Viewer**: Inspecting any file opens a tactical metadata drawer/dialog (path, size in bytes, MIME type, modified timestamp). For photos, parents can click "Load Full Image" / "View Full Image" to stream high-resolution images directly into an in-app viewer (tap-to-open directory and tap-to-inspect file on Android touchscreens; single-click inspect / double-click open on Desktop).
8. **High-Speed 64KB Streaming & Universal Download Location**: Files are sliced into 64KB binary Base64 chunks (`65536` bytes) streamed without artificial sleep delays. Downloads respect the parent's **General & System** preferences:
   - **Desktop (Tauri)**: On a fresh install, asks once for a default folder and persists it; subsequent downloads save directly into that directory with automatic duplicate-filename numbering (`photo (1).jpg`) via Rust's atomic `.nexuspart` writer, or prompt every time when **Ask Every Time Before Downloading** is enabled.
   - **Parent Android (`StorageFileSaver.kt` & `StorageCoordinator.kt`)**: Streams chunks sequentially on `Dispatchers.IO` into `cacheDir/storage_transfers/<transferId>.nexuspart` (O(1) RAM overhead) with live progress & one-tap Cancel; saves directly to `MediaStore.Downloads` under `Downloads/NexusParent/` with collision numbering when **Ask Before Download** is off, or prompts with `Intent.ACTION_CREATE_DOCUMENT` prior to starting `STORAGE_GET_FILE` when enabled.
9. **Remote File Deletion with Confirmation Modal**: Parents can remotely delete files on the child device. To prevent accidental data loss, both Desktop and Android Parent prompt with a high-contrast tactical confirmation dialog before dispatching `STORAGE_DELETE_FILE`. Deletions automatically evict affected paths from Parent caches.

---

## 1. DataChannel Protocol Specification

All transmissions occur over the bidirectional `nexus-control` WebRTC DataChannel (`ordered: true`).

### Protocol Command Matrix

| Direction | Command / Payload | Description | Expected Action / Response |
| :--- | :--- | :--- | :--- |
| **Parent → Child** | `{"type":"STORAGE_CHECK_DIR","path":"...","cachedModified":123,"cachedCount":45}` | Delta validation probe for cached directory. | Child checks directory `lastModified` and count; returns unchanged or changed. |
| **Child → Parent** | `{"type":"STORAGE_DIR_UNCHANGED","path":"..."}` | Confirms cached directory has not changed. | Desktop retains cached items, hides loading indicator. |
| **Child → Parent** | `{"type":"STORAGE_DIR_CHANGED","path":"..."}` | Signals directory was modified or files added/deleted. | Desktop invalidates cache and triggers fresh page 0 fetch. |
| **Parent → Child** | `{"type":"STORAGE_LIST","path":"...","offset":0,"limit":40,"requestId":"..."}` | Paginated directory enumeration request. | Child reads directory, generates thumbnails for slice, returns `STORAGE_LIST_BATCH`. |
| **Child → Parent** | `{"type":"STORAGE_LIST_BATCH","items":[...],"hasMore":true,"nextOffset":40,...}` | Progressive 40-item batch of file/folder metadata. | Desktop appends items to table and updates "Load More" controls. |
| **Child → Parent** | `{"type":"STORAGE_LIST_ERROR","path":"...","error":"..."}` | Reports failure (e.g. `PERMISSION_DENIED`, `NOT_FOUND`). | Desktop hides spinner and displays error toast. |
| **Parent → Child** | `{"type":"STORAGE_GET_FILE","path":"...","purpose":"download"\|"preview"}` | Requests high-speed 64KB file stream. | Child verifies access and begins 64KB chunk streaming. |
| **Child → Parent** | `{"type":"STORAGE_FILE_START", ...}` | Transfer header with total chunks, file size, and MIME type. | Desktop initializes accumulator and shows progress toast. |
| **Child → Parent** | `{"type":"STORAGE_FILE_CHUNK", ...}` | 64KB slice of Base64 encoded file data. | Desktop accumulates chunks and updates progress percentage. |
| **Child → Parent** | `{"type":"STORAGE_FILE_COMPLETE", ...}` | Notification that file transfer has concluded. | If preview: renders and caches image; if download: saves to user's chosen path. |
| **Child → Parent** | `{"type":"STORAGE_FILE_ERROR", ...}` | Reports error during file streaming. | Desktop cleans up session and shows error toast. |
| **Parent → Child** | `{"type":"STORAGE_CANCEL_TRANSFER","transferId":"..."}` | Cancels an ongoing file transfer. | Child halts coroutine and closes file stream. |
| **Parent → Child** | `{"type":"STORAGE_DELETE_FILE","path":"...","requestId":"..."}` | Requests remote file deletion. | Child calls `file.delete()`, rescans MediaScanner, evicts parent cache. |
| **Child → Parent** | `{"type":"STORAGE_DELETE_SUCCESS","path":"..."}` | Confirms remote file was deleted. | Desktop removes item from state and cache, updates counts, and shows toast. |
| **Child → Parent** | `{"type":"STORAGE_DELETE_ERROR","path":"...","reason":"..."}` | Reports deletion failure (e.g. read-only, non-empty dir). | Desktop shows error toast. |

---

## 2. Telemetry Payload Schemas

### Directory Cache Check Packet (`STORAGE_CHECK_DIR`)

```json
{
  "type": "STORAGE_CHECK_DIR",
  "path": "/storage/emulated/0/DCIM/Camera",
  "cachedModified": 1731998520000,
  "cachedCount": 42
}
```

### Directory Batch Packet (`STORAGE_LIST_BATCH`)

```json
{
  "type": "STORAGE_LIST_BATCH",
  "path": "/storage/emulated/0/DCIM/Camera",
  "items": [
    {
      "name": "IMG_20261119_104200.jpg",
      "absolutePath": "/storage/emulated/0/DCIM/Camera/IMG_20261119_104200.jpg",
      "size": 4209152,
      "modified": 1731998520000,
      "isDirectory": false,
      "mimeType": "image/jpeg",
      "category": "image",
      "thumbnail": "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQ..."
    }
  ],
  "offset": 0,
  "limit": 40,
  "hasMore": true,
  "nextOffset": 40,
  "isLast": false,
  "totalCount": 184,
  "dirModified": 1731998520000,
  "requestId": "req-101"
}
```

### File Transfer Start (`STORAGE_FILE_START`)

```json
{
  "type": "STORAGE_FILE_START",
  "transferId": "48b6c59d-647d-4113-90d2-96942fa609ec",
  "path": "/storage/emulated/0/Pictures/family.jpg",
  "fileName": "family.jpg",
  "size": 3145728,
  "totalChunks": 48,
  "chunkSize": 65536,
  "mimeType": "image/jpeg",
  "purpose": "preview",
  "requestId": "preview-01"
}
```

### File Transfer Chunk (`STORAGE_FILE_CHUNK`)

```json
{
  "type": "STORAGE_FILE_CHUNK",
  "transferId": "48b6c59d-647d-4113-90d2-96942fa609ec",
  "chunkIndex": 0,
  "totalChunks": 48,
  "data": "iVBORw0KGgoAAAANSUhEUgAA..."
}
```

---

## 3. End-to-End Sequence Flow

```text
Desktop Parent                                                          Android Child
      |                                                                       |
      |-- 1. User opens "DCIM/Camera" (Cache Miss) --------------------------->|
      |   (UI sends STORAGE_LIST with offset=0, limit=40)                     |
      |                                                                       |-- 2. Verify storage permissions
      |                                                                       |-- 3. Query directory & slice items [0..39]
      |                                                                       |-- 4. Downsample thumbnails (160x160 px)
      |<-- 5. Receive STORAGE_LIST_BATCH [items 0..39, hasMore=true] ---------|
      |-- 6. Render rows, cache in directoryCache, render "Load More" button  |
      |                                                                       |
      |-- 7. User navigates away to "Downloads" and returns to "DCIM/Camera"  |
      |-- 8. [Cache Hit] Instantly render cached 40 rows from directoryCache  |
      |-- 9. Send STORAGE_CHECK_DIR [cachedModified, cachedCount] ------------>|
      |                                                                       |-- 10. Check folder.lastModified() & count
      |<-- 11. Receive STORAGE_DIR_UNCHANGED ---------------------------------|
      |-- 12. Keep cached view active; zero lag or unnecessary bandwidth     |
      |                                                                       |
      |-- 13. User clicks "Load More" button --------------------------------->|
      |   (UI sends STORAGE_LIST with offset=40, limit=40)                    |
      |                                                                       |-- 14. Slice items [40..79]
      |<-- 15. Receive STORAGE_LIST_BATCH [items 40..79, hasMore=true] -------|
      |-- 16. Append rows to table and update directoryCache                  |
      |                                                                       |
      |-- 17. User clicks "Load Full Image" (preview) ------------------------>|
      |   (If already in imagePreviewCache -> opens instantly, no network)     |
      |   (If not cached -> Send STORAGE_GET_FILE with purpose="preview")      |
      |<-- 18. Receive 64KB high-speed chunks [STORAGE_FILE_CHUNK] -----------|
      |<-- 19. Receive STORAGE_FILE_COMPLETE ---------------------------------|
      |-- 20. Store Blob in imagePreviewCache & render in image modal         |
      |                                                                       |
      |-- 21. User clicks "Download File" (or "Download Image" in modal)        |
      |-- 22. Resolve target via General & System settings (once/ask/default) |
      |-- 23. Stream 64KB chunks -> Rust atomic `.nexuspart` commit on disk   |
      |                                                                       |
      |-- 24. User deletes a file -> Send STORAGE_DELETE_FILE ---------------->|
      |                                                                       |-- 25. Execute file.delete()
      |<-- 26. Receive STORAGE_DELETE_SUCCESS --------------------------------|
      |-- 27. Evict path from directoryCache & imagePreviewCache, remove row  |
```

---

## 4. Architecture & Engineering Safeguards

1. **High-Speed 64KB SCTP Chunk Streaming**: Slices file downloads and previews into 64KB (`65536` bytes) chunks without artificial delay, achieving 4x-10x throughput over previous 16KB throttled streams while staying within WebRTC DataChannel MTU limits.
2. **Double-Layered Caching**:
   - `directoryCache`: Keeps directory contents and pagination states indexed by path with background timestamp validation (`STORAGE_CHECK_DIR`).
   - `imagePreviewCache`: Keeps high-resolution Blob URLs in memory to eliminate redundant downloads when previewing the same photo multiple times.
3. **Safe Memory Allocation on Android**: Thumbnails are generated only for requested 40-item pagination slices, preventing memory heap spikes on folders with thousands of media files. Bitmaps are downsampled via `inSampleSize`, use `RGB_565`, and are recycled promptly.
4. **Universal Save Location & Atomic Disk Writing**:
   - **Desktop**: Uses `tauri-plugin-dialog` and Rust atomic `.nexuspart` staging (`downloads.rs`) so downloads follow General & System directory preferences (`downloadsDirectory` / `askBeforeDownload`), with `window.showSaveFilePicker` / Blob fallback outside Tauri.
   - **Parent Android**: `StorageCoordinator.kt` + `StorageFileSaver.kt` write chunks sequentially on a serialized IO dispatcher into `.nexuspart`, commit to `Downloads/NexusParent/` via `MediaStore.Downloads` or user-picked SAF `Uri` (`Intent.ACTION_CREATE_DOCUMENT`), and downsample previewed images safely up to 2048px when rendering inside `StorageScreen.kt`.
5. **Multi-Version Android Storage Compliance**:
   - Android 11+ (API 30+): Uses `Environment.isExternalStorageManager()` (`MANAGE_EXTERNAL_STORAGE`).
   - Android 10 and below: Uses `READ_EXTERNAL_STORAGE`.
   - MediaStore synchronization via `MediaScannerConnection.scanFile()` on deletion.
6. **Pure Tactical Monochrome Design**: Strict pitch-black UI (`#000000` / `#121212`) utilizing only Lucide SVGs (`folder.svg`, `image.svg`, `video.svg`, `file-text.svg`, `audio-lines.svg`, `trash.svg`, `download.svg`) and strictly zero emojis.
