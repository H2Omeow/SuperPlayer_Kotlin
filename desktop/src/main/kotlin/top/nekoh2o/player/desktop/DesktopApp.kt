package top.nekoh2o.player.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.AnimemusicRepository
import top.nekoh2o.player.data.repo.MusicVideo
import org.jetbrains.skia.Image as SkiaImage

internal fun sourceName(source: String): String = when {
    source == "kugou" -> "酷狗音乐"
    source == "local" -> "本地音乐"
    source.startsWith("animemusic-") -> "惜缘惜梦 · ${AnimemusicRepository.label(source.removePrefix("animemusic-"))}"
    else -> "网易云音乐"
}

@Composable
internal fun DesktopApp(controller: DesktopController) {
    var tab by remember { mutableStateOf("音乐") }
    var showUsage by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) false
        else when {
            event.key == Key.Escape && controller.fullPlayer -> { controller.fullPlayer = false; true }
            event.isCtrlPressed && event.key == Key.DirectionRight -> { controller.advance(1); true }
            event.isCtrlPressed && event.key == Key.DirectionLeft -> { controller.advance(-1); true }
            event.isCtrlPressed && event.key == Key.Spacebar -> { controller.pause(); true }
            else -> false
        }
    }) {
        if (controller.fullPlayer) LandscapePlayer(controller)
        else Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                NavigationRail(Modifier.fillMaxHeight().width(90.dp), header = {
                    Icon(Icons.Default.MusicNote, "NekoPlayer", Modifier.padding(vertical = 22.dp), tint = MaterialTheme.colorScheme.primary)
                }) {
                    listOf("主页" to Icons.Default.Home, "音乐" to Icons.Default.MusicNote, "我的" to Icons.Default.Person).forEach { (label, icon) ->
                        NavigationRailItem(selected = tab == label, onClick = {
                            tab = label
                            if (label == "主页") controller.loadRecommendations()
                            if (label == "我的") controller.showLibrary("我的收藏")
                        }, icon = { Icon(icon, label) }, label = { Text(label) }, modifier = Modifier.padding(vertical = 6.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = controller::accounts) { Icon(Icons.Default.AccountCircle, "账号与 Cookie") }
                    IconButton(onClick = controller::settings) { Icon(Icons.Default.Settings, "设置与音质") }
                }
                Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(controller.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text(if (tab == "我的") "收藏、歌单与最近播放" else "现在开始，听见喜欢。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = controller::openLocal) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("本地音乐") }
                        FilledTonalButton(onClick = controller::accounts) { Text(if (CookieStore.appTokenValue().isBlank()) "登录本站" else "账号中心") }
                    }
                    if (tab == "我的") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("我的收藏", "最近播放", "播放队列").forEach { label ->
                                FilterChip(selected = controller.title == label, onClick = { controller.showLibrary(label) }, label = { Text(label) })
                            }
                            TextButton(onClick = controller::playlists) { Text("我的歌单") }
                            TextButton(onClick = { controller.sync() }) { Text("云端同步") }
                        }
                    } else {
                        SourcePicker(controller) { controller.sourceUsage(); showUsage = true }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(controller.query, { controller.query = it }, Modifier.weight(1f).onPreviewKeyEvent {
                                if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) { controller.search(); true } else false
                            }, singleLine = true, placeholder = { Text(if (controller.videoMode) "搜索 MV" else "搜索歌曲、歌手") },
                                leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
                            Button(onClick = { controller.search() }, enabled = !controller.loading) { Text("搜索") }
                            FilterChip(controller.videoMode, onClick = { controller.videoMode = !controller.videoMode; controller.songs = emptyList(); controller.videos = emptyList(); controller.hasMore = false }, label = { Text("MV") })
                        }
                    }
                    if (controller.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (controller.videoMode) VideoResults(controller, Modifier.weight(1f))
                    else SongResults(controller, Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(controller.status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (controller.hasMore) TextButton(onClick = { controller.search(true) }, enabled = !controller.loading) { Text("加载更多") }
                        TextButton(onClick = controller::effects) { Icon(Icons.Default.Equalizer, null); Spacer(Modifier.width(4.dp)); Text("音效") }
                    }
                }
            }
            HorizontalDivider()
            MiniPlayer(controller)
        }
    }
    controller.error?.let { problem ->
        AlertDialog(onDismissRequest = { controller.error = null }, title = { Text("操作未完成") }, text = { Text(problem) },
            confirmButton = { TextButton(onClick = { controller.error = null }) { Text("确定") } })
    }
    if (showUsage) AlertDialog(onDismissRequest = { showUsage = false }, title = { Text("本站音源调用统计") }, text = {
        val usage = controller.usage
        Column(Modifier.width(520.dp).heightIn(max = 450.dp)) {
            if (usage == null) Text(if (controller.loading) "正在读取…" else "请登录本站后重试")
            else {
                Text("${usage.date} · 北京时间零点重置")
                Text("全站 ${usage.used} / ${usage.limit} · 剩余 ${usage.remaining}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
                Text("仅音乐链接扣额度，歌词与 MV 不扣额度")
                LazyColumn { itemsIndexed(usage.users) { index, user ->
                    ListItem(headlineContent = { Text(user.name) }, supportingContent = { Text("今日 ${user.today} 次 · 累计 ${user.total} 次") }, leadingContent = { Text("${index + 1}") })
                } }
            }
        }
    }, confirmButton = { TextButton(onClick = { controller.sourceUsage() }) { Text("刷新") } }, dismissButton = { TextButton(onClick = { showUsage = false }) { Text("关闭") } })
}

