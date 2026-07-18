package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.ui.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val s = state.settings

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("个性化设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ========== App 全局背景 ==========
            Text("App 全局背景", style = MaterialTheme.typography.titleMedium)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("启用壁纸背景", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = s.globalBgEnabled,
                    onCheckedChange = { vm.setGlobalBgEnabled(it) }
                )
            }

            if (s.globalBgEnabled) {
                OutlinedButton(onClick = { vm.refreshWallpaper() }) { Text("刷新壁纸") }

                Column {
                    Text("全局遮罩明暗 (${(s.globalMaskAlpha * 100).toInt()}%)",
                        style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = s.globalMaskAlpha,
                        onValueChange = { vm.setGlobalMaskAlpha(it) },
                        valueRange = 0f..0.9f
                    )
                }
                Column {
                    Text("全局模糊强度 (${s.globalBlurRadius}px)",
                        style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = s.globalBlurRadius.toFloat(),
                        onValueChange = { vm.setGlobalBlurRadius(it.toInt()) },
                        valueRange = 0f..40f
                    )
                }
            }

            HorizontalDivider()

            // ========== 全屏播放器背景 ==========
            Text("全屏播放器背景", style = MaterialTheme.typography.titleMedium)

            Row {
                FilterChip(
                    selected = s.fpBgSource == BgSource.COVER,
                    onClick = { vm.setFpBgSource(BgSource.COVER) },
                    label = { Text("歌曲封面") }
                )
                Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = s.fpBgSource == BgSource.WALLPAPER,
                    onClick = { vm.setFpBgSource(BgSource.WALLPAPER) },
                    label = { Text("动态壁纸") }
                )
            }

            Column {
                Text("播放器遮罩明暗 (${(s.fpMaskAlpha * 100).toInt()}%)",
                    style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.fpMaskAlpha,
                    onValueChange = { vm.setFpMaskAlpha(it) },
                    valueRange = 0f..0.9f
                )
            }
            Column {
                Text("播放器模糊强度 (${s.fpBlurRadius}px)",
                    style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = s.fpBlurRadius.toFloat(),
                    onValueChange = { vm.setFpBlurRadius(it.toInt()) },
                    valueRange = 0f..40f
                )
                Text("模糊仅在 Android 12 及以上生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // ========== 音质 ==========
            Text("音质", style = MaterialTheme.typography.titleMedium)
            val levels = listOf(
                "standard" to "标准", "higher" to "较高",
                "exhigh" to "极高", "lossless" to "无损"
            )
            levels.forEach { (value, label) ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(state.quality == value) { vm.setQuality(value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.quality == value,
                        onClick = { vm.setQuality(value) }
                    )
                    Text(label)
                }
            }

            HorizontalDivider()

            // ========== 定时关闭 ==========
            Text(
                if (state.sleepMinutes > 0) "定时关闭（${state.sleepMinutes} 分钟后）"
                else "定时关闭",
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 15, 30, 45, 60).forEach { m ->
                    FilterChip(
                        selected = state.sleepMinutes == m,
                        onClick = { vm.setSleepTimer(m) },
                        label = { Text(if (m == 0) "关闭" else "${m}分") }
                    )
                }
            }
        }
    }
}
