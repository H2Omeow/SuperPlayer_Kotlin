package top.nekoh2o.player.ui.lyrics

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.nekoh2o.player.data.model.LyricLine
import top.nekoh2o.player.data.model.LyricWord
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.ui.UiState

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DynamicLyricsPayloadTest {
    @Test fun preservesTimingTranslationAndRateWithoutSendingMediaUrls() {
        val state = UiState(
            queue = listOf(Song(1, "中文歌曲", "歌手", pc = "https://private.invalid/?token=secret")),
            currentIndex = 0,
            positionMs = 2200,
            durationMs = 120000,
            isPlaying = true,
            lyrics = listOf(
                LyricLine(1.0, "中文歌词", listOf(LyricWord("中文", 1.0, 1.5)), "Translation"),
                LyricLine(4.0, "下一行")
            )
        )
        val value = dynamicLyricsPayload(state, 1792850000123)
        val line = value.getJSONObject("lyrics").getJSONArray("lines").getJSONObject(0)
        assertEquals(1.0, line.getDouble("startTime"), 0.0)
        assertEquals(4.0, line.getDouble("endTime"), 0.0)
        assertEquals("Translation", line.getString("translation"))
        assertEquals(2.5, line.getJSONArray("words").getJSONObject(0).getDouble("endTime"), 0.0)
        assertEquals(2.2, value.getJSONObject("clock").getDouble("positionSec"), 0.0)
        assertEquals(1792850000123, value.getJSONObject("clock").getLong("anchoredAtMs"))
        assertTrue(value.getJSONObject("clock").getBoolean("playing"))
        assertFalse(value.toString().contains("secret"))
        assertTrue(value.getJSONObject("track").isNull("coverUrl"))
        assertTrue(value.getJSONObject("track").getString("seed").matches(Regex("[0-9a-f]{64}")))
        val paused = dynamicLyricsPayload(state.copy(isPlaying = false, positionMs = 80000))
        assertFalse(paused.getJSONObject("clock").getBoolean("playing"))
        assertEquals(80.0, paused.getJSONObject("clock").getDouble("positionSec"), 0.0)
    }

    @Test fun missingTrackClearsEngineAndCannotPlay() {
        val value = dynamicLyricsPayload(UiState(isPlaying = true))
        assertTrue(value.isNull("track"))
        assertTrue(value.isNull("lyrics"))
        assertFalse(value.getJSONObject("clock").getBoolean("playing"))
    }

    @Test fun lastLineHasValidEndWithoutDurationOrWordTiming() {
        val state = UiState(queue = listOf(Song(1, "曲", "歌手")), currentIndex = 0,
            lyrics = listOf(LyricLine(8.0, "末行")))
        val line = dynamicLyricsPayload(state).getJSONObject("lyrics").getJSONArray("lines").getJSONObject(0)
        assertEquals(13.0, line.getDouble("endTime"), 0.0)
    }
}
