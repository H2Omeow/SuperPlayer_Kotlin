package top.nekoh2o.player.audio

import java.nio.ByteBuffer
import top.nekoh2o.player.data.model.AudioEffectSettings

/** 每个 AudioProcessor 独占一个 DSP 实例，仅在播放线程调用。 */
internal interface NativeEffectBackend {
    fun create(sampleRate: Int, channelCount: Int): Long
    fun configure(handle: Long, settings: AudioEffectSettings)
    fun process(handle: Long, buffer: ByteBuffer, sampleCount: Int)
    fun reset(handle: Long)
    fun release(handle: Long)
}

internal class JniEffectBackend : NativeEffectBackend {
    override fun create(sampleRate: Int, channelCount: Int): Long =
        NativeAudioEffectsController.nativeCreate(sampleRate, channelCount)

    override fun configure(handle: Long, settings: AudioEffectSettings) {
        NativeAudioEffectsController.nativeConfigure(
            handle, settings.eqBands.toFloatArray(), settings.bassBoost,
            settings.virtualizer, settings.reverbWet, settings.reverbRoomSize,
            settings.reverbDamping, settings.loudnessGain,
            settings.masteringPresetId, settings.masteringMix
        )
    }

    override fun process(handle: Long, buffer: ByteBuffer, sampleCount: Int) =
        NativeAudioEffectsController.nativeProcess(handle, buffer, sampleCount)
    override fun reset(handle: Long) = NativeAudioEffectsController.nativeReset(handle)
    override fun release(handle: Long) = NativeAudioEffectsController.nativeRelease(handle)
}

internal object NativeAudioEffectsController {
    init { System.loadLibrary("nekoplayer_audio_effects") }

    external fun nativeCreate(sampleRate: Int, channelCount: Int): Long
    external fun nativeConfigure(
        handle: Long, bands: FloatArray, bass: Int, width: Int, wet: Int,
        room: Int, damping: Int, loudness: Int, masteringId: Int, masteringMix: Int
    )
    external fun nativeProcess(handle: Long, buffer: ByteBuffer, sampleCount: Int)
    external fun nativeReset(handle: Long)
    external fun nativeRelease(handle: Long)
}
