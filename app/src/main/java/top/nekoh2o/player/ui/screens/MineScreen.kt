package top.nekoh2o.player.ui.screens

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.asButton
import top.nekoh2o.player.ui.a11y.clickableRow
import top.nekoh2o.player.ui.theme.NekoDefaults

@Composable
fun MineScreen(vm: PlayerViewModel, onStartSsoLogin: () -> Unit) {
    val state by vm.ui.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var openedPlaylist by remember { mutableIntStateOf(-1) }
    var addTarget by remember { mutableStateOf<Song?>(null) }
    var showQrLogin by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showNcScreen by remember { mutableStateOf(false) }
    var showKgScreen by remember { mutableStateOf(false) }
    var showKgLogin by remember { mutableStateOf(false) }
    val titles = listOf("播放记录", "我的收藏", "自定义歌单")

    if (showSettings) {
        SettingsScreen(vm) { showSettings = false }
        return
    }
    if (showNcScreen) {
        NeteaseScreen(vm) { showNcScreen = false }
        return
    }
    if (showKgLogin) {
        KugouLoginScreen(vm) {
            showKgLogin = false
            showKgScreen = false
        }
        return
    }
    if (showKgScreen) {
        KugouAccountScreen(
            vm = vm,
            onBack = { showKgScreen = false },
            onLogin = {
                showKgScreen = false
                showKgLogin = true
            }
        )
        return
    }
    if (openedPlaylist in state.playlists.indices) {
        PlaylistDetail(vm, openedPlaylist, onBack = { openedPlaylist = -1 })
        return
    }

    Column(Modifier.fillMaxSize()) {
        UserCard(
            state = state,
            onLogin = onStartSsoLogin,
            onLogout = { vm.logout() },
            onNetease = { showNcScreen = true },
            onKugou = { showKgScreen = true },
            onSettings = { showSettings = true }
        )
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
            }
        }
        when (tab) {
            0 -> SongList(vm, state.history, "暂无播放记录") { addTarget = it }
            1 -> SongList(vm, state.favorites, "暂无收藏") { addTarget = it }
            2 -> PlaylistList(vm) { openedPlaylist = it }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistDialog(vm, song) { addTarget = null }
    }
}

@Composable
private fun UserCard(
    state: top.nekoh2o.player.ui.UiState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onNetease: () -> Unit,
    onKugou: () -> Unit,
    onSettings: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val u = state.user
            AsyncImage(
                model = u?.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    u?.nickname ?: u?.username ?: "未登录用户",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (u != null) "ID: ${u.id}" else "登录以同步多端数据",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
            Column {
                if (state.loggedIn) {
                    TextButton(
                        onClick = {
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                            android.webkit.CookieManager.getInstance().flush()
                            onLogout()
                        },
                        colors = NekoDefaults.textButtonColors()
                    ) { Text("退出") }
                } else {
                    TextButton(onClick = onLogin, colors = NekoDefaults.textButtonColors()) { Text("登录") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onNetease, colors = NekoDefaults.textButtonColors()) { Text("网易云") }
                    TextButton(onClick = onKugou, colors = NekoDefaults.textButtonColors()) { Text("酷狗") }
                }
            }
        }
    }
}

@Composable
private fun SongList(
    vm: PlayerViewModel,
    songs: List<Song>,
    emptyHint: String,
    onAddToPlaylist: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text(emptyHint) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextButton(
                onClick = { vm.playAll(songs) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                colors = NekoDefaults.textButtonColors()
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("播放全部 (${songs.size} 首)")
            }
            HorizontalDivider()
        }
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isFav = vm.isFav(song.id),
                onPlay = { vm.playNow(song) },
                onAdd = { vm.addToQueue(song) },
                onFav = { vm.toggleFav(song) },
                onAddToPlaylist = { onAddToPlaylist(song) }
            )
        }
    }
}

@Composable
private fun PlaylistList(vm: PlayerViewModel, onOpen: (Int) -> Unit) {
    val state by vm.ui.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Button(onClick = { showCreate = true }, modifier = Modifier.padding(12.dp)) {
            Text("新建歌单")
        }
        if (state.playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("暂无自定义歌单") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.playlists.size) { i ->
                    val pl = state.playlists[i]
                    ListItem(
                        headlineContent = { Text(pl.name) },
                        supportingContent = { Text("${pl.songs.size} 首歌曲") },
                        leadingContent = {
                            AsyncImage(
                                model = pl.songs.firstOrNull()?.pc?.let { "$it?param=100y100" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { vm.deletePlaylist(i) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除歌单")
                            }
                        },
                        modifier = Modifier.asButton(actionLabel = "打开歌单") { onOpen(i) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, label = { Text("歌单名称") },
                    colors = NekoDefaults.textFieldColors()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) vm.createPlaylist(newName.trim())
                        newName = ""; showCreate = false
                    },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreate = false },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetail(vm: PlayerViewModel, index: Int, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val pl = state.playlists.getOrNull(index) ?: run { onBack(); return }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        if (pl.songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("歌单为空") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    TextButton(
                        onClick = { vm.playAll(pl.songs) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        colors = NekoDefaults.textButtonColors()
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("播放全部 (${pl.songs.size} 首)")
                    }
                    HorizontalDivider()
                }
                items(pl.songs.size) { i ->
                    val song = pl.songs[i]
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f).clickableRow(
                                rowLabel = "${song.nm}，${song.ar}", actionLabel = "播放"
                            ) { vm.playNow(song) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.pc?.let { "$it?param=100y100" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.ar, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { vm.removeFromPlaylist(index, i) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "移除")
                        }
                    }
                }
            }
        }
    }
}
