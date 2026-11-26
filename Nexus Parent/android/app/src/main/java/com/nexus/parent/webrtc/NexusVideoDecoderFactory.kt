package com.nexus.parent.webrtc

import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.EglBase
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory

/**
 * NexusVideoDecoderFactory.kt - Dynamic Hardware/Software WebRTC decoder router.
 *
 * Hey friend! WebRTC's `DefaultVideoDecoderFactory` always prefers hardware `MediaCodec` decoders
 * first. Wrapping both `DefaultVideoDecoderFactory` and `SoftwareVideoDecoderFactory` lets the
 * parent toggle `hardwareAcceleration` off to genuinely force CPU software decoding (`libvpx` /
 * `openh264`) whenever a phone's GPU MediaCodec driver has green-frame or stutter issues.
 */
class NexusVideoDecoderFactory(
    eglContext: EglBase.Context,
    initialHardwareAcceleration: Boolean = true
) : VideoDecoderFactory {

    private val hardwareFactory = DefaultVideoDecoderFactory(eglContext)
    private val softwareFactory = SoftwareVideoDecoderFactory()

    @Volatile
    private var hardwareAccelerationEnabled: Boolean = initialHardwareAcceleration

    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        hardwareAccelerationEnabled = enabled
    }

    fun isHardwareAccelerationEnabled(): Boolean = hardwareAccelerationEnabled

    override fun createDecoder(codecType: VideoCodecInfo): VideoDecoder? {
        return if (hardwareAccelerationEnabled) {
            hardwareFactory.createDecoder(codecType)
        } else {
            softwareFactory.createDecoder(codecType)
        }
    }

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        return if (hardwareAccelerationEnabled) {
            hardwareFactory.supportedCodecs
        } else {
            softwareFactory.supportedCodecs
        }
    }
}
