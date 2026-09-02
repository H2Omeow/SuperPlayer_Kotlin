package top.nekoh2o.player.ui.manager

import android.content.Context
import android.net.Uri
import top.nekoh2o.player.data.model.LyricLine
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 歌词管理工具类
 * 负责：歌词格式转换、.lrc 文件读写、歌词解析
 */
object LyricManager {

    /**
     * 将 LyricLine 列表转换为标准 .lrc 格式文本
     */
    fun buildLrcText(lines: List<LyricLine>): String {
        return lines.joinToString("\n") { line ->
            val totalCs = (line.time * 100).toLong()
            val min = totalCs / 6000
            val sec = (totalCs % 6000) / 100
            val cs = totalCs % 100
            val timestamp = "[%02d:%02d.%02d]".format(min, sec, cs)
            if (line.translation.isNullOrEmpty()) {
                "$timestamp${line.text}"
            } else {
                "$timestamp${line.text}\n$timestamp${line.translation}"
            }
        }
    }

    /**
     * 从本地 .lrc 文件读取歌词文本
     */
    fun readLrcFile(context: Context, lrcUri: String): String? {
        return runCatching {
            val uri = Uri.parse(lrcUri)
            if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                }
            } else {
                java.io.File(lrcUri).readText(Charsets.UTF_8)
            }
        }.getOrNull()
    }
}
