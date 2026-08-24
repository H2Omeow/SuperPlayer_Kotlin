package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.ApiFactory

/**
 * 网易云音乐相关数据仓库
 */
class NeteaseRepository {

    private val api = ApiFactory.netease

    /**
     * 检查网易云 Cookie 是否有效
     */
    suspend fun checkNcCookieValid(): Boolean =
        runCatching {
            val cookie = top.nekoh2o.player.data.net.CookieStore.userCookieValue()
            if (cookie.isEmpty()) return@runCatching false
            val resp = api.loginStatus(cookie)
            resp.data.code == 200 && resp.data.account != null
        }.getOrDefault(false)

    /**
     * 获取网易云账号信息（含会员状态）
     */
    suspend fun fetchNcAccount(): NcAccountResp? =
        runCatching {
            val cookie = top.nekoh2o.player.data.net.CookieStore.userCookieValue()
            if (cookie.isEmpty()) return@runCatching null
            val resp = api.userAccount(cookie)
            if (resp.code == 200) resp else null
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchNcAccount() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 获取用户歌单列表
     */
    suspend fun fetchNcPlaylists(uid: Long): List<NcPlaylistItem> =
        runCatching {
            val cookie = top.nekoh2o.player.data.net.CookieStore.userCookieValue()
            if (cookie.isEmpty()) return@runCatching emptyList()
            val resp = api.userPlaylist(uid, cookie)
            if (resp.code == 200) resp.playlist else emptyList()
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchNcPlaylists() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 获取红心歌曲 ID 列表
     */
    suspend fun fetchLikeList(uid: Long): List<Long> =
        runCatching {
            val cookie = top.nekoh2o.player.data.net.CookieStore.userCookieValue()
            if (cookie.isEmpty()) return@runCatching emptyList()
            val resp = api.likeList(uid, cookie)
            if (resp.code == 200) resp.ids else emptyList()
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchLikeList() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 获取播放记录（最近一周）
     */
    suspend fun fetchPlayRecord(uid: Long): List<NcRecordItem> =
        runCatching {
            val cookie = top.nekoh2o.player.data.net.CookieStore.userCookieValue()
            if (cookie.isEmpty()) return@runCatching emptyList()
            val resp = api.userRecord(uid, cookie, type = 1)
            if (resp.code == 200) resp.weekData else emptyList()
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchPlayRecord() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 根据 ID 批量获取歌曲并转换为 Song 对象
     */
    suspend fun fetchSongsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            val resp = api.songDetail(ids.joinToString(","))
            if (resp.code == 200) {
                resp.songs.map { it.toSong() }
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchSongsByIds() failed: ${e.message}", e)
        }.getOrDefault(emptyList())
    }

    /**
     * 获取网易云歌单的歌曲列表
     */
    suspend fun fetchPlaylistTracks(playlistId: Long): List<Song> {
        return runCatching {
            val resp = api.playlistDetail(playlistId)
            if (resp.code == 200) {
                resp.playlist?.trackIds?.mapNotNull { it.id }?.let { ids ->
                    fetchSongsByIds(ids)
                } ?: emptyList()
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("NeteaseRepository", "fetchPlaylistTracks() failed: ${e.message}", e)
        }.getOrDefault(emptyList())
    }

    /**
     * 将网易云歌曲详情转换为本地 Song 对象
     */
    private fun NcSongDetail.toSong(): Song {
        return Song(
            id = this.id,
            nm = this.name,
            ar = this.ar.joinToString("/") { it.name },
            pc = this.al?.picUrl
        )
    }
}
