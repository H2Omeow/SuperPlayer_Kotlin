package top.nekoh2o.player.data.net

import retrofit2.http.GET
import retrofit2.http.Query
import top.nekoh2o.player.data.model.*

/**
 * 酷狗音乐 API 接口
 * 基础路径: https://player.nekoh2o.top/kgapi/
 */
interface KugouApi {

    // ==================== 登录相关 ====================

    /**
     * 发送验证码
     * @param phone 手机号
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/send_code")
    suspend fun sendCode(
        @Query("phone") phone: String,
        @Query("platform") platform: Int = 0
    ): KgSendCodeResp

    /**
     * 手机号登录
     * @param phone 手机号
     * @param code 验证码
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/login")
    suspend fun login(
        @Query("phone") phone: String,
        @Query("code") code: String,
        @Query("platform") platform: Int = 0
    ): KgLoginResp

    /**
     * 刷新登录
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/refresh_login")
    suspend fun refreshLogin(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgRefreshLoginResp

    /**
     * 获取 DFID（设备指纹）
     */
    @GET("kgapi/dfid")
    suspend fun getDfid(): KgDfidResp

    // ==================== 用户信息 ====================

    /**
     * 获取用户额外信息
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/user/info")
    suspend fun getUserInfo(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgUserInfoResp

    /**
     * 获取用户VIP信息
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/user/vip")
    suspend fun getVipInfo(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgVipInfoResp

    // ==================== 歌单相关 ====================

    /**
     * 获取用户歌单
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/user/playlist")
    suspend fun getUserPlaylist(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgUserPlaylistResp

    /**
     * 获取歌单详情
     * @param specialId 歌单ID
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/playlist/detail")
    suspend fun getPlaylistDetail(
        @Query("specialid") specialId: Long,
        @Query("platform") platform: Int = 0
    ): KgPlaylistDetailResp

    /**
     * 收藏/创建歌单
     * @param token 用户token
     * @param specialName 歌单名称
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/playlist/collect")
    suspend fun collectPlaylist(
        @Query("token") token: String,
        @Query("specialname") specialName: String,
        @Query("platform") platform: Int = 0
    ): KgPlaylistDetailResp

    // ==================== 搜索相关 ====================

    /**
     * 综合搜索
     * @param keyword 搜索关键词
     * @param page 页码（从1开始）
     * @param pagesize 每页数量
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pagesize: Int = 30,
        @Query("platform") platform: Int = 0
    ): KgSearchResp

    /**
     * 搜索建议
     * @param keyword 搜索关键词
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/search/suggest")
    suspend fun searchSuggest(
        @Query("keyword") keyword: String,
        @Query("platform") platform: Int = 0
    ): KgSuggestResp

    /**
     * 热搜列表
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/search/hot")
    suspend fun getHotSearch(
        @Query("platform") platform: Int = 0
    ): KgSuggestResp

    // ==================== 歌曲相关 ====================

    /**
     * 获取歌曲详情
     * @param hash 歌曲hash或audio_id
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/song/info")
    suspend fun getSongDetail(
        @Query("hash") hash: String,
        @Query("platform") platform: Int = 0
    ): KgSongDetailResp

    /**
     * 获取音乐播放URL
     * @param hash 歌曲hash
     * @param quality 音质：128/320/flac
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/song/url")
    suspend fun getSongUrl(
        @Query("hash") hash: String,
        @Query("quality") quality: String = "320",
        @Query("platform") platform: Int = 0
    ): KgSongUrlResp

    /**
     * 获取歌词
     * @param hash 歌曲hash
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/lyric")
    suspend fun getLyric(
        @Query("hash") hash: String,
        @Query("platform") platform: Int = 0
    ): KgLyricResp

    // ==================== VIP领取（概念版）====================

    /**
     * 领取VIP（概念版专用）
     * @param token 用户token
     * @param vipType VIP类型：1=VIP, 2=豪华VIP
     * @param days 领取天数
     */
    @GET("kgapi/vip/receive")
    suspend fun receiveVip(
        @Query("token") token: String,
        @Query("vip_type") vipType: Int = 1,
        @Query("days") days: Int = 7,
        @Query("platform") platform: Int = 1  // 概念版固定为1
    ): KgReceiveVipResp

    // ==================== 歌手相关 ====================

    /**
     * 获取用户关注的歌手
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/user/follow/artist")
    suspend fun getFollowArtists(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgFollowArtistResp

    /**
     * 获取歌手详情
     * @param singerId 歌手ID
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/artist/info")
    suspend fun getArtistDetail(
        @Query("singerid") singerId: Long,
        @Query("platform") platform: Int = 0
    ): KgArtistDetailResp

    /**
     * 获取歌手歌曲列表
     * @param singerId 歌手ID
     * @param page 页码
     * @param pagesize 每页数量
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/artist/song")
    suspend fun getArtistSongs(
        @Query("singerid") singerId: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pagesize: Int = 30,
        @Query("platform") platform: Int = 0
    ): KgSearchResp

    // ==================== 音乐历史 ====================

    /**
     * 获取用户听歌历史
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/user/history")
    suspend fun getUserHistory(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgHistoryResp

    /**
     * 获取推荐歌曲（私人FM）
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/recommend/songs")
    suspend fun getRecommendSongs(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0
    ): KgSearchResp
}
