package top.nekoh2o.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ==================== 播放器内部统一歌曲结构（对应 web {id,nm,ar,pc}）====================
@Serializable
data class Song(
    val id: Long,
    val nm: String,
    val ar: String,
    val pc: String? = null
)

// 自定义歌单（对应 web customPlaylists）
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: MutableList<Song> = mutableListOf()
)

// ==================== 搜索 /search?keywords= ====================
@Serializable
data class SearchResp(
    val code: Int = 0,
    val result: SearchResult? = null
)
@Serializable
data class SearchResult(
    val songs: List<SearchSong> = emptyList()
)
@Serializable
data class SearchSong(
    val id: Long,
    val name: String = "",
    val artists: List<Artist> = emptyList()
)
@Serializable
data class Artist(
    val name: String = "",
    @SerialName("img1v1Url") val img: String? = null
)

// ==================== 搜索建议 /search/suggest?keywords= ====================
@Serializable
data class SuggestResp(
    val code: Int = 0,
    val result: SuggestResult? = null
)
@Serializable
data class SuggestResult(
    val songs: List<Named> = emptyList(),
    val artists: List<Named> = emptyList(),
    val albums: List<Named> = emptyList()
)
@Serializable
data class Named(val name: String = "")

// ==================== 详情 /song/detail?ids= ====================
@Serializable
data class SongDetailResp(
    val code: Int = 0,
    val songs: List<DetailSong> = emptyList()
)
@Serializable
data class DetailSong(
    val id: Long,
    val name: String = "",
    val ar: List<Artist> = emptyList(),
    val al: Album? = null
)
@Serializable
data class Album(
    val name: String = "",
    val picUrl: String? = null
)

// ==================== 播放地址 /song/url/v1 与 /song/url ====================
@Serializable
data class SongUrlResp(
    val code: Int = 0,
    val data: List<SongUrlData> = emptyList()
)
@Serializable
data class SongUrlData(
    val id: Long = 0,
    val url: String? = null,
    val br: Int = 0
)

// ==================== 歌词 /lyric/new 与 /lyric ====================
@Serializable
data class LyricResp(
    val code: Int = 0,
    val lrc: LyricBlock? = null,
    val tlyric: LyricBlock? = null,
    val yrc: LyricBlock? = null
)
@Serializable
data class LyricBlock(val lyric: String = "")

// ==================== 推荐歌单 /personalized?limit= ====================
@Serializable
data class PersonalizedResp(
    val code: Int = 0,
    val result: List<PersonalizedItem> = emptyList()
)
@Serializable
data class PersonalizedItem(
    val id: Long,
    val name: String = "",
    val picUrl: String? = null
)

// ==================== 每日推荐 /recommend/songs ====================
@Serializable
data class RecommendSongsResp(
    val code: Int = 0,
    val data: RecommendData? = null
)
@Serializable
data class RecommendData(
    val dailySongs: List<DetailSong> = emptyList()
)

// ==================== 榜单兜底 /top/song?type=0 ====================
@Serializable
data class TopSongResp(
    val code: Int = 0,
    val data: List<DetailSong> = emptyList()
)

// ==================== 歌单详情 /playlist/detail?id= ====================
@Serializable
data class PlaylistDetailResp(
    val code: Int = 0,
    val playlist: PlaylistDetail? = null
)
@Serializable
data class PlaylistDetail(
    val name: String = "",
    val tracks: List<DetailSong> = emptyList()
)

// ==================== 游客 cookie /register/anonimous ====================
@Serializable
data class AnonResp(
    val code: Int = 0,
    val cookie: String = ""
)

// ==================== QR 登录 ====================
@Serializable
data class QrKeyResp(
    val code: Int = 0,
    val data: QrKeyData? = null
)
@Serializable
data class QrKeyData(val unikey: String = "")

@Serializable
data class QrCreateResp(
    val code: Int = 0,
    val data: QrCreateData? = null
)
@Serializable
data class QrCreateData(val qrimg: String = "")

@Serializable
data class QrCheckResp(
    val code: Int = 0,
    val cookie: String = "",
    val message: String = ""
)

// ==================== 用户信息 /auth/sso/me ====================
@Serializable
data class MeResp(
    val code: Int = -1,
    val user: User? = null
)
@Serializable
data class User(
    val id: String = "",
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val bio: String? = null
)

// ==================== 云端用户数据 /user/data ====================
@Serializable
data class UserDataResp(
    val code: Int = 0,
    val data: UserData? = null
)
@Serializable
data class UserData(
    val history: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val ncCookie: String = ""
)

// ==================== 壁纸 /local/wallpaper/list ====================
@Serializable
data class WallpaperResp(
    val code: Int = 0,
    val data: List<WallpaperItem> = emptyList()
)
@Serializable
data class WallpaperItem(
    val name: String = "",
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

// ==================== 个性化设置 ====================
enum class BgSource { WALLPAPER, COVER }

data class AppSettings(
    // App 全局背景（默认开，用壁纸）
    val globalBgEnabled: Boolean = true,
    val globalMaskAlpha: Float = 0.6f,
    val globalBlurRadius: Int = 15,
    // 全屏播放器背景
    val fpBgSource: BgSource = BgSource.COVER,
    val fpMaskAlpha: Float = 0.4f,
    val fpBlurRadius: Int = 20
)
