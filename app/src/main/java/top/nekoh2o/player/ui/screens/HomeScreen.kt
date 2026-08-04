package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.a11y.asButton

@Composable
fun HomeScreen(vm: PlayerViewModel, onOpenFullPlayer: () -> Unit) {
    val state by vm.ui.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("超级播放器", style = MaterialTheme.typography.headlineSmall)

        // 当前播放状态卡片（对应 web top-status）
        ElevatedCard(
            Modifier.fillMaxWidth().asButton(actionLabel = "打开播放器") {
                if (state.current != null) onOpenFullPlayer()
            }
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = state.current?.pc?.let { "$it?param=200y200" },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.current?.nm ?: "未播放",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.current?.ar ?: "-",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("已播 ${state.history.size} 首") },
                    leadingIcon = { Icon(Icons.Filled.Headphones, contentDescription = null) }
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关于本站", style = MaterialTheme.typography.titleSmall)
                InfoRow("站点名称", "超级播放器")
                InfoRow("技术栈", "Kotlin + Compose + Media3")
                InfoRow("功能", "在线搜歌 · 高音质 · 逐字歌词 · 后台播放")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
