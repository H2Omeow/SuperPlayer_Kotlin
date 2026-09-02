package top.nekoh2o.player.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import top.nekoh2o.player.ui.PlayMode
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.clickableRow
import top.nekoh2o.player.ui.a11y.toggleSemantics
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

        // 开了全局背景时 Scaffold 透明，让壁纸透出。
        // 控件本身的透明度已由 NekoTheme 统一处理，这里只管背景层。
        val transparent = state.settings.globalBgEnabled && state.wallpaperUrl != null
        Scaffold(
            containerColor = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(vm, onOpenFullPlayer)
                    NavigationBar {
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

    // surfaceContainerHigh 已由 NekoTheme 按 controlAlpha 调过透明度
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            val progress = if (state.durationMs > 0)
                state.positionMs.toFloat() / state.durationMs else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 封面+标题合并为「打开播放器」按钮，控制按钮各自独立可聚焦
                Row(
                    Modifier.weight(1f)
                        .clickableRow(rowLabel = "${cur.nm}，${cur.ar}", actionLabel = "打开播放器") {
                            onOpenFullPlayer()
                        },
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
                }
                val modeLabel = when (state.playMode) {
                    PlayMode.LOOP -> "列表循环"
                    PlayMode.SINGLE -> "单曲循环"
                    PlayMode.RANDOM -> "随机播放"
                }
                IconButton(
                    onClick = { vm.cyclePlayMode() },
                    modifier = Modifier.toggleSemantics("播放模式", modeLabel)
                ) {
                    AnimatedContent(
                        targetState = state.playMode,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(200)) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = tween(200)
                            )).togetherWith(
                                fadeOut(animationSpec = tween(200)) + scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(200)
                                )
                            )
                        },
                        label = "play_mode_mini"
                    ) { mode ->
                        Icon(
                            when (mode) {
                                PlayMode.LOOP -> Icons.Filled.Repeat
                                PlayMode.SINGLE -> Icons.Filled.RepeatOne
                                PlayMode.RANDOM -> Icons.Filled.Shuffle
                            },
                            contentDescription = null
                        )
                    }
                }
                IconButton(onClick = { vm.prev() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
                }
                IconButton(onClick = { vm.togglePlay() }) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = state.isPlaying,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(150)) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = tween(150)
                            )).togetherWith(
                                fadeOut(animationSpec = tween(150)) + scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = tween(150)
                                )
                            )
                        },
                        label = "play_pause_mini"
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "暂停" else "播放"
                        )
                    }
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
                }
            }
        }
    }
}
