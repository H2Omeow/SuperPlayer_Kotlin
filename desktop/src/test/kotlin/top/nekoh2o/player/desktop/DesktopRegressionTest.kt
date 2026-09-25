package top.nekoh2o.player.desktop

import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import top.nekoh2o.player.data.net.KugouSessionStore
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.*
import java.util.concurrent.*

class DesktopRegressionTest {
    @Test fun preferencesAndSeparateProviderSessionsSurviveRestart() {
        val folder = Files.createTempDirectory("desktop-prefs-")
        try {
            val path = folder.resolve("accounts.properties")
            val store = KugouSessionStore(FilePreferences(path))
            store.importCookie(0, "token=standard; userid=123; dfid=device-a")
            store.importCookie(1, "token=lite; userid=456; dfid=device-b")
            val restored = KugouSessionStore(FilePreferences(path))
            assertEquals("standard", restored.value(0, "token")); assertEquals("lite", restored.value(1,"token"))
            restored.clearLogin(0)
            assertEquals("device-a", restored.value(0,"dfid")); assertEquals("lite", restored.value(1,"token"))
            try { restored.importCookie(1,"token=bad\nheader; userid=123"); fail() } catch (_: IllegalArgumentException) {}
            assertEquals("lite", restored.value(1,"token"))
        } finally { folder.toFile().deleteRecursively() }
    }

    @Test fun interruptedDownloadDoesNotPublishPartialFile() = runBlocking {
        val root = Files.createTempDirectory("desktop-cache-")
        val server = MockWebServer()
        try {
            server.start()
            server.enqueue(MockResponse().setBody("x".repeat(30000)).throttleBody(1024, 1, TimeUnit.SECONDS))
            val media = MediaFiles(root)
            val job = launch(Dispatchers.IO) { media.fetch(server.url("/audio").toString()) }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            delay(100); job.cancelAndJoin()
            assertEquals(0, Files.list(root).use { it.count() }.toInt())
        } finally { server.shutdown(); root.toFile().deleteRecursively() }
    }

    @Test fun decoderAndNativeDspProcessRealPcm() { DesktopSelfTest.run() }

    @Test fun repeatedPlaybackCancellationDoesNotDeliverStaleCompletion() {
        val file = Files.createTempFile("desktop-stop-", ".wav")
        val firstWrite = CountDownLatch(1)
        val closed = CountDownLatch(1)
        var completed = false
        val player = DesktopPlayer { object : PcmOutput {
            override val playedFrames = 0L
            override fun write(bytes: ByteArray, count: Int) { firstWrite.countDown(); Thread.sleep(20) }
            override fun pause(paused: Boolean) = Unit
            override fun finish() = Unit
            override fun close() { closed.countDown() }
        } }
        try {
            DesktopSelfTest.fixture(file, 3); player.onEnd = { completed = true }
            player.play(file); assertTrue(firstWrite.await(5, TimeUnit.SECONDS)); player.stop()
            assertTrue(closed.await(3, TimeUnit.SECONDS)); Thread.sleep(200); assertFalse(completed)
        } finally { player.close(); Files.deleteIfExists(file) }
    }

    @Test fun libraryMergesProviderIdentitiesWithoutCollisions() {
        val root = Files.createTempDirectory("desktop-library-")
        try {
            val prefs = FilePreferences(root.resolve("prefs")); val library = Library(prefs)
            val nc = top.nekoh2o.player.data.model.Song(1,"song","artist")
            val kg = nc.copy(source = "kugou", hash = "a".repeat(32))
            library.favorite(nc); library.favorite(kg); library.played(nc); library.played(nc)
            assertEquals(2, Library(prefs).data.favorites.size); assertEquals(1, Library(prefs).data.history.size)
        } finally { root.toFile().deleteRecursively() }
    }
}
