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
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults

/**
 * 酷狗音乐登录界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouLoginScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var mid by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var platform by remember { mutableIntStateOf(state.kgAccount.platform) }
    var loginMethod by remember { mutableIntStateOf(0) } // 0=验证码 1=MID+Token
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
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
                    label = { Text("验证码登录") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = loginMethod == 1,
                    onClick = { loginMethod = 1 },
                    label = { Text("MID+Token") },
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
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "登录说明",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "• 验证码登录：使用手机号和验证码登录\n" +
                "• MID+Token登录：适合从其他设备获取凭证后直接登录\n" +
                "• 登录凭证会保存在本地并云端同步\n" +
                "• 概念版支持领取VIP功能（测试接口）\n" +
                "• 原版和概念版token不通用，需分别登录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
