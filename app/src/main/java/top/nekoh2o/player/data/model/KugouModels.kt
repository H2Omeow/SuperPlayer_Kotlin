package top.nekoh2o.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 酷狗音乐 API 数据模型
 */

// ==================== 登录相关 ====================

/**
 * 发送验证码响应
 */
@Serializable
data class KgSendCodeResp(
    val status: Int = 0,
    val error_msg: String = ""
)

/**
 * 登录响应（原版和概念版通用）
 */
@Serializable
data class KgLoginResp(
    val status: Int = 0,
    val error_msg: String = "",
    val data: KgLoginData? = null
)

@Serializable
data class KgLoginData(
    val token: String = "",
    val userid: Long = 0,
    val username: String = "",
    val dfid: String = ""
)

/**
 * 刷新登录响应
 */
@Serializable
data class KgRefreshLoginResp(
    val status: Int = 0,
    val data: KgRefreshData? = null
)

@Serializable
data class KgRefreshData(
    val token: String = ""
)

/**
 * DFID 获取响应
 */
@Serializable
data class KgDfidResp(
    val status: Int = 0,
    val data: KgDfidData? = null
)

@Serializable
data class KgDfidData(
    val dfid: String = ""
)

// ==================== 用户信息 ====================

/**
 * 用户额外信息
 */
@Serializable
data class KgUserInfoResp(
    val status: Int = 0,
    val data: KgUserInfo? = null
)

@Serializable
data class KgUserInfo(
    val userid: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = ""
)

/**
 * 用户VIP信息
 */
@Serializable
data class KgVipInfoResp(
    val status: Int = 0,
    val data: KgVipInfo? = null
)

@Serializable
data class KgVipInfo(
    @SerialName("vip_type") val vipType: Int = 0,  // 0=普通 1=VIP 2=豪华VIP
    @SerialName("vip_token") val vipToken: String = "",
    @SerialName("end_time") val endTime: Long = 0
)

// ==================== 歌单相关 ====================

/**
 * 用户歌单列表
 */
@Serializable
data class KgUserPlaylistResp(
    val status: Int = 0,
    val data: KgUserPlaylistData? = null
)

@Serializable
data class KgUserPlaylistData(
    val info: List<KgPlaylistItem> = emptyList()
)

@Serializable
data class KgPlaylistItem(
    @SerialName("specialid") val specialId: Long = 0,
    @SerialName("specialname") val specialName: String = "",
    val imgurl: String? = null,
    @SerialName("songcount") val songCount: Int = 0
)

/**
 * 歌单详情
 */
@Serializable
data class KgPlaylistDetailResp(
    val status: Int = 0,
    val data: KgPlaylistDetail? = null
)

@Serializable
data class KgPlaylistDetail(
    @SerialName("specialid") val specialId: Long = 0,
    @SerialName("specialname") val specialName: String = "",
    val imgurl: String? = null,
    val list: KgPlaylistSongList? = null
)

@Serializable
data class KgPlaylistSongList(
    val list: KgPlaylistSongs? = null
)

@Serializable
data class KgPlaylistSongs(
    val info: List<KgSongDetail> = emptyList()
)

// ==================== 搜索相关 ====================

/**
 * 综合搜索
 */
@Serializable
data class KgSearchResp(
    val status: Int = 0,
    val data: KgSearchData? = null
)

@Serializable
data class KgSearchData(
    val lists: List<KgSearchSong> = emptyList(),
    // 个人 FM 返回的字段
    @SerialName("song_list") val songList: List<KgFmSong> = emptyList()
)

@Serializable
data class KgSearchSong(
    @SerialName("FileHash") val fileHash: String = "",
    @SerialName("SongName") val songName: String = "",
    @SerialName("SingerName") val singerName: String = "",
    @SerialName("AlbumID") val albumId: String = "",
    @SerialName("Audioid") val audioId: Long = 0
)

/**
 * 个人 FM 歌曲
 */
@Serializable
data class KgFmSong(
    val songname: String = "",
    val songid: Long = 0,
    val hash: String = "",
    val singerinfo: List<KgSingerInfo> = emptyList()
)

@Serializable
data class KgSingerInfo(
    val name: String = "",
    val id: String = ""
)

/**
 * 搜索建议
 */
@Serializable
data class KgSuggestResp(
    val status: Int = 0,
    val data: KgSuggestData? = null
)

@Serializable
data class KgSuggestData(
    val song: List<KgSuggestSong> = emptyList()
)

@Serializable
data class KgSuggestSong(
    val songname: String = "",
    val singername: String = ""
)

// ==================== 歌曲相关 ====================

/**
 * 歌曲详情
 */
