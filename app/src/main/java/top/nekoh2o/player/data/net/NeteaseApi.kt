package top.nekoh2o.player.data.net

import retrofit2.http.GET
import retrofit2.http.Query
import top.nekoh2o.player.data.model.*

/**
 * 网易云音乐相关 API
 */
interface NeteaseApi {

    /**
     * 获取网易云账号信息
     */
    @GET("api/user/account")
    suspend fun userAccount(@Query("cookie") cookie: String): NcAccountResp

    /**
     * 检查网易云登录状态
     */
    @GET("api/login/status")
    suspend fun loginStatus(@Query("cookie") cookie: String): NcLoginStatusResp

    /**
     * 获取用户歌单
     * @param uid 用户 ID
     */
    @GET("api/user/playlist")
    suspend fun userPlaylist(@Query("uid") uid: Long, @Query("cookie") cookie: String): NcPlaylistResp

    /**
     * 获取用户红心歌曲 ID 列表
     * @param uid 用户 ID
     */
    @GET("api/likelist")
    suspend fun likeList(@Query("uid") uid: Long, @Query("cookie") cookie: String): NcLikeListResp

    /**
     * 获取用户播放记录
     * @param uid 用户 ID
     * @param type 1: 最近一周, 0: 所有时间
     */
    @GET("api/user/record")
    suspend fun userRecord(@Query("uid") uid: Long, @Query("cookie") cookie: String, @Query("type") type: Int = 1): NcPlayRecordResp

    /**
     * 根据 ID 批量获取歌曲详情
     * @param ids 歌曲 ID，逗号分隔
     */
    @GET("api/song/detail")
    suspend fun songDetail(@Query("ids") ids: String): NcSongDetailResp

    /**
     * 获取歌单详情
     * @param id 歌单 ID
     */
    @GET("api/playlist/detail")
    suspend fun playlistDetail(@Query("id") id: Long): NcPlaylistDetailResp
}
