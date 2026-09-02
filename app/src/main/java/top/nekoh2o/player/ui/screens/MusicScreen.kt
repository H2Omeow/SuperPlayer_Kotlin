package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.data.model.AlbumItem
import top.nekoh2o.player.data.model.ArtistItem
import top.nekoh2o.player.data.model.SearchType
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.clickableRow
import top.nekoh2o.player.ui.a11y.minTouchTarget
import top.nekoh2o.player.ui.a11y.toggleSemantics
import top.nekoh2o.player.ui.theme.NekoDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(vm: PlayerViewModel) {
    val state by vm.ui.collectAsState()

    // 歌手/专辑详情二级界面
    var detailArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var detailAlbum  by remember { mutableStateOf<AlbumItem?>(null) }
    var addTarget    by remember { mutableStateOf<Song?>(null) }

    // 二级界面：歌手详情（全屏，覆盖搜索列表）
    detailArtist?.let { artist ->
        ArtistDetailScreen(vm, artist, onBack = { detailArtist = null }, onAddToPlaylist = { addTarget = it })
        addTarget?.let { song -> AddToPlaylistDialog(vm, song) { addTarget = null } }
        return
    }
    // 二级界面：专辑详情（全屏，覆盖搜索列表）
    detailAlbum?.let { album ->
        AlbumDetailScreen(vm, album, onBack = { detailAlbum = null }, onAddToPlaylist = { addTarget = it })
        addTarget?.let { song -> AddToPlaylistDialog(vm, song) { addTarget = null } }
        return
    }

    LaunchedEffect(Unit) {
        if (state.recSongs.isEmpty() && state.recPlaylists.isEmpty()) vm.loadRecommend()
    }

    Column(Modifier.fillMaxSize()) {
        // 音乐源切换按钮
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.musicSource == "netease",
                onClick = { vm.switchMusicSource("netease") },
                label = { Text("网易云音乐") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.musicSource == "kugou",
                onClick = { vm.switchMusicSource("kugou") },
                label = { Text("酷狗音乐") },
                modifier = Modifier.weight(1f)
            )
        }

        // 搜索框
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("搜索${state.searchType.label}") },
            trailingIcon = {
                IconButton(onClick = { vm.doSearch() }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            },
            singleLine = true,
            colors = NekoDefaults.textFieldColors()
        )

        // 分类 Chip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            items(SearchType.entries) { type ->
                FilterChip(
                    selected = state.searchType == type,
                    onClick = { vm.setSearchType(type) },
                    label = { Text(type.label) }
                )
            }
        }

        // 建议列表
        if (state.suggestions.isNotEmpty()) {
            state.suggestions.forEach { sug ->
                Text(
                    sug,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow(rowLabel = sug, actionLabel = "搜索") { vm.doSearch(sug) }
                        .minTouchTarget()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
            }
        }

        if (state.searching || state.recLoading) {
            androidx.compose.animation.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        // 根据搜索类型切换结果视图
        when {
            state.searchType == SearchType.ARTIST && state.artistResults.isNotEmpty() ->
                ArtistResultList(state.artistResults, state.hasMore, vm) { detailArtist = it }

            state.searchType == SearchType.ALBUM && state.albumResults.isNotEmpty() ->
                AlbumResultList(state.albumResults, state.hasMore, vm) { detailAlbum = it }

            state.results.isNotEmpty() ->
                SearchResultList(vm, state.results, state.hasMore) { addTarget = it }

            !state.searching ->
                RecommendContent(vm) { addTarget = it }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistDialog(vm, song) { addTarget = null }
    }
}

// ==================== 歌手搜索结果 ====================
@Composable
private fun ArtistResultList(
    artists: List<ArtistItem>,
    hasMore: Boolean,
    vm: PlayerViewModel,
    onOpen: (ArtistItem) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(artists, key = { it.id }) { artist ->
            ListItem(
                headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    AsyncImage(
                        model = (artist.picUrl ?: artist.img1v1Url)?.let { "$it?param=100y100" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp))
                    )
                },
                modifier = Modifier.clickableRow(rowLabel = artist.name, actionLabel = "查看歌手") { onOpen(artist) }
            )
            HorizontalDivider()
        }
        if (hasMore && artists.isNotEmpty()) {
            item {
                LaunchedEffect(artists.size) { vm.loadMoreSearch() }
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ==================== 专辑搜索结果 ====================
@Composable
private fun AlbumResultList(
    albums: List<AlbumItem>,
    hasMore: Boolean,
    vm: PlayerViewModel,
    onOpen: (AlbumItem) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.id }) { album ->
            ListItem(
                headlineContent = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = album.artist?.let { { Text(it.name, maxLines = 1) } },
                leadingContent = {
                    AsyncImage(
                        model = album.picUrl?.let { "$it?param=100y100" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
                    )
                },
                modifier = Modifier.clickableRow(
                    rowLabel = album.artist?.let { "${album.name}，${it.name}" } ?: album.name,
                    actionLabel = "查看专辑"
                ) { onOpen(album) }
            )
            HorizontalDivider()
        }
        if (hasMore && albums.isNotEmpty()) {
            item {
                LaunchedEffect(albums.size) { vm.loadMoreSearch() }
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ==================== 歌手详情二级界面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArtistDetailScreen(
    vm: PlayerViewModel,
    artist: ArtistItem,
    onBack: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(artist.id) {
        vm.loadArtistSongs(artist.id) { result ->
            songs = result
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        val list = songs ?: emptyList()
        LazyColumn(Modifier.fillMaxSize()) {
            // 头部：大封面 + 歌手名 + 歌曲数
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = (artist.picUrl ?: artist.img1v1Url)?.let { "$it?param=400y400" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(160.dp).clip(RoundedCornerShape(80.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(artist.name, style = MaterialTheme.typography.titleLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!loading) {
                        Text("共 ${list.size} 首热门歌曲",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (list.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.playAll(list) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("播放全部")
                        }
                    }
                }
                HorizontalDivider()
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text("暂无歌曲") }
                }
            } else {
                items(list, key = { it.id }) { song ->
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
    }
}

// ==================== 专辑详情二级界面 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumDetailScreen(
    vm: PlayerViewModel,
    album: AlbumItem,
    onBack: () -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    var songs by remember { mutableStateOf<List<Song>?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(album.id) {
        vm.loadAlbumSongs(album.id) { result ->
            songs = result
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        val list = songs ?: emptyList()
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = album.picUrl?.let { "$it?param=400y400" },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(180.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(album.name, style = MaterialTheme.typography.titleLarge,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    album.artist?.let {
                        Text(it.name, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!loading) {
                        Text("共 ${list.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (list.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.playAll(list) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("播放全部")
                        }
                    }
                }
                HorizontalDivider()
            }
            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (list.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { Text("暂无歌曲") }
                }
            } else {
                items(list, key = { it.id }) { song ->
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
    }
}

// ==================== 歌曲搜索结果 ====================
@Composable
private fun RecommendContent(vm: PlayerViewModel, onAddToPlaylist: (Song) -> Unit) {
    val state by vm.ui.collectAsState()

    LazyColumn(Modifier.fillMaxSize()) {
        if (state.recPlaylists.isNotEmpty()) {
            item {
                Text(
                    "推荐歌单", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.recPlaylists, key = { it.id }) { pl ->
                        Column(
                            Modifier.width(120.dp)
                                .clickableRow(rowLabel = pl.name, actionLabel = "打开歌单") { vm.openPlaylist(pl.id) }
                        ) {
                            AsyncImage(
                                model = pl.picUrl?.let { "$it?param=200y200" },
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text(
                                pl.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "推荐歌曲", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        items(state.recSongs, key = { it.id }) { song ->
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
private fun SearchResultList(
    vm: PlayerViewModel,
    results: List<Song>,
    hasMore: Boolean,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { song ->
            SongRow(
                song = song,
                isFav = vm.isFav(song.id),
                onPlay = { vm.playNow(song) },
                onAdd = { vm.addToQueue(song) },
                onFav = { vm.toggleFav(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
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
        if (hasMore && results.isNotEmpty()) {
            item {
                LaunchedEffect(results.size) { vm.loadMoreSearch() }
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    isFav: Boolean,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onFav: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面 + 标题合并为单个「播放」按钮节点，TalkBack 朗读为一条
        Row(
            Modifier.weight(1f)
                .clickableRow(rowLabel = "${song.nm}，${song.ar}", actionLabel = "播放", onClick = onPlay),
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
        IconButton(
            onClick = onFav,
            modifier = Modifier.toggleSemantics("收藏", if (isFav) "已收藏" else "未收藏")
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = scale,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                ),
                label = "favorite_scale_list"
            )

            LaunchedEffect(isFav) {
                if (isFav) {
                    scale = 1.3f
                    kotlinx.coroutines.delay(150)
                    scale = 1f
                }
            }

            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (isFav) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                modifier = Modifier.graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
            )
        }
        if (onAddToPlaylist != null) {
            IconButton(onClick = onAddToPlaylist) {
                Icon(Icons.Filled.PlaylistAdd, contentDescription = "加入歌单")
            }
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = "加入队列")
        }
    }
}

@Composable
fun AddToPlaylistDialog(vm: PlayerViewModel, song: Song, onDismiss: () -> Unit) {
    val state by vm.ui.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入到歌单") },
        text = {
            Column {
                ListItem(
                    headlineContent = { Text("当前播放列表") },
                    leadingContent = { Icon(Icons.Filled.Add, null) },
                    modifier = Modifier.clickable { vm.addToQueue(song); onDismiss() }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("新建歌单…") },
                    leadingContent = { Icon(Icons.Filled.PlaylistAdd, null) },
                    modifier = Modifier.clickable { creating = true }
                )
                state.playlists.forEachIndexed { i, pl ->
                    ListItem(
                        headlineContent = { Text(pl.name) },
                        supportingContent = { Text("${pl.songs.size} 首") },
                        modifier = Modifier.clickable { vm.addToPlaylist(i, song); onDismiss() }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, colors = NekoDefaults.textButtonColors()) { Text("关闭") }
        }
    )

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
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
                        if (newName.isNotBlank()) {
                            vm.createPlaylist(newName.trim())
                            val idx = vm.ui.value.playlists.lastIndex
                            if (idx >= 0) vm.addToPlaylist(idx, song)
                        }
                        newName = ""; creating = false; onDismiss()
                    },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("创建并添加") }
            },
            dismissButton = {
                TextButton(
                    onClick = { creating = false },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }
}
