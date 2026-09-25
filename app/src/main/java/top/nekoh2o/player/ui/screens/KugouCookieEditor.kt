package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.net.kugouLoginError
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults

@Composable
fun KugouCookieEditor(vm: PlayerViewModel) {
    val state by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var platform by remember { mutableIntStateOf(state.kgAccount.platform) }
    var draft by remember(platform) { mutableStateOf("") }
    var saved by remember(platform) { mutableStateOf("") }
    var visible by remember(platform) { mutableStateOf(false) }
    var busy by remember(platform) { mutableStateOf(true) }
    var message by remember(platform) { mutableStateOf("") }
    var error by remember(platform) { mutableStateOf<String?>(null) }
    var confirmClear by remember(platform) { mutableStateOf(false) }

    LaunchedEffect(platform) {
        try {
            saved = vm.loadKgCookie(platform)
            draft = saved
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { error = kugouLoginError(e)
        } finally { busy = false }
    }

    Text("酷狗 Cookie", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("原版", "概念版").forEachIndexed { index, title ->
            FilterChip(selected = platform == index, enabled = !busy, onClick = { platform = index },
                label = { Text(title) }, modifier = Modifier.weight(1f))
        }
    }
    Text("可查看、复制或导入对应版本的登录 Cookie。需包含 token 和 userid；若有 dfid、KUGOU_API_* 设备字段请一并保留。网页追踪 Cookie 无法替代登录凭据。",
        style = MaterialTheme.typography.bodySmall)
    if (platform != state.kgAccount.platform) {
        OutlinedButton(enabled = !busy, onClick = { vm.kgSwitchPlatform(platform) }) { Text("使用此版本") }
    } else {
        Text("当前使用的酷狗版本", style = MaterialTheme.typography.labelSmall)
    }
    Text("两个版本的 Cookie 分别保存，需要切换时点击「使用此版本」。Cookie 包含账号凭据，请勿分享。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(value = draft, onValueChange = { draft = it; error = null; message = "" },
        enabled = !busy, label = { Text(if (platform == 0) "酷狗原版 Cookie" else "酷狗概念版 Cookie") },
        modifier = Modifier.fillMaxWidth().height(160.dp), maxLines = 6,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = NekoDefaults.textFieldColors())
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = visible, onCheckedChange = { visible = it })
        Text("显示 Cookie 内容")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy && draft.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
            busy = true; error = null; message = ""
            scope.launch {
                try {
                    saved = vm.saveKgCookie(draft, platform)
                    draft = saved
                    message = "Cookie 已保存，账号有效性以酷狗返回结果为准"
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { error = kugouLoginError(e)
                } finally { busy = false }
            }
        }) { Text("保存") }
        OutlinedButton(enabled = !busy && saved.isNotEmpty(), onClick = {
            clipboard.setText(AnnotatedString(saved)); message = "已复制当前版本保存的 Cookie"
        }) { Text("复制") }
        OutlinedButton(enabled = !busy && saved.isNotEmpty(), onClick = { confirmClear = true }) { Text("清除") }
    }
    Text(if (saved.isEmpty()) "当前版本未保存 Cookie" else "当前版本已保存 Cookie（" + saved.length + " 字符）",
        style = MaterialTheme.typography.bodySmall)
    if (saved.split(';').any { it.trim().startsWith("token=") }) {
        OutlinedButton(enabled = !busy, onClick = {
            busy = true; error = null; message = ""
            scope.launch {
                try {
                    saved = vm.logoutKgCookie(platform)
                    draft = saved
                    message = "已退出当前版本账号，设备 Cookie 已保留"
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { error = kugouLoginError(e)
                } finally { busy = false }
            }
        }) { Text("退出登录（保留设备）") }
    }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清除" + if (platform == 0) "酷狗原版 Cookie" else "酷狗概念版 Cookie") },
        text = { Text("将清除该版本的登录及设备 Cookie，下次请求会重新初始化设备。另一版本不受影响。") },
        confirmButton = { TextButton(onClick = {
            confirmClear = false; busy = true; error = null; message = ""
            scope.launch {
                try {
                    vm.clearKgCookie(platform)
                    saved = ""; draft = ""; message = "当前版本的 Cookie 已清除"
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { error = kugouLoginError(e)
                } finally { busy = false }
            }
        }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
