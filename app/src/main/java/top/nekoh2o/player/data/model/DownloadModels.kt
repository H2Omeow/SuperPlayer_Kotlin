package top.nekoh2o.player.data.model

/** 已完成下载的歌曲，持久化到 DownloadIndex。 */
data class DownloadedSong(
    val songId: Long,
    val song: Song,
    /** content:// URI (API 29+) 或 file:// 绝对路径 */
    val audioUri: String,
    /** 同目录下同名 .lrc 文件的绝对路径，null 表示无本地歌词 */
    val lrcPath: String? = null,
    val quality: String = "exhigh",
    val downloadedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus { QUEUED, DOWNLOADING, DONE, FAILED }

/** 单个下载任务的实时状态，存于内存 StateFlow，不持久化。 */
data class DownloadTask(
    val song: Song,
    val quality: String,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** 0.0 ~ 1.0 */
    val progress: Float = 0f,
    val errorMsg: String? = null,
    val result: DownloadedSong? = null
)
