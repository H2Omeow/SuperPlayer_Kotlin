package top.nekoh2o.player.ui.a11y

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * 无障碍（WCAG 2.1）通用辅助。
 *
 * 目标：
 * - 可操作：交互元素有明确角色（Role）与操作标签，屏幕阅读器（TalkBack）朗读时能说明「这是按钮，
 *   激活后会做什么」；触摸目标不小于 48dp。
 * - 可理解：开关/收藏/播放等状态类控件通过 [stateDescription] 朗读当前状态（如「已收藏」）。
 * - 可感知：装饰性图标 contentDescription 传 null（阅读器跳过），有信息的元素合并语义避免碎片化朗读。
 */

/** WCAG 2.5.5：最小触摸目标 48x48dp。用于本身尺寸偏小的可点区域。 */
fun Modifier.minTouchTarget(): Modifier = this.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)

/**
 * 列表行整行可点：把整行合并成单个「按钮」语义节点。
 *
 * TalkBack 会把整行朗读为一条（如「歌曲名 歌手，按钮，双击以播放」），而不是把封面/标题/副标题
 * 拆成多个焦点。行内若还有独立操作按钮（收藏、删除等），应放在本修饰符作用的容器之外，保持它们
 * 各自独立可聚焦。
 *
 * @param rowLabel 阅读器朗读的整行内容（通常是「歌曲名，歌手」）。
 * @param actionLabel 操作提示（如「播放」），朗读为「双击以…」。
 */
fun Modifier.clickableRow(
    rowLabel: String,
    actionLabel: String,
    onClick: () -> Unit
): Modifier = this
    .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)
    .clearAndSetSemantics { contentDescription = rowLabel }

/** 给可点内容加上「按钮」角色与操作标签，但不改写子节点语义（子节点自身文本仍会朗读）。 */
fun Modifier.asButton(actionLabel: String? = null, onClick: () -> Unit): Modifier =
    this.clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onClick)

/**
 * 切换类控件（收藏、悬浮词、播放模式等）的语义：朗读「名称 + 当前状态」。
 * 例：contentDescription = "收藏", stateDescription = "已收藏"。
 */
fun Modifier.toggleSemantics(name: String, state: String): Modifier =
    this.semantics {
        contentDescription = name
        stateDescription = state
    }
