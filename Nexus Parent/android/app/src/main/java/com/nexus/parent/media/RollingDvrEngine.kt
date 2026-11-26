package com.nexus.parent.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.webrtc.VideoFeedType
import org.webrtc.EglBase
import org.webrtc.GlRectDrawer
import org.webrtc.VideoFrame
import org.webrtc.VideoFrameDrawer
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class DvrSegmentMeta(
    val file: File,
    val startElapsedMs: Long,
    val endElapsedMs: Long
)

data class DvrSeekResolution(
    val isLiveEdge: Boolean,
    val segmentFile: File?,
    val segmentIndex: Int,
    val offsetWithinSegmentMs: Int,
    val playbackSeconds: Float
)

data class DvrProgressSnapshot(
    val hasRecordedClips: Boolean,
    val bufferStartSeconds: Float,
    val totalSeconds: Float,
    val activeContainer: ClipFormat
)

/**
 * DvrBufferPolicy - Pure-Kotlin buffer math, segment retention pruning, seek resolution, and
 * clip filename builder shared between `RollingDvrEngine` and JVM unit tests.
 */
object DvrBufferPolicy {

    fun resolveMaxBufferMs(duration: DvrDuration): Long {
        return when (duration) {
            DvrDuration.M15 -> 15L * 60L * 1000L
            DvrDuration.M30 -> 30L * 60L * 1000L
            DvrDuration.M60 -> 60L * 60L * 1000L
        }
    }

    fun pruneExpiredSegments(
        segments: MutableList<DvrSegmentMeta>,
        currentElapsedMs: Long,
        maxBufferMs: Long,
        onDelete: (File) -> Unit = { it.delete() }
    ) {
        val cutoffMs = currentElapsedMs - maxBufferMs
        if (cutoffMs <= 0L) return
        while (segments.size > 1 && segments[0].endElapsedMs < cutoffMs) {
            val removed = segments.removeAt(0)
            onDelete(removed.file)
        }
    }

    fun resolveSeek(
        segments: List<DvrSegmentMeta>,
        totalDurationSeconds: Float,
        targetFraction: Float
    ): DvrSeekResolution {
        val clampedFraction = targetFraction.coerceIn(0f, 1f)
        val clampedTargetSec = clampedFraction * totalDurationSeconds.coerceAtLeast(0f)
        if (clampedFraction >= 0.98f || (totalDurationSeconds - clampedTargetSec) <= 2f || segments.isEmpty()) {
            return DvrSeekResolution(
                isLiveEdge = true,
                segmentFile = null,
                segmentIndex = -1,
                offsetWithinSegmentMs = 0,
                playbackSeconds = totalDurationSeconds
            )
        }

        val bufferStartMs = segments.first().startElapsedMs
        val targetMs = (clampedTargetSec * 1000f).toLong().coerceAtLeast(bufferStartMs)
        val segIndex = segments.indexOfFirst { targetMs <= it.endElapsedMs }.let { idx ->
            if (idx >= 0) idx else segments.lastIndex
        }
        val seg = segments[segIndex]
        val offsetMs = (targetMs - seg.startElapsedMs).coerceAtLeast(0L).toInt()
        return DvrSeekResolution(
            isLiveEdge = false,
            segmentFile = seg.file,
            segmentIndex = segIndex,
            offsetWithinSegmentMs = offsetMs,
            playbackSeconds = targetMs / 1000f
        )
    }

    fun buildClipFileName(
        feedType: VideoFeedType,
        format: ClipFormat,
        now: Date = Date()
    ): String {
        val label = if (feedType == VideoFeedType.CAMERA) "camera" else "screen"
        val ext = if (format == ClipFormat.WEBM) "webm" else "mp4"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)
        return "nexus-$label-$stamp.$ext"
    }
}

/**
 * RollingDvrEngine.kt - Segmented hardware VideoSink recorder powering Desktop-identical
 * YouTube-style DVR time-shifting (`15m`/`30m`/`60m`) and lossless clip export.
 *
 * Hey friend! Incoming WebRTC `VideoFrame`s render via a shared OpenGL context directly onto
 * an Android `MediaCodec` input Surface and write 4-second GOP-aligned segment files inside
 * `cacheDir/dvr/`. Rewinding plays closed segments with `MediaPlayer` inside a `TextureView`
 * while new live segments keep recording seamlessly in the background!
 */
