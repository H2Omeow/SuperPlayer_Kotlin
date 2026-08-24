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
    @GET("nc/user/account")
    suspend fun userAccount(): NcAccountResp

    /**
     * 检查网易云登录状态
     */
    @GET("nc/login/status")
    suspend fun loginStatus(): NcLoginStatusResp

    /**
     * 获取用户歌单
     * @param uid 用户 ID
     */
    @GET("nc/user/playlist")
    suspend fun userPlaylist(@Query("uid") uid: Long): NcPlaylistResp

    /**
     * 获取用户红心歌曲 ID 列表
     * @param uid 用户 ID
     */
    @GET("nc/likelist")
    suspend fun likeList(@Query("uid") uid: Long): NcLikeListResp

    /**
     * 获取用户播放记录
     * @param uid 用户 ID
     * @param type 1: 最近一周, 0: 所有时间
     */
    @GET("nc/user/record")
    suspend fun userRecord(@Query("uid") uid: Long, @Query("type") type: Int = 1): NcPlayRecordResp

    /**
     * 根据 ID 批量获取歌曲详情
     * @param ids 歌曲 ID，逗号分隔
     */
    @GET("nc/song/detail")
    suspend fun songDetail(@Query("ids") ids: String): NcSongDetailResp

    /**
     * 获取歌单详情
     * @param id 歌单 ID
     */
    @GET("nc/playlist/detail")
    suspend fun playlistDetail(@Query("id") id: Long): NcPlaylistDetailResp
}
