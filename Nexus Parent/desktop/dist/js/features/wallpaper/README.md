# Wallpaper Feature

Send and set wallpaper images on the child device remotely.

## Overview

The Wallpaper feature allows you to upload an image from the parent device and set it as the wallpaper on the child device. Images are transferred via WebRTC data channel in chunks for reliability.

## Features

- **Drag & Drop**: Drag image files directly onto the drop zone
- **File Browser**: Click to browse and select images
- **Image Preview**: Preview the image before sending
- **Progress Tracking**: Real-time upload progress indicator
- **Status Updates**: Receive confirmation when wallpaper is set
- **File Size Limit**: Maximum 10MB per image

## Supported Formats

- JPG/JPEG
- PNG
- GIF
- Other image formats supported by the browser

## How It Works

### Parent Side (Desktop)
1. Select or drag an image file
2. Preview the image
3. Click "Set Wallpaper" button
4. Image is sent in 16KB chunks via data channel
5. Progress is displayed during transfer
6. Confirmation received when wallpaper is set

### Android Side
The child device:
1. Receives the SET_WALLPAPER command with file metadata
2. Receives binary chunks and reconstructs the image
3. Saves the image to device storage
4. Sets it as the device wallpaper
5. Sends confirmation back to parent

## Commands

### Set Wallpaper Command
```json
{
    "cmd": "SET_WALLPAPER",
    "size": 1234567,
    "filename": "wallpaper.jpg"
}
```

### Status Messages (from Android)
- `WALLPAPER_RECEIVING` - Child is receiving the image
- `WALLPAPER_SET_SUCCESS` - Wallpaper set successfully
- `WALLPAPER_SET_FAILED` - Failed to set wallpaper
- `WALLPAPER_ERROR: <message>` - Error occurred

## UI Components

### Drop Zone
- Displays placeholder when empty
- Shows image preview when file is selected
- Supports drag & drop and click to browse
- Visual feedback on drag over

### Action Buttons
- **Set Wallpaper**: Send image to child device (golden button)
- **Clear**: Remove preview and reset (gray button)

### Status Messages
- **Blue**: Sending/processing
- **Green**: Success
- **Red**: Error
- **Yellow**: Warning

## Technical Details

### Binary Transfer
- Images are sent in 16KB chunks for reliability
- Each chunk is sent via WebRTC data channel
- Progress is calculated based on chunks sent
- Total transfer time depends on image size and connection speed

### File Size Limit
- Maximum: 10MB
- Larger files are rejected with error message
- Recommended: Keep images under 5MB for faster transfer

### Permissions Required (Android)
- `WRITE_EXTERNAL_STORAGE` - To save wallpaper image
- `SET_WALLPAPER` - To set device wallpaper

## Usage Example

```javascript
// Send wallpaper command
const command = JSON.stringify({
    cmd: 'SET_WALLPAPER',
    size: arrayBuffer.byteLength,
    filename: 'wallpaper.jpg'
});
app.connectionManager.sendCommand(command);

// Send binary chunks
const chunkSize = 16384;
for (let i = 0; i < totalChunks; i++) {
    const chunk = arrayBuffer.slice(start, end);
    app.connectionManager.sendBinaryData(chunk);
}

// Listen for status
window.addEventListener('wallpaper-status', (event) => {
    console.log('Status:', event.detail.message);
});
```

## Error Handling

### Common Errors
- **File too large**: Image exceeds 10MB limit
- **Connection lost**: WebRTC connection dropped during transfer
- **Permission denied**: Android lacks wallpaper permissions
- **Invalid format**: File is not a valid image
- **Timeout**: No response from child device within 30 seconds

### Troubleshooting
1. Ensure child device has wallpaper permissions
2. Check WebRTC connection is stable
3. Try smaller image files
4. Verify image format is supported
5. Check device storage space

## Notes

- Transfer speed depends on WebRTC connection quality
- Large images may take longer to transfer
- Child device must have sufficient storage space
- Some Android versions may require additional permissions
- Wallpaper is set for home screen (not lock screen)
- Original image quality is preserved during transfer
