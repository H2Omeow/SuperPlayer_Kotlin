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

    // ========== 分类搜索（歌手 type=100 / 专辑 type=10）==========
    @GET("api/search")
    suspend fun searchArtist(
        @Query("keywords") kw: String,
        @Query("type") type: Int = 100,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): SearchArtistResp

    @GET("api/search")
    suspend fun searchAlbum(
        @Query("keywords") kw: String,
        @Query("type") type: Int = 10,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): SearchAlbumResp

    // ========== 歌手热门歌曲 ==========
    @GET("api/artist/top/song")
    suspend fun artistTopSong(@Query("id") id: Long): ArtistTopSongResp

    // ========== 专辑内容 ==========
    @GET("api/album")
    suspend fun albumContent(@Query("id") id: Long): AlbumContentResp

    // ========== 网易云账号信息 ==========
    @GET("api/user/account")
    suspend fun userAccount(@Query("cookie") cookie: String): NcAccountResp

    // ========== 登录状态检测 ==========
    @GET("api/login/status")
    suspend fun loginStatus(@Query("cookie") cookie: String): LoginStatusResp

    // ========== 网易云用户歌单 ==========
    @GET("api/user/playlist")
    suspend fun userPlaylist(
        @Query("uid") uid: Long,
        @Query("cookie") cookie: String,
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): NcPlaylistResp

    // ========== 喜欢音乐列表（红心歌曲 ID）==========
    @GET("api/likelist")
    suspend fun likeList(
        @Query("uid") uid: Long,
        @Query("cookie") cookie: String
    ): LikeListResp

    // ========== 用户播放记录 ==========
    @GET("api/user/record")
    suspend fun userRecord(
        @Query("uid") uid: Long,
        @Query("cookie") cookie: String,
        @Query("type") type: Int = 1  // 1=周 0=所有
    ): UserRecordResp
}
