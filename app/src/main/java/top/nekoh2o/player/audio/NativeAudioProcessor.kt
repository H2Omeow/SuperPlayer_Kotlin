package top.nekoh2o.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 方案 B：Native C++ 音效处理器
 *
 * 集成到 Media3 ExoPlayer 的音频处理管线中，对 PCM 音频数据进行实时处理。
 * 使用自研 DSP 算法，提供全设备一致的专业音质体验。
 */
@UnstableApi
class NativeAudioProcessor : AudioProcessor {
    private var inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var isActive = false
    private var pendingOutputBuffer = AudioProcessor.EMPTY_BUFFER
    private var isInitialized = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        this.inputAudioFormat = inputAudioFormat

        // 仅支持 16-bit 立体声 PCM
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == 2) {

            // 初始化 Native 引擎
            if (NativeAudioEffectsController.nativeInit(inputAudioFormat.sampleRate)) {
                isActive = true
                isInitialized = true
                outputAudioFormat = inputAudioFormat
            } else {
                isActive = false
                outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
            }
        } else {
            isActive = false
            outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        }

        return outputAudioFormat
    }

    override fun isActive(): Boolean = isActive

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive || !isInitialized) return

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // ByteBuffer → ShortArray
        val samples = ShortArray(remaining / 2)
        inputBuffer.asShortBuffer().get(samples)

        // Native 处理
        NativeAudioEffectsController.nativeProcessSamples(samples)

        // ShortArray → ByteBuffer
        val outputBuffer = replaceOutputBuffer(samples.size * 2)
        outputBuffer.asShortBuffer().put(samples)
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer = pendingOutputBuffer

    override fun queueEndOfStream() {
        // Native 处理无缓冲延迟，直接标记结束
        pendingOutputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun isEnded(): Boolean =
        pendingOutputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        pendingOutputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        isActive = false
        if (isInitialized) {
            NativeAudioEffectsController.nativeRelease()
            isInitialized = false
        }
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (pendingOutputBuffer.capacity() < size) {
            pendingOutputBuffer = ByteBuffer.allocateDirect(size)
                .order(ByteOrder.nativeOrder())
        } else {
            pendingOutputBuffer.clear()
        }
        return pendingOutputBuffer
    }

    /**
     * 应用音效设置到 Native 引擎
     */
    fun applySettings(settings: top.nekoh2o.player.data.model.AudioEffectSettings) {
        if (!isInitialized) return

        NativeAudioEffectsController.nativeSetEqBands(settings.eqBands.toFloatArray())
        NativeAudioEffectsController.nativeSetBassBoost(settings.bassBoost)
        NativeAudioEffectsController.nativeSetVirtualizer(settings.virtualizer)
        NativeAudioEffectsController.nativeSetReverbWet(settings.reverbWet)
        NativeAudioEffectsController.nativeSetLoudnessGain(settings.loudnessGain)
    }
}