@Serializable
data class KgSongDetailResp(
    val status: Int = 0,
    val data: KgSongDetail? = null
)

@Serializable
data class KgSongDetail(
    @SerialName("audio_id") val audioId: Long = 0,
    @SerialName("hash") val hash: String = "",
    @SerialName("album_audio_id") val albumAudioId: Long = 0,
    @SerialName("song_name") val songName: String = "",
    @SerialName("author_name") val authorName: String = "",
    @SerialName("img") val img: String? = null
)

/**
 * 获取音乐URL
 */
@Serializable
data class KgSongUrlResp(
    val status: Int = 0,
    val data: KgSongUrlData? = null
)

@Serializable
data class KgSongUrlData(
    val play_url: String = "",
    val play_backup_url: String = "",
    val quality: String = "",  // 当前音质
    val bitrate: Int = 0  // 当前码率
)

/**
 * 获取歌曲可用音质列表
 */
@Serializable
data class KgQualityResp(
    val status: Int = 0,
    val data: KgQualityData? = null
)

@Serializable
data class KgQualityData(
    val qualities: List<KgQualityItem> = emptyList()
)

@Serializable
data class KgQualityItem(
    val quality: String = "",  // 128/320/flac等
    val name: String = "",  // 标准/高品/无损
    val bitrate: Int = 0
)

/**
 * 歌词
 */
@Serializable
data class KgLyricResp(
    val status: Int = 0,
    val data: KgLyricData? = null
)

@Serializable
data class KgLyricData(
    val lyrics: String = ""
)

// ==================== VIP领取（概念版）====================

/**
 * 领取VIP响应
 */
@Serializable
data class KgReceiveVipResp(
    val status: Int = 0,
    val error_msg: String = "",
    val data: KgReceiveVipData? = null
)

@Serializable
data class KgReceiveVipData(
    val result: String = ""
)

// ==================== 歌手相关 ====================

/**
 * 用户关注歌手列表
 */
@Serializable
data class KgFollowArtistResp(
    val status: Int = 0,
    val data: KgFollowArtistData? = null
)

@Serializable
data class KgFollowArtistData(
    val info: List<KgArtistItem> = emptyList()
)

@Serializable
data class KgArtistItem(
    @SerialName("singerid") val singerId: Long = 0,
    @SerialName("singername") val singerName: String = "",
    val imgurl: String? = null
)

/**
 * 歌手详情
 */
@Serializable
data class KgArtistDetailResp(
    val status: Int = 0,
    val data: KgArtistDetail? = null
)

@Serializable
data class KgArtistDetail(
    @SerialName("singerid") val singerId: Long = 0,
    @SerialName("singername") val singerName: String = "",
    val imgurl: String? = null,
    val intro: String = ""
)

// ==================== 音乐历史 ====================

/**
 * 用户听歌历史
 */
@Serializable
data class KgHistoryResp(
    val status: Int = 0,
    val data: KgHistoryData? = null
)

@Serializable
data class KgHistoryData(
    val info: List<KgHistoryItem> = emptyList()
)

@Serializable
data class KgHistoryItem(
    @SerialName("audio_id") val audioId: Long = 0,
    @SerialName("hash") val hash: String = "",
    @SerialName("song_name") val songName: String = "",
    @SerialName("author_name") val authorName: String = ""
)

// ==================== QQ登录相关 ====================

/**
 * QQ 授权登录响应
 */
@Serializable
data class KgQQLoginResp(
    val status: Int = 0,
    val error_msg: String = "",
    val data: KgLoginData? = null
)

/**
 * QQ 扫码登录 - 创建二维码响应
 * 注意：新版 API 直接返回扁平 JSON，不在 data 字段内
 */
@Serializable
data class KgQQQRCreateResp(
    val qrcode: String = "",
    val qrsig: String = "",
    val ptqrtoken: Long = 0,
    @SerialName("pt_login_sig") val ptLoginSig: String = "",
    @SerialName("pt_openlogin_data") val ptOpenloginData: String = "",
    @SerialName("xlogin_url") val xloginUrl: String = "",
    val cookie: String = ""
)

/**
 * QQ 扫码登录 - 用于 UI 展示的数据
 */
data class KgQQQRCreateData(
    val qrUrl: String = "",       // 二维码图片 data URL
    val qrId: String = "",        // 用于轮询的 ID（实际是 qrsig）
    // 保存完整的响应数据，用于后续 check 调用
    val fullResp: KgQQQRCreateResp? = null
)

/**
 * QQ 扫码登录 - 检查状态响应
 */
@Serializable
data class KgQQQRCheckResp(
    val status: Int = 0,  // 0=等待扫码, 1=登录成功, 2=二维码过期
    val error_msg: String = "",
    val data: KgLoginData? = null
)

