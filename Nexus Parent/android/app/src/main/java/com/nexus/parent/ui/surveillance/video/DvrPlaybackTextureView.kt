package com.nexus.parent.ui.surveillance.video

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * DvrPlaybackTextureView.kt - Zero-blocker TextureView + MediaPlayer surface for rewound DVR clips.
 *
 * Hey friend! Rendered only when the user scrubs backwards off the live edge (`!isAtLiveEdge`).
 * Because it uses `TextureView` inside the exact same `graphicsLayer` rotation modifier as
 * `NexusVideoSurface`, rotating or swiping overlays works smoothly without `SurfaceView` holes.
 */
@Composable
fun DvrPlaybackTextureView(
    segmentPath: String?,
    offsetMs: Int,
    seekTriggerId: Long,
    rotationDegrees: Int,
    onSegmentPositionMs: (Int) -> Unit,
    onSegmentCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (segmentPath.isNullOrBlank()) return

    val mediaPlayer = remember { MediaPlayer() }
    val surfaceHolder = remember { arrayOfNulls<Surface>(1) }

    LaunchedEffect(segmentPath, seekTriggerId) {
        while (true) {
            val pos = runCatching {
                if (mediaPlayer.isPlaying) mediaPlayer.currentPosition else -1
            }.getOrDefault(-1)
            if (pos >= 0) {
                onSegmentPositionMs(pos)
            }
            delay(250L)
        }
    }

    LaunchedEffect(segmentPath, seekTriggerId) {
        runCatching {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(segmentPath)
            surfaceHolder[0]?.let { mediaPlayer.setSurface(it) }
            mediaPlayer.setOnPreparedListener { mp ->
                if (offsetMs > 0) {
                    mp.seekTo(offsetMs)
                }
                mp.start()
            }
            mediaPlayer.setOnCompletionListener {
                onSegmentCompleted()
            }
            mediaPlayer.prepareAsync()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
            surfaceHolder[0]?.release()
            surfaceHolder[0] = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { rotationZ = rotationDegrees.toFloat() },
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        val s = Surface(st)
                        surfaceHolder[0] = s
                        runCatching { mediaPlayer.setSurface(s) }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { mediaPlayer.setSurface(null) }
                        surfaceHolder[0]?.release()
                        surfaceHolder[0] = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        }
    )
}
