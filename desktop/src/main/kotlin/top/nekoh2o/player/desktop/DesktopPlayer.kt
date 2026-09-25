package top.nekoh2o.player.desktop

import kotlinx.coroutines.*
import okhttp3.*
import top.nekoh2o.player.audio.JniEffectBackend
import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.ApiFactory
import java.io.*
import java.nio.*
import java.nio.file.*
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { c ->
    c.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (c.isActive) c.resumeWithException(e) }
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun onResponse(call: Call, response: Response) { c.resume(response) { response.close() } }
    })
}

class MediaFiles(private val root: Path = DesktopPaths.home.resolve("cache")) {
    private val client = ApiFactory.mediaClient()
    @OptIn(InternalCoroutinesApi::class)
    suspend fun fetch(url: String, progress: (Long, Long) -> Unit = { _, _ -> }): Path {
        require(url.startsWith("https://") || url.startsWith("http://")) { "不支持的音频地址" }
        Files.createDirectories(root)
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val target = root.resolve(key + ".audio")
        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()))
            return target
        }
        val temp = Files.createTempFile(root, ".download-", ".part")
        val call = client.newCall(Request.Builder().url(url).build())
        val watcher = currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { if (it != null) call.cancel() }
        try {
            call.await().use { response ->
                if (!response.isSuccessful) throw IOException("音频服务器返回 HTTP " + response.code)
                val body = response.body ?: throw IOException("音频响应为空")
                val length = body.contentLength()
                require(length <= MAX_FILE) { "音频文件超过 512 MiB 限制" }
                body.byteStream().use { input -> Files.newOutputStream(temp).use { output ->
                    val buffer = ByteArray(64 * 1024); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= MAX_FILE) { "音频文件过大" }
                        output.write(buffer, 0, n); progress(total, length)
                    }
                    if (total == 0L || (length >= 0 && total != length)) throw IOException("音频下载不完整")
                } }
            }
            currentCoroutineContext().ensureActive()
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            trim(target)
            return target
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw e
        } finally { watcher?.dispose(); call.cancel(); Files.deleteIfExists(temp) }
    }
    private fun trim(keep: Path) {
        val entries = Files.newDirectoryStream(root, "*.audio").use { it.toList().sortedBy { p -> Files.getLastModifiedTime(p).toMillis() } }
        var total = entries.sumOf { Files.size(it) }
        for (path in entries) if (total > MAX_FILE && path != keep) {
            val size = Files.size(path)
            if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) total -= size
        }
    }
    companion object { const val MAX_FILE = 512L * 1024 * 1024 }
}

internal interface PcmOutput : Closeable {
    val playedFrames: Long
    fun write(bytes: ByteArray, count: Int)
    fun pause(paused: Boolean)
    fun finish()
}
private class SoundOutput : PcmOutput {
    private val line = AudioSystem.getSourceDataLine(AudioFormat(48000f, 16, 2, true, false)).apply {
        open(AudioFormat(48000f, 16, 2, true, false), 48000 / 5 * 4); start()
    }
    override val playedFrames get() = line.longFramePosition
    override fun write(bytes: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val n = line.write(bytes, offset, count - offset)
            if (n <= 0) throw IOException("音频设备已关闭")
            offset += n
        }
    }
    override fun pause(paused: Boolean) { if (paused) line.stop() else line.start() }
    override fun finish() = line.drain()
    override fun close() { line.stop(); line.flush(); line.close() }
}

