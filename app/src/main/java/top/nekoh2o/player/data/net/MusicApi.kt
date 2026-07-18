package top.nekoh2o.player.data.net

import top.nekoh2o.player.data.model.*
import retrofit2.http.GET
import retrofit2.http.Query

// 网易云 API（经 player.nekoh2o.top 的 /api 转发），返回 code=200
interface MusicApi {

    @GET("api/search")
    suspend fun search(
        @Query("keywords") kw: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): SearchResp

    @GET("api/search/suggest")
    suspend fun suggest(@Query("keywords") kw: String): SuggestResp

    @GET("api/song/detail")
    suspend fun songDetail(@Query("ids") ids: String): SongDetailResp

    @GET("api/song/url/v1")
    suspend fun songUrlV1(
        @Query("id") id: Long,
        @Query("level") level: String,
        @Query("cookie") cookie: String? = null
    ): SongUrlResp

    @GET("api/song/url")
    suspend fun songUrl(
        @Query("id") id: Long,
        @Query("br") br: Int,
        @Query("cookie") cookie: String? = null
    ): SongUrlResp

    @GET("api/lyric/new")
    suspend fun lyricNew(@Query("id") id: Long): LyricResp

    @GET("api/lyric")
    suspend fun lyric(@Query("id") id: Long): LyricResp

    @GET("api/personalized")
    suspend fun personalized(@Query("limit") limit: Int = 8): PersonalizedResp

    @GET("api/recommend/songs")
    suspend fun recommendSongs(): RecommendSongsResp

    @GET("api/top/song")
    suspend fun topSong(@Query("type") type: Int = 0, @Query("limit") limit: Int = 20): TopSongResp

    @GET("api/playlist/detail")
    suspend fun playlistDetail(@Query("id") id: Long): PlaylistDetailResp

    @GET("api/register/anonimous")
    suspend fun registerAnonymous(): AnonResp

    @GET("api/login/qr/key")
    suspend fun qrKey(@Query("timestamp") ts: Long): QrKeyResp

    @GET("api/login/qr/create")
    suspend fun qrCreate(
        @Query("key") key: String,
        @Query("qrimg") qrimg: Boolean = true,
        @Query("timestamp") ts: Long
    ): QrCreateResp

    @GET("api/login/qr/check")
    suspend fun qrCheck(
        @Query("key") key: String,
        @Query("timestamp") ts: Long
    ): QrCheckResp
}