class RollingDvrEngine(
    context: Context
) : VideoSink {

    companion object {
        private const val TAG = "RollingDvrEngine"
        private const val SEGMENT_DURATION_MS = 4_000L
        private const val BITRATE_BPS = 2_000_000
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL_SEC = 1
    }

    private val dvrRootDir = File(context.applicationContext.cacheDir, "dvr")
    private val closedSegments = CopyOnWriteArrayList<DvrSegmentMeta>()
    private val isRecording = AtomicBoolean(false)

    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null

    private var attachedTrack: VideoTrack? = null
    private var sharedEglContext: EglBase.Context? = null
    private var encoderEglBase: EglBase? = null
    private var drawer: GlRectDrawer? = null
    private var frameDrawer: VideoFrameDrawer? = null

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var muxerTrackIndex: Int = -1
    private var muxerStarted: Boolean = false
    private var encodedFormat: MediaFormat? = null

    private var currentSegmentFile: File? = null
    private var currentSegmentStartMs: Long = 0L
    private var lastFrameEncodedMs: Long = 0L
    private var segmentCounter: Int = 0
    private var sessionStartWallMs: Long = 0L
    private var lastSyncRequestAtMs: Long = 0L
    private var encoderWidth: Int = 0
    private var encoderHeight: Int = 0

    @Volatile
    private var activeFeedType: VideoFeedType = VideoFeedType.CAMERA

    @Volatile
    private var maxBufferMs: Long = DvrBufferPolicy.resolveMaxBufferMs(DvrDuration.M30)

    @Volatile
    private var activeContainer: ClipFormat = ClipFormat.MP4

    @Volatile
    private var onFallbackToMp4Callback: (() -> Unit)? = null

    fun start(
        feedType: VideoFeedType,
        track: VideoTrack,
        eglContext: EglBase.Context,
        bufferDuration: DvrDuration,
        clipFormat: ClipFormat,
        onFallbackToMp4: (() -> Unit)? = null
    ) {
        stop()
        activeFeedType = feedType
        maxBufferMs = DvrBufferPolicy.resolveMaxBufferMs(bufferDuration)
        activeContainer = clipFormat
        onFallbackToMp4Callback = onFallbackToMp4
        sessionStartWallMs = System.currentTimeMillis()
        attachedTrack = track
        sharedEglContext = eglContext

        val feedDir = getFeedDir(feedType)
        feedDir.mkdirs()
        feedDir.listFiles()?.forEach { it.delete() }
        closedSegments.clear()
        segmentCounter = 0

        val thread = HandlerThread("NexusDvrEncoderThread").also { it.start() }
        encoderThread = thread
        encoderHandler = Handler(thread.looper)

        isRecording.set(true)
        track.addSink(this)
    }

    fun updateRetention(bufferDuration: DvrDuration) {
        maxBufferMs = DvrBufferPolicy.resolveMaxBufferMs(bufferDuration)
        if (!isRecording.get() || sessionStartWallMs <= 0L) return
        val elapsedMs = System.currentTimeMillis() - sessionStartWallMs
        encoderHandler?.post {
            synchronized(closedSegments) {
                DvrBufferPolicy.pruneExpiredSegments(closedSegments, elapsedMs, maxBufferMs)
            }
        }
    }

    fun updateClipFormat(nextFormat: ClipFormat): Boolean {
        if (nextFormat == activeContainer) return false
        if (!isRecording.get()) {
            activeContainer = nextFormat
            return false
        }
        val handler = encoderHandler
        if (handler == null) {
            activeContainer = nextFormat
            return false
        }
        handler.post {
            releaseEncoderLocked()
            closedSegments.forEach { runCatching { it.file.delete() } }
            closedSegments.clear()
            segmentCounter = 0
            sessionStartWallMs = System.currentTimeMillis()
            lastFrameEncodedMs = 0L
            currentSegmentStartMs = 0L
            activeContainer = nextFormat
        }
        return true
    }

    fun getProgressSnapshot(): DvrProgressSnapshot {
        val elapsedSec = if (sessionStartWallMs > 0L && isRecording.get()) {
            ((System.currentTimeMillis() - sessionStartWallMs) / 1000f).coerceAtLeast(0f)
        } else {
            0f
        }
        val firstSegStartSec = closedSegments.firstOrNull()?.let { it.startElapsedMs / 1000f } ?: 0f
        return DvrProgressSnapshot(
            hasRecordedClips = closedSegments.isNotEmpty(),
            bufferStartSeconds = firstSegStartSec,
            totalSeconds = elapsedSec,
            activeContainer = activeContainer
        )
    }

    fun resolveSeek(targetFraction: Float): DvrSeekResolution {
        val snapshot = getProgressSnapshot()
        return DvrBufferPolicy.resolveSeek(
            segments = closedSegments.toList(),
            totalDurationSeconds = snapshot.totalSeconds,
            targetFraction = targetFraction
        )
    }

    fun getSegmentAt(index: Int): DvrSegmentMeta? = closedSegments.getOrNull(index)

    fun snapshotExportSegments(): Pair<List<File>, ClipFormat> {
        val handler = encoderHandler
        if (isRecording.get() && handler != null) {
            val latch = CountDownLatch(1)
            handler.post {
                if (muxerStarted && lastFrameEncodedMs > currentSegmentStartMs) {
                    closeCurrentSegmentLocked(lastFrameEncodedMs)
                    openNextSegmentLocked(lastFrameEncodedMs)
                }
                latch.countDown()
            }
            runCatching { latch.await(600L, TimeUnit.MILLISECONDS) }
        }
        val files = closedSegments.map { it.file }.filter { it.exists() && it.length() > 0L }
        return files to activeContainer
    }

    fun stop() {
        isRecording.set(false)
        attachedTrack?.let { track ->
            runCatching { track.removeSink(this) }
        }
        attachedTrack = null

        val handler = encoderHandler
        val thread = encoderThread
        encoderHandler = null
        encoderThread = null

        if (handler != null && thread != null && thread.isAlive) {
            handler.post {
                releaseEncoderLocked()
                thread.quitSafely()
            }
            runCatching { thread.join(1200) }
        }
        closedSegments.clear()
        sessionStartWallMs = 0L
        encoderWidth = 0
        encoderHeight = 0
    }

    override fun onFrame(frame: VideoFrame) {
        if (!isRecording.get()) return
        val handler = encoderHandler ?: return

        frame.retain()
        handler.post {
            try {
                if (!isRecording.get()) return@post
                encodeFrameOnThread(frame)
            } finally {
                frame.release()
            }
        }
    }

    private fun encodeFrameOnThread(frame: VideoFrame) {
        val targetW = normalizeEvenDimension(frame.rotatedWidth.coerceIn(160, 1280))
        val targetH = normalizeEvenDimension(frame.rotatedHeight.coerceIn(160, 1280))

        if (mediaCodec == null || targetW != encoderWidth || targetH != encoderHeight) {
            releaseEncoderLocked()
            if (!initEncoderLocked(targetW, targetH)) return
        }

        val egl = encoderEglBase ?: return
        val fDrawer = frameDrawer ?: return
        val glDrawer = drawer ?: return
        val nowMs = (System.currentTimeMillis() - sessionStartWallMs).coerceAtLeast(0L)
        lastFrameEncodedMs = nowMs

        if (nowMs - currentSegmentStartMs >= SEGMENT_DURATION_MS - 250L &&
            nowMs - lastSyncRequestAtMs >= 500L
        ) {
            lastSyncRequestAtMs = nowMs
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            runCatching { mediaCodec?.setParameters(params) }
        }

        runCatching {
            egl.makeCurrent()
            GLES20.glViewport(0, 0, encoderWidth, encoderHeight)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            fDrawer.drawFrame(frame, glDrawer, null, 0, 0, encoderWidth, encoderHeight)
            egl.swapBuffers(nowMs * 1_000_000L)
            drainEncoderLocked()
        }.onFailure { err ->
            Log.w(TAG, "Frame encode skipped: ${err.message}")
        }
    }

    private fun initEncoderLocked(width: Int, height: Int): Boolean {
        val rootCtx = sharedEglContext ?: return false
        return runCatching {
            val useWebm = activeContainer == ClipFormat.WEBM
            val mime = if (useWebm) MediaFormat.MIMETYPE_VIDEO_VP8 else MediaFormat.MIMETYPE_VIDEO_AVC
            val codec = runCatching {
                createAndConfigureCodec(mime, width, height)
            }.getOrElse {
                if (useWebm) {
                    activeContainer = ClipFormat.MP4
                    onFallbackToMp4Callback?.invoke()
                }
                createAndConfigureCodec(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            }

            val surface = codec.createInputSurface()
            codec.start()

            val egl = EglBase.create(rootCtx, EglBase.CONFIG_RECORDABLE)
            egl.createSurface(surface)
            egl.makeCurrent()

            mediaCodec = codec
            inputSurface = surface
            encoderEglBase = egl
            drawer = GlRectDrawer()
            frameDrawer = VideoFrameDrawer()
            encoderWidth = width
            encoderHeight = height
            encodedFormat = null
            muxerTrackIndex = -1
            muxerStarted = false

            openNextSegmentLocked(lastFrameEncodedMs)
            true
        }.getOrElse { err ->
            Log.w(TAG, "Failed to initialize DVR MediaCodec encoder: ${err.message}")
            releaseEncoderLocked()
            false
        }
    }

    private fun createAndConfigureCodec(mime: String, width: Int, height: Int): MediaCodec {
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE_BPS)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
        }
        return MediaCodec.createEncoderByType(mime).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
    }

    private fun openNextSegmentLocked(startMs: Long) {
        val dir = getFeedDir(activeFeedType).apply { mkdirs() }
        val ext = if (activeContainer == ClipFormat.WEBM) "webm" else "mp4"
        val outFile = File(dir, String.format(Locale.US, "seg_%05d.%s", ++segmentCounter, ext))
        val outputFormat = if (activeContainer == ClipFormat.WEBM) {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
        } else {
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        }

        val newMuxer = MediaMuxer(outFile.absolutePath, outputFormat)
        muxer = newMuxer
        currentSegmentFile = outFile
        currentSegmentStartMs = startMs
        muxerStarted = false
        muxerTrackIndex = -1

        encodedFormat?.let { fmt ->
            muxerTrackIndex = newMuxer.addTrack(fmt)
            newMuxer.start()
            muxerStarted = true
        }
    }

    private fun closeCurrentSegmentLocked(endMs: Long) {
        val activeMuxer = muxer ?: return
        val file = currentSegmentFile
        val startMs = currentSegmentStartMs
        muxer = null
        currentSegmentFile = null

        val valid = muxerStarted && file != null && endMs > startMs
        runCatching {
            if (muxerStarted) activeMuxer.stop()
        }
        runCatching { activeMuxer.release() }
        muxerStarted = false
        muxerTrackIndex = -1

        if (valid && file.exists() && file.length() > 0L) {
            synchronized(closedSegments) {
                closedSegments.add(DvrSegmentMeta(file = file, startElapsedMs = startMs, endElapsedMs = endMs))
                DvrBufferPolicy.pruneExpiredSegments(closedSegments, endMs, maxBufferMs)
            }
        } else {
            file?.delete()
        }
    }

    private fun drainEncoderLocked() {
        val codec = mediaCodec ?: return
        val info = MediaCodec.BufferInfo()

        while (true) {
            val outIndex = codec.dequeueOutputBuffer(info, 0)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    encodedFormat = format
                    val currentMuxer = muxer
                    if (currentMuxer != null && !muxerStarted) {
                        muxerTrackIndex = currentMuxer.addTrack(format)
                        currentMuxer.start()
                        muxerStarted = true
                    }
                }
                outIndex >= 0 -> {
                    val outBuf: ByteBuffer? = codec.getOutputBuffer(outIndex)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    val isKeyFrame = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    val ptsMs = info.presentationTimeUs / 1000L

                    if (!isConfig && outBuf != null && info.size > 0) {
                        if (isKeyFrame && (ptsMs - currentSegmentStartMs) >= SEGMENT_DURATION_MS && muxerStarted) {
                            closeCurrentSegmentLocked(ptsMs)
                            openNextSegmentLocked(ptsMs)
                        }

                        if (muxerStarted && muxerTrackIndex >= 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer?.writeSampleData(muxerTrackIndex, outBuf, info)
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun releaseEncoderLocked() {
        closeCurrentSegmentLocked(lastFrameEncodedMs)
        runCatching { frameDrawer?.release() }
        frameDrawer = null
        runCatching { drawer?.release() }
        drawer = null
        runCatching { encoderEglBase?.releaseSurface() }
        runCatching { encoderEglBase?.release() }
        encoderEglBase = null
        runCatching { mediaCodec?.stop() }
        runCatching { mediaCodec?.release() }
        mediaCodec = null
        runCatching { inputSurface?.release() }
        inputSurface = null
        encodedFormat = null
    }

    private fun getFeedDir(feedType: VideoFeedType): File {
        val sub = if (feedType == VideoFeedType.CAMERA) "camera" else "screen"
        return File(dvrRootDir, sub)
    }

    private fun normalizeEvenDimension(value: Int): Int = if (value % 2 == 0) value else value - 1
}
