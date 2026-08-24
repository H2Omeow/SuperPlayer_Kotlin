package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.model.NcAccountResp
import top.nekoh2o.player.data.model.NcPlaylistItem
import top.nekoh2o.player.data.model.NcRecordItem
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.asButton
import top.nekoh2o.player.ui.theme.NekoDefaults

/**
 * 网易云音乐界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeteaseScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hasNcCookie = remember { CookieStore.hasNcUserCookie() }
    var showQrLogin by remember { mutableStateOf(!hasNcCookie) }
    var ncAccount by remember { mutableStateOf<NcAccountResp?>(null) }
    var loading by remember { mutableStateOf(false) }
    var currentTab by remember { mutableIntStateOf(0) }

    // 歌单、红心、播放记录
    var playlists by remember { mutableStateOf<List<NcPlaylistItem>>(emptyList()) }
    var likeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playRecords by remember { mutableStateOf<List<NcRecordItem>>(emptyList()) }

    // 加载网易云账号信息
    LaunchedEffect(hasNcCookie) {
        if (hasNcCookie && !showQrLogin) {
            loading = true
            ncAccount = vm.fetchNcAccount()
            loading = false
        }
    }

    if (showQrLogin) {
        QrLoginDialog(vm) {
            showQrLogin = false
            // 登录成功后刷新
            scope.launch {
                loading = true
                ncAccount = vm.fetchNcAccount()
                loading = false
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网易云音乐") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 账号信息卡片
            NcAccountCard(ncAccount, loading) {
                showQrLogin = true
            }

            if (ncAccount != null) {
                // Tab 切换
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        text = { Text("歌单") },
                        icon = { Icon(Icons.Filled.QueueMusic, null) }
                    )
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        text = { Text("红心") },
                        icon = { Icon(Icons.Filled.Favorite, null) }
                    )
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        text = { Text("播放记录") },
                        icon = { Icon(Icons.Filled.History, null) }
                    )
                }

                // 内容区域
                when (currentTab) {
                    0 -> NcPlaylistTab(vm, ncAccount, playlists, loading) { list ->
                        playlists = list
                    }
                    1 -> NcLikeSongsTab(vm, ncAccount, likeSongs, loading) { songs ->
                        likeSongs = songs
                    }
                    2 -> NcPlayRecordTab(vm, ncAccount, playRecords, loading) { records ->
                        playRecords = records
                    }
                }
            }
        }
    }
}

@Composable
private fun NcAccountCard(
    account: NcAccountResp?,
    loading: Boolean,
    onRelogin: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(12.dp)) {
        if (loading) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (account == null) {
            Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("未登录或 Cookie 已失效", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRelogin, colors = NekoDefaults.textButtonColors()) {
                        Text("重新登录")
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = account.profile?.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(28.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        account.profile?.nickname ?: "网易云用户",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        vipTypeName(account.account?.vipType ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (account.account?.vipType ?: 0 > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 歌单列表 Tab
 */
@Composable
private fun NcPlaylistTab(
    vm: PlayerViewModel,
    account: NcAccountResp?,
    playlists: List<NcPlaylistItem>,
    loading: Boolean,
    onLoaded: (List<NcPlaylistItem>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = account?.profile?.userId ?: return

    LaunchedEffect(uid) {
        if (playlists.isEmpty() && !loading) {
            val list = vm.fetchNcPlaylists(uid)
            onLoaded(list)
        }
    }

    if (loading || playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            if (loading) CircularProgressIndicator() else Text("暂无歌单")
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = playlist.coverImgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.trackCount} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            vm.syncNcPlaylistToLocal(playlist)
                        }
                    }
                ) {
                    Icon(Icons.Filled.CloudDownload, "同步")
                }
            }
            HorizontalDivider()
        }
    }
}

/**
 * 红心歌曲 Tab
 */
@Composable
private fun NcLikeSongsTab(
    vm: PlayerViewModel,
    account: NcAccountResp?,
    songs: List<Song>,
    loading: Boolean,
    onLoaded: (List<Song>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = account?.profile?.userId ?: return

    LaunchedEffect(uid) {
        if (songs.isEmpty() && !loading) {
            val list = vm.fetchNcLikeSongs(uid)
            onLoaded(list)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text("暂无红心歌曲")
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("共 ${songs.size} 首", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = {
                        scope.launch {
                            vm.syncNcLikeSongsToLocal(songs)
                        }
                    },
                    colors = NekoDefaults.textButtonColors()
                ) {
                    Icon(Icons.Filled.CloudDownload, null)
                    Spacer(Modifier.width(4.dp))
                    Text("全部同步到收藏")
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongRow(song) { vm.playNow(song) }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 播放记录 Tab
 */
@Composable
private fun NcPlayRecordTab(
    vm: PlayerViewModel,
    account: NcAccountResp?,
    records: List<NcRecordItem>,
    loading: Boolean,
    onLoaded: (List<NcRecordItem>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = account?.profile?.userId ?: return

    LaunchedEffect(uid) {
        if (records.isEmpty() && !loading) {
            val list = vm.fetchNcPlayRecord(uid)
            onLoaded(list)
        }
    }

    val songs = remember(records) { records.map { it.song.toSong() } }

    Column(Modifier.fillMaxSize()) {
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (loading) CircularProgressIndicator() else Text("暂无播放记录")
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("最近一周 ${records.size} 首", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = {
                        scope.launch {
                            vm.syncNcRecordToLocal(songs)
                        }
                    },
                    colors = NekoDefaults.textButtonColors()
                ) {
                    Icon(Icons.Filled.CloudDownload, null)
                    Spacer(Modifier.width(4.dp))
                    Text("全部同步到历史")
                }
            }
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxSize()) {
                items(records, key = { it.song.id }) { record ->
                    Column {
                        SongRow(record.song.toSong()) { vm.playNow(record.song.toSong()) }
                        Text(
                            "播放 ${record.playCount} 次",
                            modifier = Modifier.padding(start = 72.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.pc,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.nm,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.ar,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 辅助函数
private fun vipTypeName(vipType: Int): String = when (vipType) {
    0 -> "普通用户"
    1 -> "黑胶 VIP"
    11 -> "超级 VIP"
    else -> "VIP"
}

private fun formatVipExpire(timestamp: Long): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
    return date
}

// NcSongDetail 转 Song 扩展
private fun top.nekoh2o.player.data.model.NcSongDetail.toSong() = Song(
    id = this.id,
    nm = this.name,
    ar = this.ar.joinToString("/") { it.name },
    pc = this.al?.picUrl
)