internal object Decoder {
    fun executable(): String {
        System.getProperty("nekoplayer.ffmpeg")?.let { return it }
        val root = Paths.get(System.getProperty("nekoplayer.home", "."))
        val name = if (System.getProperty("os.name").startsWith("Windows")) "ffmpeg.exe" else "ffmpeg"
        return root.resolve("native").resolve(name).takeIf { Files.isRegularFile(it) }?.toAbsolutePath()?.toString() ?: name
    }
    fun start(path: Path, seconds: Double): Process = ProcessBuilder(executable(), "-hide_banner", "-loglevel", "error", "-nostdin",
        "-protocol_whitelist", "file,pipe", "-ss", seconds.coerceAtLeast(0.0).toString(), "-i", path.toAbsolutePath().toString(),
        "-vn", "-sn", "-dn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "48000", "-ac", "2", "pipe:1").start()
    fun duration(path: Path): Double {
        val process = ProcessBuilder(executable(), "-hide_banner", "-nostdin", "-protocol_whitelist", "file,pipe", "-i", path.toAbsolutePath().toString()).redirectErrorStream(true).start()
        val timeout = Thread { try { Thread.sleep(10000); if (process.isAlive) process.destroyForcibly() } catch (_: InterruptedException) {} }.apply { isDaemon = true; start() }
        val text = try { process.inputStream.bufferedReader().use { it.readText().take(16384) } } finally { process.destroyForcibly(); timeout.interrupt() }
        val m = Regex("""Duration: (\d+):(\d+):(\d+(?:\.\d+)?)""").find(text) ?: return 0.0
        return m.groupValues[1].toDouble() * 3600 + m.groupValues[2].toDouble() * 60 + m.groupValues[3].toDouble()
    }
}

internal class DesktopPlayer(private val outputFactory: () -> PcmOutput = { SoundOutput() }) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var process: Process? = null
    @Volatile private var output: PcmOutput? = null
    @Volatile private var offset = 0.0
    @Volatile var paused = false; private set
    @Volatile var effects = AudioEffectSettings()
    @Volatile var volume = 0.8f
    val position get() = offset + (output?.playedFrames ?: 0) / 48000.0
    var onEnd: () -> Unit = {}
    var onError: (Throwable) -> Unit = {}

    fun play(path: Path, seconds: Double = 0.0) {
        val previous = job
        previous?.cancel(); process?.destroy(); output?.close()
        job = scope.launch {
            previous?.join()
            offset = seconds; paused = false
            var decoder: Process? = null; var sink: PcmOutput? = null; var backend: JniEffectBackend? = null; var handle = 0L
            try {
                decoder = Decoder.start(path, seconds); process = decoder
                val errors = launch { decoder.errorStream.use { stream -> val b = ByteArray(1024); while (stream.read(b) >= 0) { ensureActive() } } }
                sink = outputFactory(); output = sink
                val pcm = ByteArray(8192); val direct = ByteBuffer.allocateDirect(pcm.size).order(ByteOrder.LITTLE_ENDIAN)
                var applied: AudioEffectSettings? = null
                var frames = 0L
                decoder.inputStream.use { input ->
                    while (isActive) {
                        while (paused) delay(30)
                        var count = 0
                        while (count < pcm.size) { val n = input.read(pcm, count, pcm.size - count); if (n < 0) break; count += n }
                        if (count == 0) break
                        require(count % 4 == 0) { "解码器返回不完整 PCM 帧" }
                        val settings = effects.normalized()
                        direct.clear(); direct.put(pcm, 0, count); direct.flip()
                        if (settings.engine == AudioEffectEngine.NATIVE_CPP) {
                            if (backend == null) { backend = JniEffectBackend(); handle = backend.create(48000, 2); check(handle != 0L) { "DSP 初始化失败" } }
                            if (settings != applied) { backend.configure(handle, settings); applied = settings }
                            backend.process(handle, direct, count / 2)
                        } else if (applied != null) { backend?.reset(handle); applied = null }
                        val gain = volume.coerceIn(0f, 1f)
                        for (i in 0 until count step 2) direct.putShort(i, (direct.getShort(i) * gain).toInt().coerceIn(-32768,32767).toShort())
                        direct.position(0); direct.get(pcm, 0, count)
                        sink.write(pcm, count); frames += count / 4
                    }
                }
                ensureActive(); decoder.waitFor(); errors.join()
                if (decoder.exitValue() != 0 || frames == 0L) throw IOException("音频解码失败，请检查文件格式或重新下载")
                sink.finish(); ensureActive(); onEnd()
            } catch (e: CancellationException) { throw e }
              catch (e: Throwable) { if (isActive) onError(e) }
            finally {
                if (handle != 0L) backend?.release(handle)
                sink?.close(); decoder?.destroyForcibly()
                if (process === decoder) { process = null; output = null }
            }
        }
    }
    fun pause() { paused = !paused; output?.pause(paused) }
    fun stop() { job?.cancel(); process?.destroy(); output?.close(); paused = false }
    override fun close() { stop(); scope.cancel() }
}
