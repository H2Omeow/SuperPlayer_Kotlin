package top.nekoh2o.player.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.data.model.LyricLine
import top.nekoh2o.player.data.model.LyricWord
import top.nekoh2o.player.ui.PlayMode
import top.nekoh2o.player.ui.PlayerViewModel
import android.provider.Settings
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.platform.LocalContext
import top.nekoh2o.player.ui.a11y.clickableRow
import top.nekoh2o.player.ui.a11y.toggleSemantics
import top.nekoh2o.player.ui.theme.NekoDefaults

private val TextMain = Color.White
private val TextSub = Color.White.copy(alpha = 0.7f)
private val LyricIdle = Color.White.copy(alpha = 0.45f)

@Composable
fun FullPlayerScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val state by vm.ui.collectAsState()
    val cur = state.current
    var showQueue by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showFloatingPermDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activeColor = MaterialTheme.colorScheme.primary

    Box(Modifier.fillMaxSize()) {
        // 背景层：壁纸 or 封面
        val bgModel = when (state.settings.fpBgSource) {
            BgSource.WALLPAPER -> state.wallpaperUrl
            BgSource.COVER -> cur?.pc?.let { "$it?param=800y800" }
        }
        AsyncImage(
            model = bgModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(state.settings.fpBlurRadius.dp)
        )
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = state.settings.fpMaskAlpha))
        )

        // 内容
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "收起",
                        tint = TextMain)
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // 唱片/歌词切换区域
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = if (showLyrics) "显示唱片" else "显示歌词",
                        role = Role.Button
                    ) { showLyrics = !showLyrics },
                contentAlignment = Alignment.Center
            ) {
                if (showLyrics) {
                    // 歌词模式：只显示歌词
                    LyricView(
                        lyrics = state.lyrics,
                        activeIndex = state.lyricIndex,
                        positionSec = state.positionMs / 1000.0,
                        isPlaying = state.isPlaying,
                        activeColor = activeColor,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 唱片模式：显示唱片和歌曲信息
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RotatingCover(
                            url = cur?.pc?.let { "$it?param=500y500" },
                            playing = state.isPlaying,
                            modifier = Modifier
                        )
                        Spacer(Modifier.height(32.dp))
                        Text(
                            cur?.nm ?: "未播放",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold, color = TextMain,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            cur?.ar ?: "-",
                            style = MaterialTheme.typography.bodyMedium, color = TextSub,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            ControlRow(
                isFav = cur != null && vm.isFav(cur.id),
                speed = state.settings.playbackSpeed,
                sleepMinutes = state.sleepMinutes,
                floatingEnabled = state.settings.floatingLyricEnabled,
                onSpeedClick = { showSpeedDialog = true },
                onTimerClick = { showTimerDialog = true },
                onDownloadClick = { if (cur != null) showQualityDialog = true },
                onFavClick = { cur?.let { vm.toggleFav(it) } },
                onFloatingClick = {
                    if (!Settings.canDrawOverlays(context)) showFloatingPermDialog = true
                    else vm.toggleFloatingLyric(context)
                }
            )

            ProgressBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { vm.seekTo(it) }
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val modeLabel = when (state.playMode) {
                    PlayMode.LOOP -> "列表循环"
                    PlayMode.SINGLE -> "单曲循环"
                    PlayMode.RANDOM -> "随机播放"
                }
                IconButton(
                    onClick = { vm.cyclePlayMode() },
                    modifier = Modifier.toggleSemantics("播放模式", modeLabel)
                ) {
                    Icon(
                        when (state.playMode) {
                            PlayMode.LOOP -> Icons.Filled.Repeat
                            PlayMode.SINGLE -> Icons.Filled.RepeatOne
                            PlayMode.RANDOM -> Icons.Filled.Shuffle
                        },
                        contentDescription = null, tint = TextMain
                    )
                }
                IconButton(onClick = { vm.prev() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首",
                        tint = TextMain, modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = { vm.togglePlay() }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首",
                        tint = TextMain, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放列表",
                        tint = TextMain)
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(vm, onDismiss = { showQueue = false })
    }
    if (showFloatingPermDialog) {
        AlertDialog(
            onDismissRequest = { showFloatingPermDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("悬浮窗歌词需要「显示在其他应用上层」权限，以便在锁屏或其他应用界面上实时显示当前歌词。") },
            confirmButton = {
                TextButton(
                    onClick = { showFloatingPermDialog = false; vm.toggleFloatingLyric(context) },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("去授权") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFloatingPermDialog = false },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }
    if (showSpeedDialog) {
        SpeedDialog(
            current = state.settings.playbackSpeed,
            onSelect = { vm.setPlaybackSpeed(it); showSpeedDialog = false },
            onDismiss = { showSpeedDialog = false }
        )
    }
    if (showTimerDialog) {
        TimerDialog(
            current = state.sleepMinutes,
            onSelect = { vm.setSleepTimer(it); showTimerDialog = false },
            onDismiss = { showTimerDialog = false }
        )
    }
    if (showQualityDialog) {
        QualityDialog(
            current = state.quality,
            onSelect = { q ->
                showQualityDialog = false
                cur?.let { vm.downloadSong(it, q) }
            },
            onDismiss = { showQualityDialog = false }
        )
    }
}

@Composable
private fun QualityDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    // value → label
    val levels = listOf(
        "standard" to "标准 128k",
        "higher" to "较高 192k",
        "exhigh" to "极高 320k",
        "lossless" to "无损 FLAC"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载音质") },
        text = {
            Column {
                levels.forEach { (value, label) ->
                    TextButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NekoDefaults.textButtonColors()
                    ) {
                        Text(
                            label,
                            color = if (value == current) MaterialTheme.colorScheme.primary else TextMain,
                            fontWeight = if (value == current) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = NekoDefaults.textButtonColors()) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ControlRow(
    isFav: Boolean,
    speed: Float,
    sleepMinutes: Int,
    floatingEnabled: Boolean,
    onSpeedClick: () -> Unit,
    onTimerClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onFavClick: () -> Unit,
    onFloatingClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlItem(
            label = "${speed}x", active = false,
            description = "倍速", onClick = onSpeedClick
        ) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = TextMain)
        }
        ControlItem(
            label = if (sleepMinutes > 0) "${sleepMinutes}min" else "定时",
            active = sleepMinutes > 0,
            description = "定时关闭",
            stateDescription = if (sleepMinutes > 0) "已设定 ${sleepMinutes} 分钟" else "未开启",
            onClick = onTimerClick
        ) {
            Icon(
                Icons.Filled.Timer, contentDescription = null,
                tint = if (sleepMinutes > 0) MaterialTheme.colorScheme.primary else TextMain
            )
        }
        ControlItem(
            label = "下载", active = false,
            description = "下载当前歌曲", onClick = onDownloadClick
        ) {
            Icon(Icons.Filled.Download, contentDescription = null, tint = TextMain)
        }
        ControlItem(
            label = if (isFav) "已收藏" else "收藏",
            active = isFav,
            description = "收藏",
            stateDescription = if (isFav) "已收藏" else "未收藏",
            onClick = onFavClick
        ) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (isFav) MaterialTheme.colorScheme.primary else TextMain
            )
        }
        ControlItem(
            label = "悬浮词", active = floatingEnabled,
            description = "悬浮歌词",
            stateDescription = if (floatingEnabled) "已开启" else "已关闭",
            onClick = onFloatingClick
        ) {
            Icon(
                if (floatingEnabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                contentDescription = null,
                tint = if (floatingEnabled) MaterialTheme.colorScheme.primary else TextMain
            )
        }
    }
}

@Composable
private fun ControlItem(
    label: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
    stateDescription: String? = null,
    icon: @Composable () -> Unit
) {
    // 合并图标+文字为单个按钮语义节点，TalkBack 朗读「名称，状态，按钮」而非拆成两段
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
            role = Role.Button
            stateDescription?.let { this.stateDescription = it }
        }
    ) {
        IconButton(onClick = onClick) { icon() }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.primary else TextSub
        )
    }
}

@Composable
private fun SpeedDialog(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放速度") },
        text = {
            Column {
                speeds.forEach { s ->
                    TextButton(
                        onClick = { onSelect(s) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NekoDefaults.textButtonColors()
                    ) {
                        Text(
                            "${s}x",
                            color = if (s == current) MaterialTheme.colorScheme.primary else TextMain,
                            fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = NekoDefaults.textButtonColors()) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun TimerDialog(current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val options = listOf(0 to "关闭", 15 to "15 分钟", 30 to "30 分钟", 45 to "45 分钟", 60 to "60 分钟")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭") },
        text = {
            Column {
                options.forEach { (mins, label) ->
                    TextButton(
                        onClick = { onSelect(mins) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = NekoDefaults.textButtonColors()
                    ) {
                        Text(
                            label,
                            color = if (mins == current) MaterialTheme.colorScheme.primary else TextMain,
                            fontWeight = if (mins == current) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, colors = NekoDefaults.textButtonColors()) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RotatingCover(url: String?, playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cover")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(20000, easing = LinearEasing)),
        label = "angle"
    )
    var lastAngle by remember { mutableFloatStateOf(0f) }
    if (playing) lastAngle = angle

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(240.dp).rotate(if (playing) angle else lastAngle).clip(CircleShape)
    )
}

@Composable
private fun LyricView(
    lyrics: List<LyricLine>,
    activeIndex: Int,
    positionSec: Double,
    isPlaying: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier, Alignment.Center) {
            Text("暂无歌词", color = LyricIdle)
        }
        return
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    LaunchedEffect(activeIndex) {
        if (activeIndex < 0) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportEndOffset -
            listState.layoutInfo.viewportStartOffset
        val approxItemPx = with(density) { (18 * 1.6f + 14).dp.toPx() }.toInt()
        val offset = -(viewportHeight / 2 - approxItemPx / 2)
        listState.animateScrollToItem(activeIndex, offset)
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 40.dp)
    ) {
        items(lyrics.size) { i ->
            val line = lyrics[i]
            LyricRow(line, i == activeIndex, positionSec, isPlaying, activeColor)
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    isActive: Boolean,
    positionSec: Double,
    isPlaying: Boolean,
    activeColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isActive && line.words != null) {
            KaraokeLine(line.words, positionSec, isPlaying, activeColor, LyricIdle)
        } else {
            Text(
                line.text,
                color = if (isActive) activeColor else LyricIdle,
                fontSize = if (isActive) 18.sp else 15.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
        line.translation?.let {
            Text(
                it,
                color = if (isActive) activeColor.copy(alpha = 0.8f) else LyricIdle,
                fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun KaraokeLine(
    words: List<LyricWord>,
    positionSec: Double,
    isPlaying: Boolean,
    activeColor: Color,
    idleColor: Color
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 18.sp
    )
    val fullText = remember(words) { words.joinToString("") { it.text } }

    var anchorSec by remember { mutableDoubleStateOf(positionSec) }
    var anchorNanos by remember { mutableLongStateOf(0L) }
    var frameSec by remember { mutableDoubleStateOf(positionSec) }

    LaunchedEffect(positionSec) {
        anchorSec = positionSec; anchorNanos = 0L; frameSec = positionSec
    }
    LaunchedEffect(isPlaying, words) {
        if (!isPlaying) { frameSec = positionSec; return@LaunchedEffect }
        while (true) {
            withFrameNanos { now ->
                if (anchorNanos == 0L) anchorNanos = now
                frameSec = anchorSec + (now - anchorNanos) / 1_000_000_000.0
            }
        }
    }
    val drivePos = if (isPlaying) frameSec else positionSec

    Canvas(
        modifier = Modifier.fillMaxWidth()
            .height(with(LocalDensity.current) { (style.fontSize.value * 1.7f).sp.toDp() })
    ) {
        val layout = measurer.measure(text = fullText, style = style)
        val startX = (size.width - layout.size.width) / 2f
        val topY = (size.height - layout.size.height) / 2f

        var filledPx = 0f
        var charIndex = 0
        run {
            words.forEach { w ->
                val start = charIndex
                val end = charIndex + w.text.length
                if (start >= layout.layoutInput.text.length || end == 0) {
                    charIndex = end; return@forEach
                }
                val wLeft = layout.getBoundingBox(start).left
                val wRight = layout.getBoundingBox(end - 1).right
                val p = ((drivePos - w.start) / w.dur).coerceIn(0.0, 1.0).toFloat()
                if (p >= 1f) filledPx = wRight
                else if (p > 0f) { filledPx = wLeft + (wRight - wLeft) * p; charIndex = end; return@run }
                else { charIndex = end; return@run }
                charIndex = end
            }
        }

        drawText(layout, color = idleColor,
            topLeft = androidx.compose.ui.geometry.Offset(startX, topY))
        if (filledPx > 0f) {
            clipRect(left = startX, top = 0f, right = startX + filledPx, bottom = size.height) {
                drawText(layout, color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(startX, topY))
            }
        }
    }
}

@Composable
private fun ProgressBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val sliderValue = if (dragging) dragValue else fraction

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = {
                dragging = false
                if (durationMs > 0) onSeek((dragValue * durationMs).toLong())
            }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                fmtTime(if (dragging) (dragValue * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.labelSmall, color = TextSub
            )
            Text(fmtTime(durationMs), style = MaterialTheme.typography.labelSmall, color = TextSub)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val state by vm.ui.collectAsState()
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        vm.moveInQueue(from.index, to.index)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "播放列表 (${state.queue.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (state.queue.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                Text("播放列表为空")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            ) {
                items(state.queue, key = { it.id }) { song ->
                    val index = state.queue.indexOf(song)
                    ReorderableItem(reorderState, key = song.id) { isDragging ->
                        Surface(tonalElevation = if (isDragging) 4.dp else 0.dp) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "拖动排序",
                                    modifier = Modifier.draggableHandle()
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(
                                    Modifier.weight(1f).clickableRow(
                                        rowLabel = "${song.nm}，${song.ar}" +
                                            if (index == state.currentIndex) "，正在播放" else "",
                                        actionLabel = "播放"
                                    ) { vm.playAt(index); onDismiss() }
                                ) {
                                    Text(
                                        song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (index == state.currentIndex)
                                            MaterialTheme.colorScheme.primary
                                        else LocalContentColor.current
                                    )
                                    Text(song.ar, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = { vm.removeFromQueue(index) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    return "%02d:%02d".format(total / 60, total % 60)
}
