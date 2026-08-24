package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults
import java.text.SimpleDateFormat
import java.util.*

/**
 * 酷狗音乐账户管理界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KugouAccountScreen(vm: PlayerViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by vm.ui.collectAsState()
    val kgAccount = state.kgAccount
    var showVipDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (kgAccount.isValid) {
            vm.refreshKgAccount()
        }
    }

    // VIP领取对话框（仅概念版）
    if (showVipDialog && kgAccount.platform == 1) {
        VipReceiveDialog(
            onDismiss = { showVipDialog = false },
            onReceive = { vipType, days ->
                vm.kgReceiveVip(vipType, days)
                showVipDialog = false
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("酷狗音乐账户") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
            }
        )

        if (!kgAccount.isValid) {
            // 未登录状态
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "未登录",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "登录酷狗音乐账户后可以：\n" +
                    "• 搜索和播放酷狗音乐\n" +
                    "• 查看个人歌单和历史\n" +
                    "• 概念版可领取VIP",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("登录酷狗账户")
                }
            }
        } else {
            // 已登录状态
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 用户信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 头像
                        AsyncImage(
                            model = kgAccount.avatar,
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                kgAccount.nickname.ifEmpty { "酷狗用户" },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "UID: ${kgAccount.userId}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (kgAccount.vipType) {
                                    1 -> "VIP会员"
                                    2 -> "豪华VIP"
                                    else -> "普通用户"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (kgAccount.vipType > 0) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                HorizontalDivider()

                // 版本信息
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前版本", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (kgAccount.platform == 0) "原版" else "概念版",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            vm.kgSwitchPlatform(if (kgAccount.platform == 0) 1 else 0)
                        },
                        colors = NekoDefaults.outlinedButtonColors()
                    ) {
                        Text("切换到${if (kgAccount.platform == 0) "概念版" else "原版"}")
                    }
                }

                // VIP信息
                if (kgAccount.vipType > 0) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("会员信息", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "会员类型：${if (kgAccount.vipType == 2) "豪华VIP" else "VIP"}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (kgAccount.vipEndTime > 0) {
                                val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(Date(kgAccount.vipEndTime * 1000))
                                Text(
                                    "到期时间：$date",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // 概念版专属功能
                if (kgAccount.platform == 1) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("概念版专属", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "概念版支持领取VIP功能（测试接口）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { showVipDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("领取VIP")
                            }
                        }
                    }
                }

                HorizontalDivider()

                // 功能说明
                Text(
                    "功能说明",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "• 已登录的酷狗账户可以搜索和播放酷狗音乐\n" +
                    "• 原版和概念版使用不同的token，切换版本需重新登录\n" +
                    "• 概念版支持领取VIP（测试接口，可能随时失效）\n" +
                    "• 登录凭证会自动云端同步",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VipReceiveDialog(
    onDismiss: () -> Unit,
    onReceive: (vipType: Int, days: Int) -> Unit
) {
    var vipType by remember { mutableIntStateOf(1) }
    var days by remember { mutableIntStateOf(7) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("领取VIP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("VIP类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = vipType == 1,
                        onClick = { vipType = 1 },
                        label = { Text("VIP") }
                    )
                    FilterChip(
                        selected = vipType == 2,
                        onClick = { vipType = 2 },
                        label = { Text("豪华VIP") }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text("领取天数")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 365).forEach { d ->
                        FilterChip(
                            selected = days == d,
                            onClick = { days = d },
                            label = { Text("${d}天") }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "注意：这是测试接口，可能随时失效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReceive(vipType, days) },
                colors = NekoDefaults.textButtonColors()
            ) {
                Text("领取")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = NekoDefaults.textButtonColors()
            ) {
                Text("取消")
            }
        }
    )
}
