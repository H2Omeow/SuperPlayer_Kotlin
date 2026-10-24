package top.nekoh2o.player.data.net

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query

interface AnimemusicApi {
    @GET("index.php?route=music/mv/search")
    suspend fun mvSearch(@Query("platform") platform: String, @Query("keyword") keyword: String,
        @Query("page") page: Int = 1, @Query("limit") limit: Int = 30): AnimemusicSearchResponse

    @GET("index.php?route=music/mv/url")
    suspend fun mvUrl(@Query("platform") platform: String, @Query("id") id: String,
        @Query("quality") quality: String): AnimemusicUrlResponse
    @GET("index.php?route=music/search")
    suspend fun search(
        @Query("platform") platform: String,
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): AnimemusicSearchResponse

    @GET("index.php?route=music/url")
    suspend fun url(
        @Query("source") source: String,
        @Query("musicId") musicId: String,
        @Query("quality") quality: String
    ): AnimemusicUrlResponse

    @GET("index.php?route=music/lyric")
    suspend fun lyric(
        @Query("platform") platform: String,
        @Query("musicId") musicId: String,
        @Query("name") name: String = "",
        @Query("interval") interval: Int = 200
    ): AnimemusicLyricResponse

    @GET("index.php?route=music/lyric/word")
    suspend fun lyricWord(
        @Query("platform") platform: String,
        @Query("musicId") musicId: String,
        @Query("name") name: String = "",
        @Query("interval") interval: Int = 200
    ): AnimemusicLyricResponse
}

@kotlinx.serialization.Serializable
data class AnimemusicSearchResponse(
    val code: Int = 0,
    val platform: String = "",
    val data: List<AnimemusicSong> = emptyList(),
    val isEnd: Boolean = true
)

@kotlinx.serialization.Serializable
data class AnimemusicSong(
    val id: String = "",
    val songId: String = "",
    val album_audio_id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val duration: Long = 0,
    val _src: String = ""
)

@kotlinx.serialization.Serializable
data class AnimemusicUrlResponse(
    val code: Int = 0,
    val url: String? = null,
    val message: String? = null,
    val quality: String = "",
    val size: Long = 0,
    val freeTrialInfo: JsonElement? = null,
    val is_free_part: Int = 0
)

@kotlinx.serialization.Serializable
data class AnimemusicLyricResponse(
    val code: Int = 0,
    val lyric: String? = null,
    val tlyric: String? = null,
    val format: String? = null
)
