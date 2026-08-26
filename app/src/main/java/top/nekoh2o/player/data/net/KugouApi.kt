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
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/send_code")
    suspend fun sendCode(
        @Query("phone") phone: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgSendCodeResp

    /**
     * 手机号登录
     * @param phone 手机号
     * @param code 验证码
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/login")
    suspend fun login(
        @Query("phone") phone: String,
        @Query("code") code: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgLoginResp

    /**
     * 刷新登录
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/refresh_login")
    suspend fun refreshLogin(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgRefreshLoginResp

    /**
     * 获取 DFID（设备指纹）
     * 注意：很多接口需要先调用此接口获取 dfid
     */
    @GET("kgapi/register/dev")
    suspend fun getDfid(): KgDfidResp

    /**
     * QQ 授权登录
     * @param openid QQ 授权返回的 openid
     * @param accessToken QQ 授权返回的 access_token
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/login/qq")
    suspend fun loginWithQQ(
        @Query("openid") openid: String,
        @Query("access_token") accessToken: String,
        @Query("platform") platform: Int = 0
    ): KgLoginResp

    /**
     * QQ 扫码登录 - 生成二维码
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/login/qq/qr/create")
    suspend fun createQQLoginQR(
        @Query("platform") platform: Int = 0
    ): KgQQQRCreateResp

    /**
     * QQ 扫码登录 - 检测扫码状态
     * @param qrsig 二维码会话标识
     * @param ptqrtoken qrsig 的 hash33 值
     * @param ptLoginSig QQ 登录签名
     * @param ptOpenloginData xlogin 完整参数
     * @param xloginUrl xlogin 接口完整链接
     * @param cookie 会话 Cookie
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/login/qq/qr/check")
    suspend fun checkQQLoginQR(
        @Query("qrsig") qrsig: String,
        @Query("ptqrtoken") ptqrtoken: String,
        @Query("pt_login_sig") ptLoginSig: String,
        @Query("pt_openlogin_data") ptOpenloginData: String,
        @Query("xlogin_url") xloginUrl: String,
        @Query("cookie") cookie: String,
        @Query("platform") platform: Int = 0
    ): KgQQQRCheckResp

    // ==================== 用户信息 ====================

    /**
     * 获取用户额外信息
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/user/info")
    suspend fun getUserInfo(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgUserInfoResp

    /**
     * 获取用户VIP信息
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/user/vip")
    suspend fun getVipInfo(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgVipInfoResp

    // ==================== 歌单相关 ====================

    /**
     * 获取用户歌单
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/user/playlist")
    suspend fun getUserPlaylist(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgUserPlaylistResp

    /**
     * 获取歌单详情
     * @param specialId 歌单ID
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/playlist/detail")
    suspend fun getPlaylistDetail(
        @Query("specialid") specialId: Long,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
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
     * @param type 搜索类型：song=单曲，special=歌单，lyric=歌词，album=专辑，author=歌手，mv=mv
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）【必需，否则返回 error_code: 152】
     */
    @GET("kgapi/search")
    suspend fun search(
        @Query("keywords") keyword: String,
        @Query("page") page: Int = 1,
        @Query("pagesize") pagesize: Int = 30,
        @Query("type") type: String = "song",
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String
    ): KgSearchResp

    /**
     * 搜索建议
     * @param keyword 搜索关键词
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）【必需】
     */
    @GET("kgapi/search/suggest")
    suspend fun searchSuggest(
        @Query("keywords") keyword: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String
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
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/song/info")
    suspend fun getSongDetail(
        @Query("hash") hash: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgSongDetailResp

    /**
     * 获取音乐播放URL
     * @param hash 歌曲hash
     * @param quality 音质：128/320/flac/high等
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/song/url")
    suspend fun getSongUrl(
        @Query("hash") hash: String,
        @Query("quality") quality: String = "320",
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgSongUrlResp

    /**
     * 获取音乐播放URL（新版，返回所有音质）
     * @param hash 歌曲hash
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/song/url/new")
    suspend fun getSongUrlNew(
        @Query("hash") hash: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgQualityResp

    /**
     * 获取歌词
     * @param hash 歌曲hash
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/lyric")
    suspend fun getLyric(
        @Query("hash") hash: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgLyricResp

    // ==================== VIP领取（概念版）====================

    /**
     * 领取VIP（概念版专用）
     * @param token 用户token
     * @param vipType VIP类型：1=VIP, 2=豪华VIP
     * @param days 领取天数
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/vip/receive")
    suspend fun receiveVip(
        @Query("token") token: String,
        @Query("vip_type") vipType: Int = 1,
        @Query("days") days: Int = 7,
        @Query("platform") platform: Int = 1,  // 概念版固定为1
        @Query("cookie") cookie: String = ""
    ): KgReceiveVipResp

    // ==================== 歌手相关 ====================

    /**
     * 获取用户关注的歌手
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/user/follow/artist")
    suspend fun getFollowArtists(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgFollowArtistResp

    /**
     * 获取歌手详情
     * @param singerId 歌手ID
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/artist/info")
    suspend fun getArtistDetail(
        @Query("singerid") singerId: Long,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgArtistDetailResp

    /**
     * 获取歌手歌曲列表
     * @param singerId 歌手ID
     * @param page 页码
     * @param pagesize 每页数量
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/artist/song")
    suspend fun getArtistSongs(
        @Query("singerid") singerId: Long,
        @Query("page") page: Int = 1,
        @Query("pagesize") pagesize: Int = 30,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgSearchResp

    // ==================== 音乐历史 ====================

    /**
     * 获取用户听歌历史
     * @param token 用户token
     * @param platform 平台类型：0=原版，1=概念版
     * @param cookie 酷狗 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     */
    @GET("kgapi/user/history")
    suspend fun getUserHistory(
        @Query("token") token: String,
        @Query("platform") platform: Int = 0,
        @Query("cookie") cookie: String = ""
    ): KgHistoryResp

    /**
     * 获取推荐歌曲（私人FM）
     * 说明：私人 FM，对应手机和 PC 端的"猜你喜欢"
     * @param cookie 完整 cookie 字符串（格式：token=xxx;userid=xxx;dfid=xxx）
     * @param platform 平台类型：0=原版，1=概念版
     */
    @GET("kgapi/personal/fm")
    suspend fun getRecommendSongs(
        @Query("cookie") cookie: String,
        @Query("platform") platform: Int = 0
    ): KgSearchResp

    // ==================== QQ登录 ====================

    /**
     * QQ授权登录
     * @param openid QQ OpenID
     * @param accessToken QQ Access Token
     */
    @GET("kgapi/login/qq")
    suspend fun loginWithQQ(
        @Query("openid") openid: String,
        @Query("access_token") accessToken: String
    ): KgQQLoginResp

    /**
     * QQ扫码登录 - 创建二维码
     */
    @GET("kgapi/login/qq/qr/create")
    suspend fun createQQLoginQR(): KgQQQRCreateResp

    /**
     * QQ扫码登录 - 检查状态
     * @param qrId 二维码ID
     */
    @GET("kgapi/login/qq/qr/check")
    suspend fun checkQQLoginQR(
        @Query("qr_id") qrId: String
    ): KgQQQRCheckResp
}
