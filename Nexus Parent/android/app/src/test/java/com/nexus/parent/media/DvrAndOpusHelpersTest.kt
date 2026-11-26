package com.nexus.parent.media

import com.nexus.parent.state.ClipFormat
import com.nexus.parent.state.DvrDuration
import com.nexus.parent.webrtc.VideoFeedType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/**
 * DvrAndOpusHelpersTest.kt - Verifies rolling DVR retention math, FIFO pruning, seek resolution,
 * filename construction, and RFC 7845 Opus CSD header construction.
 */
class DvrAndOpusHelpersTest {

    @Test
    fun `prunes expired DVR segments according to M15 M30 M60 retention windows`() {
        assertEquals(900_000L, DvrBufferPolicy.resolveMaxBufferMs(DvrDuration.M15))
        assertEquals(1_800_000L, DvrBufferPolicy.resolveMaxBufferMs(DvrDuration.M30))
        assertEquals(3_600_000L, DvrBufferPolicy.resolveMaxBufferMs(DvrDuration.M60))

        val deletedNames = mutableListOf<String>()
        val segments = mutableListOf(
            DvrSegmentMeta(File("seg_0001.mp4"), 0L, 4_000L),
            DvrSegmentMeta(File("seg_0002.mp4"), 4_000L, 8_000L),
            DvrSegmentMeta(File("seg_0003.mp4"), 8_000L, 12_000L),
            DvrSegmentMeta(File("seg_0004.mp4"), 12_000L, 16_000L)
        )

        DvrBufferPolicy.pruneExpiredSegments(
            segments = segments,
            currentElapsedMs = 16_000L,
            maxBufferMs = 7_000L,
            onDelete = { deletedNames.add(it.name) }
        )

        assertEquals(listOf("seg_0001.mp4", "seg_0002.mp4"), deletedNames)
        assertEquals(listOf("seg_0003.mp4", "seg_0004.mp4"), segments.map { it.file.name })
    }

    @Test
    fun `resolves DVR scrubber fractions cleanly to live edge or exact segment offset`() {
        val segments = listOf(
            DvrSegmentMeta(File("seg_1.mp4"), 0L, 4_000L),
            DvrSegmentMeta(File("seg_2.mp4"), 4_000L, 8_000L),
            DvrSegmentMeta(File("seg_3.mp4"), 8_000L, 12_000L)
        )

        val liveSnap = DvrBufferPolicy.resolveSeek(segments, totalDurationSeconds = 12f, targetFraction = 0.99f)
        assertTrue(liveSnap.isLiveEdge)

        val rewind = DvrBufferPolicy.resolveSeek(segments, totalDurationSeconds = 12f, targetFraction = 0.50f)
        assertFalse(rewind.isLiveEdge)
        assertEquals(1, rewind.segmentIndex)
        assertEquals("seg_2.mp4", rewind.segmentFile?.name)
        assertEquals(2000, rewind.offsetWithinSegmentMs)

        val mp4Name = DvrBufferPolicy.buildClipFileName(VideoFeedType.CAMERA, ClipFormat.MP4, Date(1764160000000L))
        assertTrue(mp4Name.startsWith("nexus-camera-"))
        assertTrue(mp4Name.endsWith(".mp4"))

        val webmName = DvrBufferPolicy.buildClipFileName(VideoFeedType.SCREEN, ClipFormat.WEBM, Date(1764160000000L))
        assertTrue(webmName.startsWith("nexus-screen-"))
        assertTrue(webmName.endsWith(".webm"))
    }

    @Test
    fun `builds valid 19-byte RFC7845 OpusHead header matching 48kHz Mono stream`() {
        val head = OpusHeaderBuilder.buildOpusHead(sampleRateHz = 48_000, channels = 1, preSkipSamples = 312)
        assertEquals(19, head.size)

        val buf = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8).also { buf.get(it) }
        assertEquals("OpusHead", String(magic, Charsets.US_ASCII))
        assertEquals(1.toByte(), buf.get()) // version
        assertEquals(1.toByte(), buf.get()) // channels
        assertEquals(312.toShort(), buf.getShort()) // pre-skip
        assertEquals(48_000, buf.getInt()) // sample rate
        assertEquals(0.toShort(), buf.getShort()) // gain
        assertEquals(0.toByte(), buf.get()) // channel mapping
    }
}
