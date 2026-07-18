package top.nekoh2o.player.lyric

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.nekoh2o.player.data.model.LyricLine
import top.nekoh2o.player.data.model.LyricWord

/**
 * 对应 web 端 player.js 的 pLrc / renderParsedLrc。
 * 支持三种输入：
 *  - JSON 行（网易云新版 yrc 的一种）：{"t":123,"c":[{"tx":"字"}...]}
 *  - 逐字 lrc：[start,dur](s,d,0)字(s,d,0)字
 *  - 标准 lrc：[mm:ss.xx]文本
 * 并把翻译按时间就近(<0.5s)合并进主歌词行。
 */
object LyricParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val wordTagRe = Regex("""\(\d+,\d+,\d+\)""")
    private val wordTagCapRe = Regex("""\((\d+),(\d+),(\d+)\)""")

    fun parse(main: String?, translation: String? = null): List<LyricLine> {
        val ma = parseLines(main)
        if (translation.isNullOrBlank()) return ma
        val ta = parseLines(translation)
        return ma.map { line ->
            val t = ta.firstOrNull { kotlin.math.abs(it.time - line.time) < 0.5 }
            if (t != null) line.copy(translation = t.text) else line
        }
    }

    private fun parseLines(src: String?): List<LyricLine> {
        if (src.isNullOrBlank()) return emptyList()
        val result = mutableListOf<LyricLine>()

        for (raw in src.split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // JSON 行
            if (line.startsWith("{")) {
                runCatching {
                    val obj = json.parseToJsonElement(line).jsonObject
                    val t = obj["t"]?.jsonPrimitive?.content?.toDoubleOrNull()
                    val c = obj["c"]?.jsonArray
                    if (t != null && c != null) {
                        val text = c.joinToString("") {
                            it.jsonObject["tx"]?.jsonPrimitive?.content ?: ""
                        }
                        if (text.isNotEmpty()) result.add(LyricLine(t / 1000.0, text))
                    }
                }
                continue
            }

            val lb = line.indexOf('[')
            val rb = line.indexOf(']')
            if (lb == -1 || rb == -1) continue

            val timeStr = line.substring(lb + 1, rb)
            val body = line.substring(rb + 1).trim()
            if (body.isEmpty()) continue

            // 逐字解析
            val words = mutableListOf<LyricWord>()
            val parts = body.split(wordTagRe)
            val tags = wordTagCapRe.findAll(body).map {
                val (s, d, _) = it.destructured
                (s.toDouble() / 1000.0) to (d.toDouble() / 1000.0)
            }.toList()
            for (i in tags.indices) {
                if (i + 1 < parts.size) {
                    val ch = parts[i + 1]
                    if (ch.isNotEmpty()) words.add(LyricWord(ch, tags[i].first, tags[i].second))
                }
            }

            val plain = body.replace(wordTagRe, "").trim()
            if (plain.isEmpty()) continue

            // 行首时间解析
            val time = parseTime(timeStr)
            if (time > 0) {
                result.add(
                    LyricLine(
                        time = time,
                        text = plain,
                        words = words.ifEmpty { null }
                    )
                )
            }
        }
        return result.sortedBy { it.time }
    }

    // 支持 [mm:ss.xx] 与 [start,dur]（逐字行首为两个数字）
    private fun parseTime(m: String): Double {
        if (m.contains(',')) {
            val p = m.split(',')
            return p.getOrNull(0)?.toDoubleOrNull()?.div(1000.0) ?: 0.0
        }
        val p = m.split(':')
        if (p.size == 2) {
            val min = p[0].toDoubleOrNull() ?: return 0.0
            val sec = p[1].toDoubleOrNull() ?: return 0.0
            return min * 60 + sec
        }
        return 0.0
    }
}
