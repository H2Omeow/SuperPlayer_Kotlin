package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.asButton

@Composable
fun HomeScreen(vm: PlayerViewModel, onOpenFullPlayer: () -> Unit) {
    val state by vm.ui.collectAsState()
    val recent = state.history.take(12)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("NekoPlayer", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                Text("让喜欢的音乐，陪你一会儿", style = MaterialTheme.typography.headlineSmall)
                Text("从一首熟悉的歌开始。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("继续收听", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(Modifier.size(72.dp), shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            if (state.current?.pc != null) AsyncImage(
                                model = state.current?.pc,
                                contentDescription = null,
                                contentScale = ContentScale.Crop
                            ) else Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(state.current?.nm ?: "还没有正在播放的歌曲",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(state.current?.ar ?: "前往音乐页，搜索你的第一首歌",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.current != null) Button(onClick = onOpenFullPlayer) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("打开播放器")
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryCount("我的收藏", state.favorites.size, Modifier.weight(1f))
                LibraryCount("最近播放", state.history.size, Modifier.weight(1f))
                LibraryCount("我的歌单", state.playlists.size, Modifier.weight(1f))
            }
        }
        item { Text("最近听过", style = MaterialTheme.typography.titleLarge) }
        if (recent.isEmpty()) item {
            Text("播放记录会出现在这里。你收藏的歌曲和创建的歌单都能在“我的”中找到。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(recent, key = { index, song -> "${song.source}:${song.id}:$index" }) { index, song ->
            ListItem(
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    .asButton(actionLabel = "播放 ${song.nm}") { vm.playAll(recent.drop(index) + recent.take(index)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                headlineContent = { Text(song.nm, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(song.ar, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    AsyncImage(song.pc, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop)
                },
                trailingContent = { Icon(Icons.Default.PlayArrow, null) }
            )
        }
    }
}

@Composable
private fun LibraryCount(label: String, count: Int, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
