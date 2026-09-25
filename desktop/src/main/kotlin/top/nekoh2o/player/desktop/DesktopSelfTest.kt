package top.nekoh2o.player.desktop

import top.nekoh2o.player.data.model.*
import java.nio.*
import java.nio.file.*
import java.io.*
import java.util.concurrent.*
import javax.sound.sampled.*
import kotlin.math.*

internal object DesktopSelfTest {
    fun fixture(path: Path, seconds: Int = 1) {
        val pcm = ByteBuffer.allocate(48000 * seconds * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 48000 * seconds) { val sample = (sin(i * 440.0 * 2 * PI / 48000) * 4000).toInt().toShort(); pcm.putShort(sample); pcm.putShort(sample) }
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(pcm.array()), AudioFormat(48000f,16,2,true,false),48000L * seconds), AudioFileFormat.Type.WAVE,path.toFile())
    }
    fun run() {
        val file = Files.createTempFile("nekoplayer-selftest-", ".wav")
        try {
            fixture(file)
            val duration = Decoder.duration(file); check(duration in 0.99..1.01) { "Decoder metadata failure" }
            for (effects in listOf(AudioEffectSettings(), AudioEffectSettings(engine = AudioEffectEngine.NATIVE_CPP, bassBoost = 20, masteringPresetId = 15))) {
                val done = CountDownLatch(1); var failure: Throwable? = null; var samples = 0L; var peak = 0
                val player = DesktopPlayer { object : PcmOutput {
                    override val playedFrames get() = samples / 2
                    override fun write(bytes: ByteArray, count: Int) {
                        val buffer = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN)
                        while (buffer.remaining() >= 2) { peak = max(peak, abs(buffer.short.toInt())); samples++ }
                    }
                    override fun pause(paused: Boolean) = Unit
                    override fun finish() = Unit
                    override fun close() = Unit
                } }
                player.effects = effects; player.onEnd = { done.countDown() }; player.onError = { failure = it; done.countDown() }
                try {
                    player.play(file); check(done.await(20, TimeUnit.SECONDS)) { "Decoder/DSP playback timed out" }
                    failure?.let { throw it }; check(samples == 96000L) { "Unexpected decoded sample count: " + samples }; check(peak in 100..16000) { "Invalid PCM peak: " + peak }
                } finally { player.close() }
            }
            println("PASS: FFmpeg decode, duration, stereo PCM, native DSP, graceful shutdown")
        } finally { Files.deleteIfExists(file) }
    }
}
