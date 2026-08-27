package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import android.graphics.BitmapFactory
import android.util.Base64
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults

/**
 * 酷狗音乐登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouLoginScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mid by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var openid by remember { mutableStateOf("") }
    var accessToken by remember { mutableStateOf("") }
    var platform by remember { mutableIntStateOf(state.kgAccount.platform) }
    var loginMethod by remember { mutableIntStateOf(0) } // 0=验证码 1=MID+Token 2=QQ授权 3=QQ扫码
    var qqAppId by remember { mutableStateOf("102058589") } // 默认 AppID（用户可以修改）
    var countdown by remember { mutableIntStateOf(0) }
    var qrData by remember { mutableStateOf<top.nekoh2o.player.data.model.KgQQQRCreateData?>(null) }
    var qrChecking by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    // QQ 扫码轮询检查
    LaunchedEffect(qrData, qrChecking) {
        if (qrData != null && qrChecking) {
            while (qrChecking) {
                kotlinx.coroutines.delay(2000)
                val result = vm.kgCheckQQLoginQR(qrData!!)
                if (result != null) {
                    when (result.status) {
                        1 -> {
                            // 登录成功
                            qrChecking = false
                            vm.toast("QQ 扫码登录成功")
                            onBack()
                        }
                        2 -> {
                            // 二维码已过期
                            qrChecking = false
                            vm.toast("二维码已过期，请重新获取")
                            qrData = null
                        }
                        else -> {
                            // 继续等待扫描
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("酷狗音乐登录") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "选择版本",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = platform == 0,
                    onClick = { platform = 0 },
                    label = { Text("原版") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = platform == 1,
                    onClick = { platform = 1 },
                    label = { Text("概念版") },
                    modifier = Modifier.weight(1f)
                )
            }

            if (platform == 1) {
                Text(
                    "概念版支持领取VIP功能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider()

            Text(
                "登录方式",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = loginMethod == 0,
                    onClick = { loginMethod = 0 },
                    label = { Text("验证码") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = loginMethod == 1,
                    onClick = { loginMethod = 1 },
                    label = { Text("MID+Token") },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = loginMethod == 2,
                    onClick = { loginMethod = 2 },
                    label = { Text("手机QQ") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = loginMethod == 3,
                    onClick = { loginMethod = 3 },
                    label = { Text("QQ扫码") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))

            when (loginMethod) {
                0 -> {
                    // 验证码登录
                    Text(
                        "手机号验证码登录",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("手机号") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = NekoDefaults.textFieldColors()
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = { Text("验证码") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = NekoDefaults.textFieldColors()
                        )

                        Button(
                            onClick = {
                                if (phone.isNotEmpty()) {
                                    vm.kgSendCode(phone)
                                    countdown = 60
                                } else {
                                    vm.toast("请输入手机号")
                                }
                            },
                            enabled = countdown == 0 && phone.isNotEmpty()
                        ) {
                            Text(if (countdown > 0) "${countdown}s" else "获取验证码")
                        }
                    }

                    Button(
                        onClick = {
                            if (phone.isEmpty() || code.isEmpty()) {
                                vm.toast("请填写完整信息")
                            } else {
                                vm.kgLogin(phone, code, platform)
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = phone.isNotEmpty() && code.isNotEmpty()
                    ) {
                        Text("登录")
                    }
                }
                1 -> {
                    // MID+Token登录
                    Text(
                        "使用MID和Token登录",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = mid,
                        onValueChange = { mid = it },
                        label = { Text("MID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = NekoDefaults.textFieldColors()
                    )

                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = NekoDefaults.textFieldColors()
                    )

                    Button(
                        onClick = {
                            if (mid.isEmpty() || token.isEmpty()) {
                                vm.toast("请填写完整信息")
                            } else {
                                vm.kgLoginWithToken(mid, token, platform)
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = mid.isNotEmpty() && token.isNotEmpty()
                    ) {
                        Text("登录")
                    }

                    Text(
                        "如何获取MID和Token？\n在酷狗官网或APP登录后，从浏览器开发者工具或抓包工具中获取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                2 -> {
                    // 手机QQ登录（拉起QQ应用）
                    Text(
                        "手机QQ登录",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        "通过拉起手机QQ应用完成授权登录",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = qqAppId,
                        onValueChange = { qqAppId = it },
                        label = { Text("QQ 互联 AppID") },
                        placeholder = { Text("默认: 102058589") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = NekoDefaults.textFieldColors()
                    )

                    Text(
                        "如果默认 AppID 无法使用，请在 QQ 互联平台申请自己的 AppID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (qqAppId.isEmpty()) {
                                vm.toast("请输入 AppID")
                            } else {
                                vm.launchQQLogin(qqAppId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = qqAppId.isNotEmpty()
                    ) {
                        Text("拉起手机QQ登录")
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "说明：\n" +
                        "• 点击按钮将拉起手机QQ应用\n" +
                        "• 在QQ中完成授权后自动返回\n" +
                        "• 如果手机未安装QQ，请使用其他登录方式\n" +
                        "• 如果拉起失败，建议使用下方的QQ扫码登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                3 -> {
                    // QQ扫码登录
                    Text(
                        "QQ扫码登录",
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (qrData == null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val result = vm.kgCreateQQLoginQR()
                                    if (result != null) {
                                        qrData = result
                                        qrChecking = true  // 自动开始检测
                                    } else {
                                        vm.toast("获取二维码失败，请重试")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("获取二维码")
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "请使用手机QQ扫描二维码",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                // 显示二维码图片 - 解码 base64
                                qrData?.let { data ->
                                    val base64String = data.qrUrl.removePrefix("data:image/png;base64,")
                                    val imageBytes = Base64.decode(base64String, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    bitmap?.let {
                                        Image(
                                            painter = BitmapPainter(it.asImageBitmap()),
                                            contentDescription = "QQ登录二维码",
                                            modifier = Modifier.size(200.dp)
                                        )
                                    }
                                }

                                if (qrChecking) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                    Text("等待扫码...", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(
                                        "点击开始检测按钮开始轮询",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    qrData = null
                                    qrChecking = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重新获取")
                            }
                            OutlinedButton(
                                onClick = {
                                    qrChecking = !qrChecking
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (qrChecking) "停止检测" else "开始检测")
                            }
                        }
                    }

                    Text(
                        "使用手机QQ扫描二维码完成登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "登录说明",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "• 验证码登录：使用手机号和验证码登录\n" +
                "• MID+Token登录：适合从其他设备获取凭证后直接登录\n" +
                "• 手机QQ登录：直接拉起手机QQ应用完成授权（需要QQ互联AppID）\n" +
                "• QQ扫码登录：使用手机QQ扫码完成登录（推荐，无需AppID）\n" +
                "• 登录凭证会保存在本地并云端同步\n" +
                "• 概念版支持领取VIP功能（测试接口）\n" +
                "• 原版和概念版token不通用，需分别登录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
