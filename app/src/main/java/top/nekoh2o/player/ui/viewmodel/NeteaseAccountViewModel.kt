package top.nekoh2o.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.MusicRepository

/**
 * 网易云账号管理 ViewModel
 * 负责：网易云 Cookie 管理、QR 扫码登录、账号数据同步
 */
class NeteaseAccountViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository()

    // ==================== Cookie 管理 ====================

    /**
     * 获取当前网易云 Cookie
     */
    fun getNcCookie(): String {
        return CookieStore.userCookieValue()
    }

    /**
     * 保存网易云 Cookie
     */
    fun saveNcCookie(cookie: String, onSchedulePush: () -> Unit) {
        viewModelScope.launch {
            CookieStore.setUserCookie(cookie.trim())
            onSchedulePush()
        }
    }

    /**
     * 清除网易云 Cookie
     */
    fun clearNcCookie() {
        viewModelScope.launch {
            CookieStore.setUserCookie("")
        }
    }

    /**
     * 检测 Cookie 是否有效
     */
    suspend fun checkCookieValidity(): Boolean {
        if (!CookieStore.hasNcUserCookie()) return false
        // 实际校验需要通过 UserRepository.checkNcLoginStatus()
        return true
    }

    // ==================== QR 扫码登录 ====================

    /**
     * 获取 QR 登录 key
     */
    suspend fun getQrKey(): String? {
        return repo.qrKey()
    }

    /**
     * 生成 QR 二维码 URL
     */
    suspend fun createQr(key: String): String? {
        return repo.qrCreate(key)
    }

    /**
     * 检查 QR 扫码状态
     * @return 状态码：800=二维码过期, 801=等待扫码, 802=待确认, 803=授权成功
     */
    suspend fun checkQrStatus(key: String): Int {
        return repo.qrCheck(key)
    }

    // ==================== 网易云数据获取 ====================

    /**
     * 获取网易云歌单详情
     */
    suspend fun getPlaylistDetail(playlistId: Long): List<Song> {
        return runCatching { repo.playlistTracks(playlistId) }.getOrDefault(emptyList())
    }
}
