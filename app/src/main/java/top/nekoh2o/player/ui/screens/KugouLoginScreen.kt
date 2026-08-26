package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
                    label = { Text("QQ授权") },
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
                    // QQ授权登录
                    Text(
                        "QQ授权登录",
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = openid,
                        onValueChange = { openid = it },
                        label = { Text("OpenID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = NekoDefaults.textFieldColors()
                    )

                    OutlinedTextField(
                        value = accessToken,
                        onValueChange = { accessToken = it },
                        label = { Text("Access Token") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        colors = NekoDefaults.textFieldColors()
                    )

                    Button(
                        onClick = {
                            if (openid.isEmpty() || accessToken.isEmpty()) {
                                vm.toast("请填写完整信息")
                            } else {
                                vm.kgLoginWithQQ(openid, accessToken)
                                onBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = openid.isNotEmpty() && accessToken.isNotEmpty()
                    ) {
                        Text("登录")
                    }

                    Text(
                        "如何获取QQ授权信息？\n通过QQ互联开放平台授权后获取OpenID和Access Token",
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
                                        qrChecking = true
                                    } else {
                                        vm.toast("获取二维码失败")
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
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 这里应该显示二维码图片
                                // 简化处理：显示二维码URL
                                Text(
                                    "请使用QQ扫描二维码",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "二维码地址: ${qrData!!.qrUrl}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (qrChecking) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                    Text("等待扫码...", style = MaterialTheme.typography.bodySmall)
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
                "• QQ授权登录：通过QQ互联获取OpenID和Access Token\n" +
                "• QQ扫码登录：使用手机QQ扫码完成登录\n" +
                "• 登录凭证会保存在本地并云端同步\n" +
                "• 概念版支持领取VIP功能（测试接口）\n" +
                "• 原版和概念版token不通用，需分别登录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
