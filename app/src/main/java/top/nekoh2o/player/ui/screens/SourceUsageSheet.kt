package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.SourceUsage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceUsageSheet(onDismiss: () -> Unit) {
    var usage by remember { mutableStateOf<SourceUsage?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(revision) {
        problem = null
        try { usage = withContext(Dispatchers.IO) { ApiFactory.animemusic.usage() } }
        catch (error: Exception) { problem = if (top.nekoh2o.player.data.net.CookieStore.appTokenValue().isBlank()) "请先在我的页面登录本站账号" else "统计读取失败，请重新登录或稍后重试" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("本站音源调用统计", style = MaterialTheme.typography.headlineSmall)
            Text("每日共享 10,000 次音乐链接调用，北京时间零点重置。歌词与 MV 不占额度。", Modifier.padding(vertical = 12.dp))
            problem?.let { Text(it) }
            usage?.let { data ->
                Text("${data.date} · ${data.used} / ${data.limit}", style = MaterialTheme.typography.titleLarge)
                Text("剩余 ${data.remaining} 次 · 我的今日调用 ${data.myToday} 次")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    itemsIndexed(data.users) { index, user -> ListItem(headlineContent = { Text(user.name) },
                        supportingContent = { Text("今日 ${user.today} 次 · 累计 ${user.total} 次") }, leadingContent = { Text("${index + 1}") }) }
                }
            }
            TextButton(onClick = { revision++ }) { Text("刷新") }
        }
    }
}
