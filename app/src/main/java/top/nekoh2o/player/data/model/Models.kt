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

// ==================== 分类搜索 ====================
// 搜索类型，对应网易云 /search?type= 参数
enum class SearchType(val code: Int, val label: String) {
    SONG(1, "单曲"),
    ARTIST(100, "歌手"),
    ALBUM(10, "专辑")
}

// 分类搜索 - 歌手 (type=100)
@Serializable
data class SearchArtistResp(
    val code: Int = 0,
    val result: SearchArtistResult? = null
)
@Serializable
data class SearchArtistResult(
    val artists: List<ArtistItem> = emptyList()
)
@Serializable
data class ArtistItem(
    val id: Long,
    val name: String = "",
    val picUrl: String? = null,
    @SerialName("img1v1Url") val img1v1Url: String? = null
)

// 分类搜索 - 专辑 (type=10)
@Serializable
data class SearchAlbumResp(
    val code: Int = 0,
    val result: SearchAlbumResult? = null
)
@Serializable
data class SearchAlbumResult(
    val albums: List<AlbumItem> = emptyList()
)
@Serializable
data class AlbumItem(
    val id: Long,
    val name: String = "",
    val picUrl: String? = null,
    val artist: Artist? = null
)

// ==================== 歌手热门歌曲 /artist/top/song?id= ====================
@Serializable
data class ArtistTopSongResp(
    val code: Int = 0,
    val songs: List<DetailSong> = emptyList()
)

// ==================== 专辑内容 /album?id= ====================
@Serializable
data class AlbumContentResp(
    val code: Int = 0,
    val album: AlbumInfo? = null,
    val songs: List<DetailSong> = emptyList()
)
@Serializable
data class AlbumInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String? = null
)

// ==================== 已缓存歌曲信息（缓存管理用）====================
data class CachedItem(
    val key: String,
    val song: Song? = null,
    val sizeBytes: Long = 0L
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
    val fpBlurRadius: Int = 20,
    // 歌曲缓存开关（默认开启）
    val cacheEnabled: Boolean = true,
    // 控件透明度：0 完全透明，1 不透明；与个性化联动
    val controlAlpha: Float = 0.3f,
    // 播放倍速（0.5x ~ 2.0x），跨曲保持
    val playbackSpeed: Float = 1.0f,
    // 悬浮窗歌词开关（需悬浮窗权限）
    val floatingLyricEnabled: Boolean = false,
    // 悬浮窗歌词：显示双行（当前行 + 下一行）
    val floatingLyricDoubleRow: Boolean = false,
    // 悬浮窗歌词：显示翻译行（若歌词含翻译）
    val floatingLyricShowTranslation: Boolean = true,
    // 下载目录：SAF tree URI 字符串（空串 = 默认 Music/NekoPlayer/）
    val downloadDirUri: String = ""
)
