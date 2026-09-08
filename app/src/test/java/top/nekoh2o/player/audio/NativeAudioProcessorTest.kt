package top.nekoh2o.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.nekoh2o.player.data.model.AudioEffectEngine
import top.nekoh2o.player.data.model.AudioEffectSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class NativeAudioProcessorTest {
    private class Backend : NativeEffectBackend {
        var created = 0
        var released = 0
        var configured = 0
        var format: Pair<Int, Int>? = null
        var failure: Throwable? = null
        override fun create(sampleRate: Int, channelCount: Int): Long {
            created++
            format = sampleRate to channelCount
            return created.toLong()
        }
        override fun configure(handle: Long, settings: AudioEffectSettings) { configured++ }
        override fun process(handle: Long, buffer: ByteBuffer, sampleCount: Int) {
            assertEquals(0, buffer.position())
            assertEquals(sampleCount * 2, buffer.remaining())
            assertTrue(buffer.isDirect)
            for (i in 0 until sampleCount) buffer.putShort(i * 2, (buffer.getShort(i * 2) / 2).toShort())
            failure?.let { throw it }
        }
        override fun reset(handle: Long) {}
        override fun release(handle: Long) { released++ }
    }

    private val backend = Backend()
    private var error: String? = null
    private val processor = NativeAudioProcessor(backend) { error = it }
    private val native = AudioEffectSettings(engine = AudioEffectEngine.NATIVE_CPP)

    private fun prepare(channels: Int = 2, rate: Int = 44100) {
        processor.configure(AudioFormat(rate, channels, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.applySettings(native)
    }

    private fun input(vararg values: Int): ByteBuffer =
        ByteBuffer.allocateDirect(values.size * 2).order(ByteOrder.nativeOrder()).apply {
            values.forEach { putShort(it.toShort()) }; flip()
        }

    private fun output(): List<Int> {
        val buffer = processor.output
        return buildList { while (buffer.hasRemaining()) add(buffer.short.toInt()) }
    }

    @Test fun consumesInputWithNonzeroPositionAndReturnsReadableOutputOnce() {
        prepare()
        val input = input(900, 800, 100, -200, 300, -400).apply { position(4) }
        processor.queueInput(input)
        assertEquals(input.limit(), input.position())
        assertEquals(listOf(50, -100, 150, -200), output())
        assertFalse(processor.output.hasRemaining())
    }

    @Test fun eosRetainsPendingOutputAndFlushAllowsAnotherTrack() {
        prepare()
        processor.queueInput(input(100, 200))
        processor.queueEndOfStream()
        assertFalse(processor.isEnded)
        assertEquals(listOf(50, 100), output())
        assertTrue(processor.isEnded)
        processor.flush()
        processor.queueInput(input(200, 400))
        assertEquals(listOf(100, 200), output())
        assertEquals(2, backend.created)
        assertEquals(1, backend.released)
    }

    @Test fun pendingOutputIsNotOverwritten() {
        prepare()
        processor.queueInput(input(100, 200))
        val next = input(300, 400)
        processor.queueInput(next)
        assertEquals(0, next.position())
        assertEquals(listOf(50, 100), output())
        processor.queueInput(next)
        assertEquals(listOf(150, 200), output())
    }

    @Test fun switchingEnginesUsesSameProcessorAndBypassesExactly() {
        prepare()
        processor.queueInput(input(100, -200)); output()
        processor.applySettings(native.copy(engine = AudioEffectEngine.SYSTEM))
        processor.queueInput(input(-32768, 32767))
        assertEquals(listOf(-32768, 32767), output())
        assertEquals(1, backend.released)
        processor.applySettings(native)
        processor.queueInput(input(100, 200))
        assertEquals(listOf(50, 100), output())
        assertEquals(2, backend.created)
    }

    @Test fun configureTakesEffectOnFlushAndReleasesOldFormat() {
        prepare()
        processor.queueInput(input(100, 200)); output()
        processor.configure(AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.queueInput(input(100, 200, 300))
        assertEquals(listOf(50, 100, 150), output())
        assertEquals(48000 to 1, backend.format)
        processor.reset()
        processor.reset()
        assertEquals(2, backend.released)
    }

    @Test fun nativeFailureRestoresInputAndDoesNotRetryEveryBuffer() {
        prepare()
        backend.failure = UnsatisfiedLinkError("test JNI mismatch")
        processor.queueInput(input(100, -200))
        assertEquals(listOf(100, -200), output())
        assertNotNull(error)
        processor.queueInput(input(300, -400))
        assertEquals(listOf(300, -400), output())
        assertEquals(1, backend.created)
        backend.failure = null
        processor.applySettings(native.copy(bassBoost = 10))
        processor.queueInput(input(100, -200))
        assertEquals(listOf(50, -100), output())
        assertNull(error)
    }

    @Test fun unsupportedChannelsAndPartialFramesPassThrough() {
        prepare(channels = 6)
        processor.queueInput(input(1, 2, 3, 4, 5, 6))
        assertEquals(listOf(1, 2, 3, 4, 5, 6), output())
        assertEquals(0, backend.created)
        assertNotNull(error)
        prepare()
        processor.queueInput(input(123))
        assertEquals(listOf(123), output())
        assertEquals(0, backend.created)
    }

    @Test fun emptyInputDoesNotInitializeNativeAndSettingsAreOnlyAppliedWhenChanged() {
        prepare()
        processor.queueInput(input())
        assertEquals(0, backend.created)
        repeat(2) { processor.queueInput(input(100, 200)); output() }
        assertEquals(1, backend.configured)
        processor.applySettings(native.copy(bassBoost = 50))
        processor.queueInput(input(100, 200)); output()
        assertEquals(2, backend.configured)
    }

    @Test fun settingsNormalizeNonfiniteAndLegacyValues() {
        val safe = native.copy(eqBands = listOf(Float.NaN, Float.POSITIVE_INFINITY, 99f),
            bassBoost = -2, masteringPresetId = 999, masteringMix = 101).normalized()
        assertEquals(listOf(0f, 0f, 15f) + List(7) { 0f }, safe.eqBands)
        assertEquals(0, safe.bassBoost)
        assertEquals(0, safe.masteringPresetId)
        assertEquals(100, safe.masteringMix)
    }
}
