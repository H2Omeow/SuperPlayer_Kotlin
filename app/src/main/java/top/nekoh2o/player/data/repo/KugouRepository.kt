package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore

/**
 * 酷狗音乐数据仓库
 */
class KugouRepository {

    private val api = ApiFactory.kugou

    /**
     * 初始化：确保 dfid 存在
     * 应在应用启动或首次使用酷狗功能时调用
     */
    suspend fun ensureInitialized() {
        // 如果已有 dfid，跳过
        if (CookieStore.kgDfidValue().isNotEmpty()) return
        // 获取并保存 dfid
        getDfid()
    }

    // ==================== 登录相关 ====================

    /**
     * 发送验证码
     */
    suspend fun sendCode(phone: String): Boolean =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.sendCode(phone, platform, cookie)
            resp.status == 1
        }.getOrDefault(false)

    /**
     * 手机号登录
     */
    suspend fun login(phone: String, code: String): KgLoginData? =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.login(phone, code, platform, cookie)
            if (resp.status == 1 && resp.data != null) {
                // 保存token、userid、dfid
                CookieStore.setKgToken(resp.data.token)
                CookieStore.setKgUserid(resp.data.userid.toString())
                if (resp.data.dfid.isNotEmpty()) {
                    CookieStore.setKgDfid(resp.data.dfid)
                }
                resp.data
            } else null
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "login() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 刷新登录
     */
    suspend fun refreshLogin(): Boolean =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching false
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.refreshLogin(token, platform, cookie)
            if (resp.status == 1 && resp.data != null) {
                CookieStore.setKgToken(resp.data.token)
                true
            } else false
        }.getOrDefault(false)

    /**
     * 获取DFID（设备指纹）
     */
    suspend fun getDfid(): String? =
        runCatching {
            val resp = api.getDfid()
            if (resp.status == 1 && resp.data?.dfid?.isNotEmpty() == true) {
                // 保存dfid到CookieStore
                CookieStore.setKgDfid(resp.data.dfid)
                resp.data.dfid
            } else null
        }.getOrNull()

    // ==================== 用户信息 ====================

    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(): KgUserInfo? =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching null
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getUserInfo(token, platform, cookie)
            if (resp.status == 1) resp.data else null
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getUserInfo() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 获取VIP信息
     */
    suspend fun getVipInfo(): KgVipInfo? =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching null
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getVipInfo(token, platform, cookie)
            if (resp.status == 1) resp.data else null
        }.getOrNull()

    // ==================== 歌单相关 ====================

    /**
     * 获取用户歌单
     */
    suspend fun getUserPlaylists(): List<KgPlaylistItem> =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching emptyList()
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getUserPlaylist(token, platform, cookie)
            if (resp.status == 1) resp.data?.info ?: emptyList() else emptyList()
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getUserPlaylists() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 获取歌单详情及歌曲列表
     */
    suspend fun getPlaylistDetail(specialId: Long): List<Song> =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getPlaylistDetail(specialId, platform, cookie)
            if (resp.status == 1) {
                resp.data?.list?.list?.info?.map { it.toSong() } ?: emptyList()
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getPlaylistDetail() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    // ==================== 搜索相关 ====================

    /**
     * 搜索歌曲
     */
    suspend fun search(keyword: String, page: Int = 1): List<Song> =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.search(keyword, page, 30, "song", platform, cookie)
            if (resp.status == 1) {
                resp.data?.lists?.map { it.toSong() } ?: emptyList()
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "search() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 搜索建议
     */
    suspend fun searchSuggest(keyword: String): List<String> =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.searchSuggest(keyword, platform, cookie)
            if (resp.status == 1) {
                resp.data?.song?.map { it.songname } ?: emptyList()
            } else emptyList()
        }.getOrDefault(emptyList())

    // ==================== 歌曲相关 ====================

    /**
     * 获取歌曲播放URL
     */
    suspend fun getSongUrl(hash: String, quality: String = "320"): String? =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getSongUrl(hash, quality, platform, cookie)
            if (resp.status == 1) {
                resp.data?.play_url?.ifEmpty { resp.data.play_backup_url }
            } else null
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getSongUrl() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 获取歌曲可用音质列表
     */
    suspend fun getSongQualities(hash: String): List<KgQualityItem> =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getSongUrlNew(hash, platform, cookie)
            if (resp.status == 1) {
                resp.data?.qualities ?: emptyList()
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getSongQualities() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    /**
     * 获取歌词
     */
    suspend fun getLyric(hash: String): String? =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getLyric(hash, platform, cookie)
            if (resp.status == 1) resp.data?.lyrics else null
        }.getOrNull()

    /**
     * 获取歌曲详情
     */
    suspend fun getSongDetail(hash: String): KgSongDetail? =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getSongDetail(hash, platform, cookie)
            if (resp.status == 1) resp.data else null
        }.getOrNull()

    // ==================== VIP领取（概念版）====================

    /**
     * 领取VIP（仅概念版）
     */
    suspend fun receiveVip(vipType: Int = 1, days: Int = 7): String? =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching null
            val cookie = CookieStore.kgCookieValue()
            val resp = api.receiveVip(token, vipType, days, 1, cookie)
            if (resp.status == 1) resp.data?.result else resp.error_msg
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "receiveVip() failed: ${e.message}", e)
        }.getOrNull()

    // ==================== 歌手相关 ====================

    /**
     * 获取用户关注的歌手
     */
    suspend fun getFollowArtists(): List<KgArtistItem> =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching emptyList()
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getFollowArtists(token, platform, cookie)
            if (resp.status == 1) resp.data?.info ?: emptyList() else emptyList()
        }.getOrDefault(emptyList())

    /**
     * 获取歌手歌曲
     */
    suspend fun getArtistSongs(singerId: Long): List<Song> =
        runCatching {
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getArtistSongs(singerId, 1, 50, platform, cookie)
            if (resp.status == 1) {
                resp.data?.lists?.map { it.toSong() } ?: emptyList()
            } else emptyList()
        }.getOrDefault(emptyList())

    // ==================== 音乐历史 ====================

    /**
     * 获取用户听歌历史
     */
    suspend fun getUserHistory(): List<Song> =
        runCatching {
            val token = CookieStore.kgTokenValue()
            if (token.isEmpty()) return@runCatching emptyList()
            val platform = CookieStore.kgPlatformValue()
            val cookie = CookieStore.kgCookieValue()
            val resp = api.getUserHistory(token, platform, cookie)
            if (resp.status == 1) {
                resp.data?.info?.map { it.toSong() } ?: emptyList()
            } else emptyList()
        }.getOrDefault(emptyList())

    /**
     * 获取推荐歌曲（个人 FM）
     */
    suspend fun getRecommendSongs(): List<Song> =
        runCatching {
            val cookie = CookieStore.kgCookieValue()
            if (cookie.isEmpty()) return@runCatching emptyList()
            val platform = CookieStore.kgPlatformValue()
            val resp = api.getRecommendSongs(cookie, platform)
            if (resp.status == 1) {
                // 个人 FM 返回 song_list 字段，不是 lists
                resp.data?.songList?.map { it.toSong() } ?: emptyList()
            } else emptyList()
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "getRecommendSongs() failed: ${e.message}", e)
        }.getOrDefault(emptyList())

    // ==================== QQ登录 ====================

    /**
     * QQ授权登录
     */
    suspend fun loginWithQQ(openid: String, accessToken: String): KgLoginData? =
        runCatching {
            val resp = api.loginWithQQ(openid, accessToken)
            if (resp.status == 1 && resp.data != null) {
                CookieStore.setKgToken(resp.data.token)
                CookieStore.setKgUserid(resp.data.userid.toString())
                if (resp.data.dfid.isNotEmpty()) {
                    CookieStore.setKgDfid(resp.data.dfid)
                }
                resp.data
            } else null
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "loginWithQQ() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 创建QQ扫码登录二维码
     */
    suspend fun createQQLoginQR(): KgQQQRCreateData? =
        runCatching {
            val resp = api.createQQLoginQR()
            // 新版 API 直接返回扁平 JSON，需要转换为旧格式供 UI 使用
            if (resp.qrcode.isNotEmpty()) {
                // 将 base64 二维码转换为 data URL
                val qrUrl = "data:image/png;base64,${resp.qrcode}"
                // 使用 qrsig 作为 qr_id，并保存完整响应用于后续 check
                KgQQQRCreateData(qrUrl = qrUrl, qrId = resp.qrsig, fullResp = resp)
            } else null
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "createQQLoginQR() failed: ${e.message}", e)
        }.getOrNull()

    /**
     * 检查QQ扫码登录状态
     */
    suspend fun checkQQLoginQR(qrData: KgQQQRCreateData): KgQQQRCheckResp? =
        runCatching {
            val fullResp = qrData.fullResp ?: return@runCatching null
            val platform = CookieStore.kgPlatformValue()
            val resp = api.checkQQLoginQR(
                qrsig = fullResp.qrsig,
                ptqrtoken = fullResp.ptqrtoken.toString(),
                ptLoginSig = fullResp.ptLoginSig,
                ptOpenloginData = fullResp.ptOpenloginData,
                xloginUrl = fullResp.xloginUrl,
                cookie = fullResp.cookie,
                platform = platform
            )
            if (resp.status == 1 && resp.data != null) {
                CookieStore.setKgToken(resp.data.token)
                CookieStore.setKgUserid(resp.data.userid.toString())
                if (resp.data.dfid.isNotEmpty()) {
                    CookieStore.setKgDfid(resp.data.dfid)
                }
            }
            resp
        }.onFailure { e ->
            android.util.Log.e("KugouRepository", "checkQQLoginQR() failed: ${e.message}", e)
        }.getOrNull()

    // ==================== 辅助转换方法 ====================

    /**
     * 酷狗搜索结果转Song
     */
    private fun KgSearchSong.toSong(): Song = Song(
        id = this.audioId,
        nm = this.songName,
        ar = this.singerName,
        pc = null,  // 酷狗搜索结果不带封面
        source = "kugou"
    )

    /**
     * 酷狗歌曲详情转Song
     */
    private fun KgSongDetail.toSong(): Song = Song(
        id = this.audioId,
        nm = this.songName,
        ar = this.authorName,
        pc = this.img,
        source = "kugou"
    )

    /**
     * 酷狗历史记录转Song
     */
    private fun KgHistoryItem.toSong(): Song = Song(
        id = this.audioId,
        nm = this.songName,
        ar = this.authorName,
        pc = null,
        source = "kugou"
    )

    /**
     * 酷狗个人 FM 歌曲转 Song
     */
    private fun KgFmSong.toSong(): Song = Song(
        id = this.songid,
        nm = this.songname,
        ar = this.singerinfo.joinToString(", ") { it.name },
        pc = null,
        source = "kugou"
    )
}
