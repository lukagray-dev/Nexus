package com.nexus.parent.ui.monitor

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * MonitorImageHelpers.kt - Safe JPEG/PNG ByteArray and Base64 decoding helpers for Monitor UI panels.
 *
 * Hey friend! Shared between `AppLockPanel` (intruder selfies) and `WallpaperPanel` (Home/Lock
 * screen snapshots) so bitmap decoding never crashes the UI thread if a partial or corrupted frame arrives.
 */
object MonitorImageHelpers {

    fun decodeImageBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    fun decodeImageBitmapFromBase64(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val clean = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
}
