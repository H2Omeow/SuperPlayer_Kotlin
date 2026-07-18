package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(vm: PlayerViewModel) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(Unit) {
        if (state.recSongs.isEmpty() && state.recPlaylists.isEmpty()) vm.loadRecommend()
    }

    var addTarget by remember { mutableStateOf<Song?>(null) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("搜索歌曲") },
            trailingIcon = {
                IconButton(onClick = { vm.doSearch() }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            },
            singleLine = true
        )

        if (state.suggestions.isNotEmpty()) {
            state.suggestions.forEach { sug ->
                Text(
                    sug,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { vm.doSearch(sug) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
            }
        }

        if (state.searching || state.recLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (state.results.isNotEmpty()) {
            SearchResultList(vm, state.results, state.hasMore) { addTarget = it }
        } else {
            RecommendContent(vm) { addTarget = it }
        }
    }

    addTarget?.let { song ->
        AddToPlaylistDialog(vm, song) { addTarget = null }
    }
}

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
                        Column(Modifier.width(120.dp).clickable { vm.openPlaylist(pl.id) }) {
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
                onAddToPlaylist = { onAddToPlaylist(song) }
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
    onAddToPlaylist: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
        IconButton(onClick = onFav) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "收藏",
                tint = if (isFav) MaterialTheme.colorScheme.primary else LocalContentColor.current
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建歌单") },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    singleLine = true, label = { Text("歌单名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        vm.createPlaylist(newName.trim())
                        val idx = vm.ui.value.playlists.lastIndex
                        if (idx >= 0) vm.addToPlaylist(idx, song)
                    }
                    newName = ""; creating = false; onDismiss()
                }) { Text("创建并添加") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } }
        )
    }
}
