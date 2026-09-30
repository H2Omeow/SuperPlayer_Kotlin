package top.nekoh2o.player.data.net

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

interface AnimemusicApi {
    @GET("usage")
    suspend fun usage(): SourceUsage

    @GET("music/mv/search")
    suspend fun mvSearch(@Query("platform") platform: String, @Query("keyword") keyword: String,
        @Query("page") page: Int = 1, @Query("limit") limit: Int = 30): AnimemusicSearchResponse

    @GET("music/mv/url")
    suspend fun mvUrl(@Query("platform") platform: String, @Query("id") id: String,
        @Query("quality") quality: String): AnimemusicUrlResponse
    @GET("music/search")
    suspend fun search(
        @Query("platform") platform: String,
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): AnimemusicSearchResponse

    @GET("music/url")
    suspend fun url(
        @Query("source") source: String,
        @Query("musicId") musicId: String,
        @Query("quality") quality: String
    ): AnimemusicUrlResponse

    @GET("music/lyric")
    suspend fun lyric(
        @Query("source") source: String,
        @Query("musicId") musicId: String
    ): AnimemusicLyricResponse
}

@kotlinx.serialization.Serializable
data class SourceUsage(val code: Int = 0, val date: String = "", val limit: Int = 10000,
    val used: Int = 0, val remaining: Int = 0, val myToday: Int = 0, val users: List<SourceUserUsage> = emptyList())

@kotlinx.serialization.Serializable
data class SourceUserUsage(val name: String = "", val today: Int = 0, val total: Int = 0)

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
    val message: String? = null
)

@kotlinx.serialization.Serializable
data class AnimemusicLyricResponse(
    val code: Int = 0,
    val lyric: String? = null,
    val tlyric: String? = null,
    val format: String? = null
)
