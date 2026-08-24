package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.model.Playlist
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.model.User
import top.nekoh2o.player.data.model.UserData
import top.nekoh2o.player.data.model.NcAccountResp
import top.nekoh2o.player.data.model.NcPlaylistItem
import top.nekoh2o.player.data.model.NcRecordItem
import top.nekoh2o.player.data.net.ApiFactory

class UserRepository {

    private val api = ApiFactory.user

    suspend fun fetchMe(): User? =
        runCatching {
            val resp = api.me()
            android.util.Log.d("UserRepository", "fetchMe() - response code: ${resp.code}, user: ${resp.user}")
            if (resp.code == 0) resp.user else {
                android.util.Log.w("UserRepository", "fetchMe() - non-zero code: ${resp.code}")
                null
            }
        }.onFailure { e ->
            android.util.Log.e("UserRepository", "fetchMe() - network/parse error: ${e.message}", e)
        }.getOrNull()

    suspend fun pull(): UserData? =
        runCatching {
            val resp = api.pullData()
            if (resp.code == 0) resp.data else null
        }.getOrNull()

    suspend fun push(
        history: List<Song>,
        favorites: List<Song>,
        playlists: List<Playlist>,
        ncCookie: String
    ) {
        runCatching {
            api.pushData(UserData(history, favorites, playlists, ncCookie))
        }
    }

    suspend fun logout() {
        runCatching { api.logout() }
    }

    // 竖屏随机取一张壁纸，返回完整 URL；失败兜底 picsum
    suspend fun randomWallpaper(): String {
        val fallback = "https://picsum.photos/1080/1920?random=" +
            (System.currentTimeMillis() % 100000)
        return runCatching {
            val resp = api.wallpaperList("vertical")
            if (resp.code == 200 && resp.data.isNotEmpty()) {
                val item = resp.data.random()
                ApiFactory.BASE.trimEnd('/') + item.url
            } else fallback
        }.getOrDefault(fallback)
    }

    // ==================== 网易云相关 ====================

    private val ncApi = ApiFactory.netease

    /**
     * 检测网易云登录状态
     * @return true 表示 Cookie 有效
     */
    suspend fun checkNcLoginStatus(): Boolean =
        runCatching {
            val resp = ncApi.loginStatus()
            resp.data?.code == 200
        }.getOrDefault(false)

    /**
     * 获取网易云账号信息（含会员状态）
     */
    suspend fun fetchNcAccount(): NcAccountResp? =
        runCatching {
            ncApi.userAccount()
        }.getOrNull()

    /**
     * 获取网易云用户歌单列表
     */
    suspend fun fetchNcPlaylists(uid: Long): List<NcPlaylistItem> =
        runCatching {
            val resp = ncApi.userPlaylist(uid)
            if (resp.code == 200) resp.playlist else emptyList()
        }.getOrDefault(emptyList())

    /**
     * 获取网易云红心歌曲 ID 列表
     */
    suspend fun fetchNcLikeList(uid: Long): List<Long> =
        runCatching {
            val resp = ncApi.likeList(uid)
            if (resp.code == 200) resp.ids else emptyList()
        }.getOrDefault(emptyList())

    /**
     * 获取网易云播放记录
     * @param type 1=最近一周 0=所有时间
     */
    suspend fun fetchNcRecord(uid: Long, type: Int = 1): List<top.nekoh2o.player.data.model.NcRecordItem> =
        runCatching {
            val resp = ncApi.userRecord(uid, type)
            if (resp.code == 200) {
                if (type == 1) resp.weekData else resp.allData
            } else emptyList()
        }.getOrDefault(emptyList())
}
