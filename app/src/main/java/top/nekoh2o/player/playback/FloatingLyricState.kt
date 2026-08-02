package top.nekoh2o.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 悬浮窗歌词的进程内共享状态。
 *
 * ViewModel 在歌词行变化时调用 [publish]，[FloatingLyricService] 观察后刷新悬浮窗。
 * 单例 StateFlow 桥接，避免 Service 重复解析歌词或拉取网络。
 */
object FloatingLyricState {

    /** 当前主歌词行（中文/原文）。空串表示无歌词。 */
    val currentLine = MutableStateFlow("")

    /** 当前翻译行。空串表示无翻译或未启用翻译。 */
    val translationLine = MutableStateFlow("")

    /** 下一句歌词（双行模式下无翻译时作为第二行兜底）。 */
    val nextLineText = MutableStateFlow("")

    /** 当前行的逐字填充进度 0f~1f，用于悬浮窗卡拉OK渐变。 */
    val lineProgress = MutableStateFlow(0f)

    /** 悬浮窗是否处于开启状态，供 UI 恢复勾选态。 */
    val enabled = MutableStateFlow(false)

    fun publish(line: String) {
        currentLine.value = line
    }

    fun publishTranslation(line: String) {
        translationLine.value = line
    }

    fun publishNextLine(line: String) {
        nextLineText.value = line
    }

    fun publishProgress(fraction: Float) {
        lineProgress.value = fraction.coerceIn(0f, 1f)
    }

    fun setEnabled(v: Boolean) {
        enabled.value = v
    }

    val lineFlow: StateFlow<String> get() = currentLine
    val translationFlow: StateFlow<String> get() = translationLine
    val nextLineFlow: StateFlow<String> get() = nextLineText
    val progressFlow: StateFlow<Float> get() = lineProgress
}
