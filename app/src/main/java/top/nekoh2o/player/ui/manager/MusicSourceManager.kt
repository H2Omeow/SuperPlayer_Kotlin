package top.nekoh2o.player.ui.manager

import top.nekoh2o.player.data.net.CookieStore

/**
 * 音乐源管理工具类
 * 负责：音乐源切换逻辑、音乐源配置管理
 */
object MusicSourceManager {

    /**
     * 获取当前音乐源
     * @return "netease" 或 "kugou"
     */
    fun getCurrentSource(): String {
        return if (CookieStore.hasKgToken()) "kugou" else "netease"
    }

    /**
     * 检查是否为酷狗音乐源
     */
    fun isKugouSource(): Boolean {
        return CookieStore.hasKgToken()
    }

    /**
     * 检查是否为网易云音乐源
     */
    fun isNeteaseSource(): Boolean {
        return !CookieStore.hasKgToken()
    }

    /**
     * 切换到酷狗音乐源
     * 注意：此方法包含 suspend 调用，需要在协程中执行
     */
    suspend fun switchToKugou(token: String, platform: Int) {
        CookieStore.setKgToken(token)
        CookieStore.setKgPlatform(platform)
    }

    /**
     * 切换到网易云音乐源
     * 注意：此方法包含 suspend 调用，需要在协程中执行
     */
    suspend fun switchToNetease(cookie: String) {
        CookieStore.setUserCookie(cookie)
        CookieStore.setKgToken("")  // 清除酷狗 Token
    }

    /**
     * 清除当前音乐源
     * 注意：此方法包含 suspend 调用，需要在协程中执行
     */
    suspend fun clearCurrentSource() {
        if (isKugouSource()) {
            CookieStore.setKgToken("")
        } else {
            CookieStore.setUserCookie("")
        }
    }
}
