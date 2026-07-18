package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.model.Playlist
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.model.User
import top.nekoh2o.player.data.model.UserData
import top.nekoh2o.player.data.net.ApiFactory

class UserRepository {

    private val api = ApiFactory.user

    suspend fun fetchMe(): User? =
        runCatching {
            val resp = api.me()
            if (resp.code == 0) resp.user else null
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
}
