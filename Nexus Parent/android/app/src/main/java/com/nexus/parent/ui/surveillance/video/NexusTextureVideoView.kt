package com.nexus.parent.ui.surveillance.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import com.nexus.parent.ui.theme.NexusColors
import org.webrtc.EglBase
import org.webrtc.EglRenderer
import org.webrtc.GlRectDrawer
import org.webrtc.RendererCommon
import org.webrtc.ThreadUtils
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.CountDownLatch

/**
 * NexusTextureVideoRenderer - Custom TextureView-backed WebRTC VideoSink.
 *
 * Hey friend! Standard WebRTC `SurfaceViewRenderer` subclasses `SurfaceView`, which punches a
 * transparent hole in the Android Window hierarchy. Inside Jetpack Compose that breaks floating
 * player bars, DVR scrubbers, draggable Mic overlays, push-drawer translations, and 90-degree
 * rotations.
 *
 * By implementing `VideoSink` + `TextureView.SurfaceTextureListener` backed by `org.webrtc.EglRenderer`,
 * incoming WebRTC frames render into a standard GPU texture inside the Compose view tree with zero
 * Z-order or clipping blockers!
 */
class NexusTextureVideoRenderer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener, VideoSink {

    private val eglRenderer = EglRenderer("NexusTextureVideoRenderer")
    private var isInitialized = false
    private val videoLayoutMeasure = RendererCommon.VideoLayoutMeasure()
    private var rotatedFrameWidth = 0
    private var rotatedFrameHeight = 0

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun init(sharedContext: EglBase.Context, rendererEvents: RendererCommon.RendererEvents? = null) {
        ThreadUtils.checkIsOnMainThread()
        if (isInitialized) return
        isInitialized = true
        eglRenderer.init(sharedContext, EglBase.CONFIG_PLAIN, GlRectDrawer())
        rendererEvents?.let { events ->
            eglRenderer.addFrameListener({ }, 0f)
            // Hook point for first-frame / resolution callbacks when WebRTC wiring is added
            @Suppress("UNUSED_VARIABLE")
            val ignored = events
        }
    }

    fun setScalingType(scalingType: RendererCommon.ScalingType) {
        videoLayoutMeasure.setScalingType(scalingType)
        requestLayout()
    }

    fun setMirror(mirror: Boolean) {
        eglRenderer.setMirror(mirror)
    }

    fun release() {
        if (!isInitialized) return
        isInitialized = false
        eglRenderer.release()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
        if (isInitialized) {
            eglRenderer.createEglSurface(surface)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        ThreadUtils.checkIsOnMainThread()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        ThreadUtils.checkIsOnMainThread()
        val latch = CountDownLatch(1)
        if (isInitialized) {
            eglRenderer.releaseEglSurface { latch.countDown() }
        } else {
            latch.countDown()
        }
        ThreadUtils.awaitUninterruptibly(latch)
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onFrame(frame: VideoFrame) {
        if (rotatedFrameWidth != frame.rotatedWidth || rotatedFrameHeight != frame.rotatedHeight) {
            rotatedFrameWidth = frame.rotatedWidth
            rotatedFrameHeight = frame.rotatedHeight
            post { requestLayout() }
        }
        eglRenderer.onFrame(frame)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        ThreadUtils.checkIsOnMainThread()
        val size = videoLayoutMeasure.measure(
            widthMeasureSpec,
            heightMeasureSpec,
            rotatedFrameWidth,
            rotatedFrameHeight
        )
        setMeasuredDimension(size.x, size.y)
    }
}

/**
 * Compose wrapper around `NexusTextureVideoRenderer` with `DisposableEffect` sink lifecycle
 * management and smooth 90-degree `graphicsLayer` rotation support.
 */
@Composable
fun NexusVideoSurface(
    videoTrack: VideoTrack?,
    eglContext: EglBase.Context?,
    rotationDegrees: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(NexusColors.CanvasBlack)
    ) {
        if (videoTrack != null && eglContext != null) {
            val rendererHolder = remember { arrayOfNulls<NexusTextureVideoRenderer>(1) }

            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotationDegrees.toFloat() },
                factory = { ctx ->
                    NexusTextureVideoRenderer(ctx).apply {
                        init(eglContext)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                        rendererHolder[0] = this
                    }
                },
                update = { view ->
                    rendererHolder[0] = view
                }
            )

            DisposableEffect(videoTrack, eglContext) {
                val renderer = rendererHolder[0]
                if (renderer != null) {
                    videoTrack.addSink(renderer)
                }
                onDispose {
                    if (renderer != null) {
                        videoTrack.removeSink(renderer)
                        renderer.release()
                    }
                }
            }
        }
    }
}
