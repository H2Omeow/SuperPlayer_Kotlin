package top.nekoh2o.player.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.ui.PlayMode
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.screens.HomeScreen
import top.nekoh2o.player.ui.screens.MineScreen
import top.nekoh2o.player.ui.screens.MusicScreen

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("主页", Icons.Filled.Home),
    MUSIC("音乐", Icons.Filled.MusicNote),
    MINE("我的", Icons.Filled.Person)
}

@Composable
fun MainScaffold(
    vm: PlayerViewModel,
    onOpenFullPlayer: () -> Unit,
    onStartSsoLogin: () -> Unit
) {
    val state by vm.ui.collectAsState()
    var tab by remember { mutableStateOf(Tab.HOME) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 全局壁纸背景层
        if (state.settings.globalBgEnabled && state.wallpaperUrl != null) {
            AsyncImage(
                model = state.wallpaperUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(state.settings.globalBlurRadius.dp)
            )
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = state.settings.globalMaskAlpha))
            )
        }

        // 开了全局背景时 Scaffold 透明，让壁纸透出
        val transparent = state.settings.globalBgEnabled && state.wallpaperUrl != null
        Scaffold(
            containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(vm, onOpenFullPlayer)
                    NavigationBar(
                        containerColor = if (transparent)
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    tab = t
                                    if (t == Tab.MUSIC && state.recSongs.isEmpty()) vm.loadRecommend()
                                },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    Tab.HOME -> HomeScreen(vm, onOpenFullPlayer)
                    Tab.MUSIC -> MusicScreen(vm)
                    Tab.MINE -> MineScreen(vm, onStartSsoLogin)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpenFullPlayer: () -> Unit) {
    val state by vm.ui.collectAsState()
    val cur = state.current ?: return

    val transparent = state.settings.globalBgEnabled && state.wallpaperUrl != null
    Surface(
        color = if (transparent) MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            val progress = if (state.durationMs > 0)
                state.positionMs.toFloat() / state.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                Modifier.fillMaxWidth()
                    .clickable { onOpenFullPlayer() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = cur.pc?.let { "$it?param=100y100" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur.nm, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(cur.ar, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { vm.cyclePlayMode() }) {
                    Icon(
                        when (state.playMode) {
                            PlayMode.LOOP -> Icons.Filled.Repeat
                            PlayMode.SINGLE -> Icons.Filled.RepeatOne
                            PlayMode.RANDOM -> Icons.Filled.Shuffle
                        },
                        contentDescription = "播放模式"
                    )
                }
                IconButton(onClick = { vm.prev() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停"
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
            }
        }
    }
}