@Composable
private fun SourcePicker(controller: DesktopController, onUsage: () -> Unit) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("netease" to "网易云音乐", "kugou" to "酷狗音乐", "animemusic-kg" to "惜缘惜梦音源").forEach { (source, name) ->
                FilterChip(selected = if (source.startsWith("animemusic-")) controller.source.startsWith("animemusic-") else controller.source == source,
                    onClick = { controller.switchSource(source) }, label = { Text(name) })
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onUsage) { Text("音源统计") }
        }
        if (controller.source.startsWith("animemusic-")) {
            if (CookieStore.appTokenValue().isBlank()) Text("登录本站账号后即可使用，共享每日 10,000 次音乐链接额度", style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AnimemusicRepository.platforms) { (id, label) ->
                    FilterChip(controller.source == "animemusic-$id", onClick = { controller.switchSource("animemusic-$id") }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun SongResults(controller: DesktopController, modifier: Modifier) {
    val scroll = rememberLazyListState()
    Box(modifier.fillMaxWidth()) {
        if (controller.songs.isEmpty()) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (controller.loading) "正在寻找音乐…" else "搜索喜欢的音乐，或打开本地文件", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { controller.loadRecommendations() }) { Text("加载推荐") }
        }
        LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 14.dp)) {
            items(controller.songs, key = Library::key) { song -> SongCard(controller, song) }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun SongCard(controller: DesktopController, song: Song) {
    controller.revision
    val favorite = controller.library.data.favorites.any { Library.key(it) == Library.key(song) }
    val playing = controller.current?.let(Library::key) == Library.key(song)
    val menu = listOf(ContextMenuItem("播放") { controller.start(song, controller.songs) }, ContextMenuItem(if (favorite) "取消收藏" else "收藏") { controller.favorite(song) },
        ContextMenuItem("加入歌单") { controller.addToPlaylist(song) }, ContextMenuItem("下载") { controller.download(song) }, ContextMenuItem("歌曲评论") { controller.comments(song) })
    ContextMenuArea(items = { menu }) {
        Card(onClick = { controller.start(song, controller.songs) }, modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (playing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cover(song.pc, Modifier.size(58.dp), song.nm)
                Column(Modifier.weight(1f)) {
                    Text(song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    Text(song.ar, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(sourceName(song.source), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { controller.favorite(song) }) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "取消收藏" else "收藏", tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded, { expanded = false }) { menu.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, onClick = { expanded = false; action.onClick() }) } }
                }
            }
        }
    }
}

@Composable
private fun VideoResults(controller: DesktopController, modifier: Modifier) {
    val scroll = rememberLazyListState()
    Box(modifier.fillMaxWidth()) {
        if (controller.videos.isEmpty()) Text("搜索 MV 后点击播放，使用系统浏览器打开视频", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(state = scroll, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 14.dp)) {
            items(controller.videos, key = { it.source + ":" + it.id }) { video ->
                Card(onClick = { controller.playVideo(video) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Cover(video.artwork, Modifier.size(84.dp, 54.dp), video.title)
                        Column(Modifier.weight(1f)) { Text(video.title); Text("${video.artist} · ${sourceName(video.source)}", style = MaterialTheme.typography.bodySmall) }
                        Icon(Icons.Default.PlayCircle, "播放 MV")
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun MiniPlayer(controller: DesktopController) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.weight(1f).clickable { controller.fullPlayer = true }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Cover(controller.current?.pc, Modifier.size(58.dp), "打开播放器")
            Column { Text(controller.current?.nm ?: "NekoPlayer", maxLines = 1, overflow = TextOverflow.Ellipsis); Text(controller.current?.ar ?: "选择音乐开始播放", style = MaterialTheme.typography.bodySmall) }
        }
        Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) { PlaybackControls(controller); SeekBar(controller) }
        Row(Modifier.width(155.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeUp, "音量", Modifier.size(20.dp)); Slider(controller.currentVolume, controller::setVolume, Modifier.weight(1f))
        }
        IconButton(onClick = { controller.fullPlayer = true }) { Icon(Icons.Default.OpenInFull, "横屏播放器") }
    }
}

@Composable
private fun PlaybackControls(controller: DesktopController) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = { controller.repeat = (controller.repeat + 1) % 4 }) {
            Icon(when (controller.repeat) { 2 -> Icons.Default.RepeatOne; 3 -> Icons.Default.Shuffle; else -> Icons.Default.Repeat }, listOf("顺序播放", "列表循环", "单曲循环", "随机播放")[controller.repeat], tint = if (controller.repeat == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = { controller.advance(-1) }) { Icon(Icons.Default.SkipPrevious, "上一首") }
        FilledIconButton(onClick = controller::pause, Modifier.size(48.dp)) { Icon(if (controller.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (controller.paused) "播放" else "暂停") }
        IconButton(onClick = { controller.advance(1) }) { Icon(Icons.Default.SkipNext, "下一首") }
        IconButton(onClick = { controller.showLibrary("播放队列"); controller.fullPlayer = false }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "播放队列") }
    }
}

@Composable
private fun SeekBar(controller: DesktopController) {
    var drag by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(PlayerClock(controller.position), style = MaterialTheme.typography.labelSmall)
        Slider(drag ?: controller.position.toFloat().coerceIn(0f, controller.duration.toFloat().coerceAtLeast(1f)), { drag = it }, Modifier.weight(1f),
            enabled = controller.duration > 0, valueRange = 0f..controller.duration.toFloat().coerceAtLeast(1f), onValueChangeFinished = { drag?.let { controller.seek(it.toDouble()) }; drag = null })
        Text(PlayerClock(controller.duration), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LandscapePlayer(controller: DesktopController) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { controller.fullPlayer = false }) { Icon(Icons.Default.KeyboardArrowDown, "返回") }
            Text("正在播放", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = controller::effects) { Text("音效与母带") }
        }
        Row(Modifier.weight(1f).padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(controller.current?.pc, Modifier.fillMaxWidth(0.8f).aspectRatio(1f).sizeIn(maxWidth = 360.dp, maxHeight = 360.dp), "歌曲封面")
                Text(controller.current?.nm ?: "尚未播放", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 2)
                Text(controller.current?.ar.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sourceName(controller.current?.source ?: "netease"), style = MaterialTheme.typography.labelMedium)
                Row {
                    IconButton(onClick = { controller.current?.let(controller::favorite) }) { Icon(Icons.Default.FavoriteBorder, "收藏") }
                    IconButton(onClick = { controller.current?.let(controller::download) }) { Icon(Icons.Default.Download, "下载") }
                    IconButton(onClick = { controller.current?.let(controller::comments) }) { Icon(Icons.Default.Comment, "评论") }
                }
                SeekBar(controller); PlaybackControls(controller)
            }
            val list = rememberLazyListState()
            val active = controller.lyrics.indexOfLast { it.time <= controller.position }
            LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
            Box(Modifier.weight(1.2f).fillMaxHeight()) {
                if (controller.lyrics.isEmpty()) Text("暂无歌词", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(state = list, contentPadding = PaddingValues(vertical = 100.dp, horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    itemsIndexed(controller.lyrics) { index, line ->
                        Column(Modifier.fillMaxWidth().clickable { controller.seek(line.time) }.padding(8.dp)) {
                            Text(line.text, style = if (index == active) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                fontWeight = if (index == active) FontWeight.Bold else FontWeight.Normal,
                                color = if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            line.translation?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Cover(url: String?, modifier: Modifier, description: String) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = null
        if (!url.isNullOrBlank() && (url.startsWith("https://") || url.startsWith("http://"))) value = withContext(Dispatchers.IO) {
            runCatching {
                ApiFactory.mediaClient().newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful || response.body == null || response.body!!.contentLength() > 8 * 1024 * 1024) null
                    else SkiaImage.makeFromEncoded(response.body!!.bytes()).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        image?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Default.MusicNote, description, Modifier.fillMaxSize(0.35f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun PlayerClock(seconds: Double) = "%02d:%02d".format(seconds.toInt().coerceAtLeast(0) / 60, seconds.toInt().coerceAtLeast(0) % 60)
