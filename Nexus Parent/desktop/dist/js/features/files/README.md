# Files Feature

Displays and manages files from the child device's storage.

## Features

- Browse files and folders from child device
- View file thumbnails and metadata
- Navigate through folder hierarchy
- Preview images and videos
- Download files to local system
- Search and filter files
- Load more files on demand

## Commands Sent to Child

### LIST_FILES
Requests a list of files from the child device.

```json
{
  "cmd": "LIST_FILES",
  "request_id": "files-1234567890",
  "include_hidden": false,
  "path": "/storage/emulated/0/DCIM" // optional, for folder navigation
}
```

### REQUEST_FILE
Requests a specific file for preview or download.

```json
{
  "cmd": "REQUEST_FILE",
  "file_path": "/storage/emulated/0/DCIM/photo.jpg",
  "purpose": "preview" // or "download"
}
```

## Messages Received from Child

### LIST_FILES_BATCH
Batch of files received during listing.

```json
{
  "type": "LIST_FILES_BATCH",
  "request_id": "files-1234567890",
  "storage_id": "primary",
  "storage_label": "Internal Storage",
  "parent_path": "/storage/emulated/0",
  "items": [
    {
      "filename": "photo.jpg",
      "absolute_path": "/storage/emulated/0/DCIM/photo.jpg",
      "file_size": 1024000,
      "mime_type": "image/jpeg",
      "is_directory": false,
      "file_type_icon": "image",
      "created_timestamp": 1234567890000,
      "modified_timestamp": 1234567890000,
      "thumbnail_base64": "..." // optional
    }
  ]
}
```

### LIST_FILES_COMPLETE
Indicates file listing is complete.

```json
{
  "type": "LIST_FILES_COMPLETE",
  "request_id": "files-1234567890"
}
```

### LIST_FILES_ERROR
Indicates an error during file listing.

```json
{
  "type": "LIST_FILES_ERROR",
  "request_id": "files-1234567890",
  "reason": "Permission denied"
}
```

### FILE_TRANSFER_*
File transfer progress and completion messages.

```json
{
  "type": "FILE_TRANSFER_PROGRESS",
  "file_path": "/storage/emulated/0/DCIM/photo.jpg",
  "progress": 0.5,
  "status": "progress"
}
```

```json
{
  "type": "FILE_TRANSFER_COMPLETE",
  "file_path": "/storage/emulated/0/DCIM/photo.jpg",
  "local_path": "/path/to/cached/file.jpg",
  "status": "complete"
}
```

```json
{
  "type": "FILE_TRANSFER_ERROR",
  "file_path": "/storage/emulated/0/DCIM/photo.jpg",
  "reason": "File not found",
  "status": "error"
}
```

## UI Components

### Main View
- File gallery with grid layout
- Breadcrumb navigation
- Refresh and back buttons
- Load more button
- Status bar

### Detail View
- File preview area (images/videos)
- File metadata grid
- Preview and download buttons
- Progress indicator
- Back button

## File Types

Supported file type icons:
- 📁 folder
- 🖼️ image
- 🎞️ video
- 🎧 audio
- 📕 pdf
- 🗜️ archive
- 📄 doc/text
- 📦 generic file

## Video Playback

Uses Electron's built-in video player (HTML5 `<video>` element) instead of external players like MPV.

## Data Persistence

File data persists across panel open/close cycles using `window.filesFeatureData`:
- files: Array of file objects
- fileMap: Map of file paths to file objects
- visibleCount: Number of files to display
- currentPath: Current folder path
- isListing: Whether a scan is in progress
- selectedFile: Currently selected file for detail view
