package top.nekoh2o.player.data.model

// 逐字：一个字/词的起始与时长（秒）
data class LyricWord(
    val text: String,
    val start: Double,
    val dur: Double
)

// 一行歌词：时间(秒)、整行文本、可选逐字、可选翻译
data class LyricLine(
    val time: Double,
    val text: String,
    val words: List<LyricWord>? = null,
    val translation: String? = null
)
