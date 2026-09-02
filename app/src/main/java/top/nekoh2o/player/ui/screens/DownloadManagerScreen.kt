package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.data.model.DownloadStatus
import top.nekoh2o.player.data.model.DownloadTask
import top.nekoh2o.player.data.model.DownloadedSong
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.clickableRow
import top.nekoh2o.player.ui.theme.NekoDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    // 正在进行的任务（未完成）
    val active = state.downloadTasks.filter { it.status != DownloadStatus.DONE }
    val done = state.downloadedSongs

    var showDeleteConfirm by remember { mutableStateOf<Long?>(null) }

    // 删除确认对话框
    if (showDeleteConfirm != null) {
        val song = done.find { it.songId == showDeleteConfirm }
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除文件") },
            text = { Text("确定要删除「${song?.song?.nm ?: "该歌曲"}」及其文件吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm?.let { vm.removeDownloaded(it) }
                        showDeleteConfirm = null
                    },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = null },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("下载管理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        if (active.isEmpty() && done.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("暂无下载") }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (active.isNotEmpty()) {
                item {
                    Text(
                        "正在下载 (${active.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(active, key = { it.song.id }) { task ->
                    ActiveTaskRow(
                        task = task,
                        onRetry = { vm.retryDownload(task.song, task.quality) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            placementSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    )
                }
            }

            if (done.isNotEmpty()) {
                item {
                    Text(
                        "已下载 (${done.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
                    )
                }
                items(done, key = { it.songId }) { d ->
                    DownloadedRow(
                        item = d,
                        onPlay = { vm.playNow(d.song) },
                        onRemove = { showDeleteConfirm = d.songId },
                        modifier = Modifier.animateItem(
                            fadeInSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            placementSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            ),
                            fadeOutSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                            )
                        )
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActiveTaskRow(task: DownloadTask, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = task.song.pc?.let { "$it?param=80y80" },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(task.song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium)
            when (task.status) {
                DownloadStatus.FAILED -> Text(
                    "下载失败：${task.errorMsg ?: "未知错误"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                else -> {
                    Spacer(Modifier.height(4.dp))
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = task.progress,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 300,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        label = "download_progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        if (task.status == DownloadStatus.FAILED) {
            TextButton(
                onClick = onRetry,
                colors = NekoDefaults.textButtonColors()
            ) {
                Text("重试")
            }
        } else {
            Text("${(task.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun DownloadedRow(
    item: DownloadedSong,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).clickableRow(
                rowLabel = "${item.song.nm}，${item.song.ar}，${qualityLabel(item.quality)}",
                actionLabel = "播放"
            ) { onPlay() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = item.song.pc?.let { "$it?param=80y80" },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.song.ar} · ${qualityLabel(item.quality)}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "移除")
        }
    }
}

private fun qualityLabel(q: String): String = when (q) {
    "standard" -> "标准"
    "higher" -> "较高"
    "exhigh" -> "极高"
    "lossless" -> "无损 SQ"
    "hires" -> "Hi-Res 无损"
    "jyeffect" -> "高清臻音"
    "sky" -> "沉浸环绕声"
    "jymaster" -> "超清母带"
    "dolby" -> "臻音全景声"
    else -> q
}
