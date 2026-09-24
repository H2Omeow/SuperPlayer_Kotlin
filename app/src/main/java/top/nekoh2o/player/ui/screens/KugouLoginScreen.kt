package top.nekoh2o.player.ui.screens

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.KugouApiException
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouLoginScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val finish by rememberUpdatedState(onBack)
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var platform by remember { mutableIntStateOf(state.kgAccount.platform) }
    var method by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var deadline by remember { mutableLongStateOf(0L) }
    var seconds by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<KgQrSession?>(null) }
    var qrDeadline by remember { mutableLongStateOf(0L) }
    var polling by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<KgLoginAccount>>(emptyList()) }

    LaunchedEffect(deadline) {
        do {
            seconds = ((deadline - SystemClock.elapsedRealtime() + 999) / 1000).coerceAtLeast(0).toInt()
            if (seconds > 0) delay(500)
        } while (seconds > 0)
    }

    LaunchedEffect(platform, method) {
        qr = null
        polling = false
        error = null
        message = ""
        accounts = emptyList()
        code = ""
    }

    LaunchedEffect(qr?.key, polling, platform, method) {
        val session = qr ?: return@LaunchedEffect
        if (!polling || method != 1 || session.platform != platform) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var failures = 0
            while (polling) {
                if (SystemClock.elapsedRealtime() >= qrDeadline) {
                    polling = false
                    message = "二维码已过期，请刷新"
                    break
                }
                delay(2000)
                try {
                    val result = vm.kgCheckLoginQR(session)
                    failures = 0
                    error = null
                    when (result.status) {
                        0 -> { polling = false; message = "二维码已过期，请刷新" }
                        1 -> message = "等待使用酷狗 App 扫码"
                        2 -> message = "已扫码，请在酷狗 App 中确认登录"
                        4 -> { polling = false; finish() }
                    }
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) {
                    failures++
                    error = loginError(e)
                    if (e is KugouApiException || failures >= 3) polling = false
                }
            }
        }
    }

    fun submit(userId: Long? = null) {
        if (busy) return
        busy = true
        error = null
        accounts = emptyList()
        scope.launch {
            try {
                vm.kgLogin(phone.trim(), code.trim(), platform, userId?.toString())
                finish()
            } catch (e: CancellationException) { throw e
            } catch (e: KugouAccountSelection) { accounts = e.accounts
            } catch (e: Exception) { error = loginError(e)
            } finally { busy = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("酷狗音乐登录") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
        })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("选择版本", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("原版", "概念版").forEachIndexed { index, title ->
                    FilterChip(selected = platform == index, enabled = !busy,
                        onClick = { platform = index }, label = { Text(title) }, modifier = Modifier.weight(1f))
                }
            }
            Text("两个版本的登录凭据分别保存。扫码时请使用对应版本的酷狗 App。", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("验证码", "酷狗扫码").forEachIndexed { index, title ->
                    FilterChip(selected = method == index, enabled = !busy,
                        onClick = { method = index }, label = { Text(title) }, modifier = Modifier.weight(1f))
                }
            }
            if (method == 0) {
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter(Char::isDigit).take(11) },
                    label = { Text("大陆手机号") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = NekoDefaults.textFieldColors())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(8) },
                        label = { Text("验证码") }, enabled = !busy, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = NekoDefaults.textFieldColors())
                    Button(enabled = !busy && seconds == 0 && phone.length == 11, onClick = {
                        busy = true; error = null
                        scope.launch {
                            try {
                                vm.kgSendCode(phone.trim(), platform)
                                deadline = SystemClock.elapsedRealtime() + 60000
                                message = "验证码已发送"
                            } catch (e: CancellationException) { throw e
                            } catch (e: Exception) { error = loginError(e)
                            } finally { busy = false }
                        }
                    }) { Text(if (seconds > 0) seconds.toString() + "s" else "获取验证码") }
                }
                Button(onClick = { submit() }, enabled = !busy && phone.length == 11 && code.length in 4..8, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "处理中…" else "登录")
                }
            } else {
                val bitmap = remember(qr?.image) {
                    runCatching {
                        val raw = qr?.image ?: return@runCatching null
                        val bytes = Base64.decode(raw.substringAfter("base64,", raw).removePrefix("base64://"), Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                if (qr != null) {
                    if (bitmap != null) Image(bitmap, "酷狗登录二维码", Modifier.size(240.dp).align(Alignment.CenterHorizontally))
                    else Text("二维码图片无法显示，请刷新", color = MaterialTheme.colorScheme.error)
                }
                Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                    qr = null; polling = false; busy = true; error = null; message = "正在获取二维码"
                    scope.launch {
                        try {
                            qr = vm.kgCreateLoginQR(platform)
                            qrDeadline = SystemClock.elapsedRealtime() + 180000
                            polling = true
                            message = "请使用" + if (platform == 1) "酷狗概念版扫码并确认" else "酷狗音乐 App 扫码并确认"
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { error = loginError(e); message = ""
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "获取中…" else if (qr == null) "获取二维码" else "刷新二维码") }
                if (polling) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            }
            if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (accounts.isNotEmpty()) AlertDialog(onDismissRequest = { accounts = emptyList() }, title = { Text("选择酷狗账号") }, text = {
        Column {
            accounts.forEach { account ->
                TextButton(onClick = { submit(account.userid) }) {
                    Text(account.nickname.ifBlank { "酷狗账号" } + "（" + account.userid + "）")
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = { accounts = emptyList() }) { Text("取消") } })
}

private fun loginError(error: Exception): String = when (error) {
    is KugouApiException, is IllegalArgumentException -> error.message ?: "登录失败，请重试"
    is java.io.IOException -> "网络连接失败，请检查网络后重试"
    else -> "酷狗响应异常，请稍后重试"
}
