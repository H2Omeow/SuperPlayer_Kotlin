package top.nekoh2o.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 网易云账号信息响应
 */
@Serializable
data class NcAccountResp(
    val code: Int,
    val account: NcAccount? = null,
    val profile: NcProfile? = null
)

@Serializable
data class NcAccount(
    val id: Long,
    @SerialName("userName") val userName: String? = null,
    val type: Int = 0,
    val status: Int = 0,
    @SerialName("vipType") val vipType: Int = 0,
    @SerialName("viptypeVersion") val viptypeVersion: Long = 0,
    @SerialName("createTime") val createTime: Long = 0
)

@Serializable
data class NcProfile(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val signature: String? = null,
    val gender: Int = 0,
    val birthday: Long = 0,
    val province: Int = 0,
    val city: Int = 0
)

/**
 * 网易云登录状态响应
 */
@Serializable
data class NcLoginStatusResp(
    val data: NcLoginStatusData
)

@Serializable
data class NcLoginStatusData(
    val code: Int,
    val account: NcAccount? = null,
    val profile: NcProfile? = null
)

/**
 * 网易云用户歌单响应
 */
@Serializable
data class NcPlaylistResp(
    val code: Int,
    val playlist: List<NcPlaylistItem> = emptyList()
)

@Serializable
data class NcPlaylistItem(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0,
    val description: String? = null,
    val creator: NcCreator? = null
)

@Serializable
data class NcCreator(
    val userId: Long,
    val nickname: String,
    val avatarUrl: String? = null
)

/**
 * 红心歌曲 ID 列表响应
 */
@Serializable
data class NcLikeListResp(
    val code: Int,
    val ids: List<Long> = emptyList()
)

/**
 * 播放记录响应
 */
@Serializable
data class NcPlayRecordResp(
    val code: Int,
    @SerialName("weekData") val weekData: List<NcRecordItem> = emptyList(),
    @SerialName("allData") val allData: List<NcRecordItem> = emptyList()
)

@Serializable
data class NcRecordItem(
    val playCount: Int,
    val score: Int,
    val song: NcSongDetail
)

@Serializable
data class NcSongDetail(
    val id: Long,
    val name: String,
    val ar: List<NcArtist> = emptyList(),
    val al: NcAlbum? = null,
    val dt: Long = 0
)

@Serializable
data class NcArtist(
    val id: Long,
    val name: String
)

@Serializable
data class NcAlbum(
    val id: Long,
    val name: String,
    val picUrl: String? = null
)

/**
 * 根据 ID 批量获取歌曲详情
 */
@Serializable
data class NcSongDetailResp(
    val code: Int,
    val songs: List<NcSongDetail> = emptyList()
)

/**
 * 歌单详情响应
 */
@Serializable
data class NcPlaylistDetailResp(
    val code: Int,
    val playlist: NcPlaylistDetail? = null
)

@Serializable
data class NcPlaylistDetail(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val trackIds: List<NcTrackId> = emptyList(),
    val description: String? = null
)

@Serializable
data class NcTrackId(
    val id: Long
)

/**
 * 登录状态响应
 */
@Serializable
data class LoginStatusResp(
    val code: Int,
    val data: LoginStatusData? = null
)

@Serializable
data class LoginStatusData(
    val code: Int,
    val account: NcAccount? = null,
    val profile: NcProfile? = null
)

/**
 * 喜欢列表响应
 */
@Serializable
data class LikeListResp(
    val code: Int,
    val ids: List<Long> = emptyList(),
    val checkPoint: Long = 0
)

/**
 * 用户播放记录响应
 */
@Serializable
data class UserRecordResp(
    val code: Int,
    val weekData: List<RecordItem> = emptyList(),
    val allData: List<RecordItem> = emptyList()
)

@Serializable
data class RecordItem(
    val song: NcSongDetail,
    val playCount: Int = 0,
    val score: Int = 0
)
