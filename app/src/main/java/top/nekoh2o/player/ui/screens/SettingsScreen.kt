package top.nekoh2o.player.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.ui.PlayerViewModel
import top.nekoh2o.player.ui.theme.NekoDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val s = state.settings
    val context = LocalContext.current

    var showCacheManager by remember { mutableStateOf(false) }
    var showCookieManager by remember { mutableStateOf(false) }
    var showDownloadManager by remember { mutableStateOf(false) }

    if (showCacheManager) {
        CacheManagerScreen(vm) { showCacheManager = false }
        return
    }
    if (showCookieManager) {
        CookieManagerScreen(vm) { showCookieManager = false }
        return
    }
    if (showDownloadManager) {
        DownloadManagerScreen(vm) { showDownloadManager = false }
        return
    }

    // SAF 目录选择器：选中后持久化读写权限并保存 tree URI
    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            vm.setDownloadDir(uri.toString())
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
            // containerColor 继承 surface，NekoTheme 已按 controlAlpha 处理透明度
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                Switch(checked = s.globalBgEnabled, onCheckedChange = { vm.setGlobalBgEnabled(it) })
            }

            if (s.globalBgEnabled) {
                OutlinedButton(
                    onClick = { vm.refreshWallpaper() },
                    colors = NekoDefaults.outlinedButtonColors()
                ) { Text("刷新壁纸") }
                Column {
                    Text("全局遮罩明暗 (${(s.globalMaskAlpha * 100).toInt()}%)",
                        style = MaterialTheme.typography.titleSmall)
                    Slider(value = s.globalMaskAlpha, onValueChange = { vm.setGlobalMaskAlpha(it) },
                        valueRange = 0f..0.9f)
                }
                Column {
                    Text("全局模糊强度 (${s.globalBlurRadius}px)",
                        style = MaterialTheme.typography.titleSmall)
                    Slider(value = s.globalBlurRadius.toFloat(),
                        onValueChange = { vm.setGlobalBlurRadius(it.toInt()) },
                        valueRange = 0f..40f)
                }
            }

            HorizontalDivider()

            // ========== 全屏播放器背景 ==========
            Text("全屏播放器背景", style = MaterialTheme.typography.titleMedium)
            Row {
                FilterChip(selected = s.fpBgSource == BgSource.COVER,
                    onClick = { vm.setFpBgSource(BgSource.COVER) },
                    label = { Text("歌曲封面") })
                Spacer(Modifier.width(12.dp))
                FilterChip(selected = s.fpBgSource == BgSource.WALLPAPER,
                    onClick = { vm.setFpBgSource(BgSource.WALLPAPER) },
                    label = { Text("动态壁纸") })
            }
            Column {
                Text("播放器遮罩明暗 (${(s.fpMaskAlpha * 100).toInt()}%)",
                    style = MaterialTheme.typography.titleSmall)
                Slider(value = s.fpMaskAlpha, onValueChange = { vm.setFpMaskAlpha(it) },
                    valueRange = 0f..0.9f)
            }
            Column {
                Text("播放器模糊强度 (${s.fpBlurRadius}px)",
                    style = MaterialTheme.typography.titleSmall)
                Slider(value = s.fpBlurRadius.toFloat(),
                    onValueChange = { vm.setFpBlurRadius(it.toInt()) },
                    valueRange = 0f..40f)
                Text("模糊仅在 Android 12 及以上生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()

            // ========== 控件透明度（个性化联动）==========
            Text("个性化 - 控件透明度", style = MaterialTheme.typography.titleMedium)
            Column {
                Text("控件透明度 (${(s.controlAlpha * 100).toInt()}%)",
                    style = MaterialTheme.typography.titleSmall)
                Slider(value = s.controlAlpha, onValueChange = { vm.setControlAlpha(it) },
                    valueRange = 0f..1f)
                Text("与壁纸个性化联动，值越低控件越透明",
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
                    RadioButton(selected = state.quality == value, onClick = { vm.setQuality(value) })
                    Text(label)
                }
            }

            HorizontalDivider()

            // ========== 后台保活 ==========
            Text("后台保活", style = MaterialTheme.typography.titleMedium)
            Text(
                "以下措施有助于防止系统在后台将应用终止，从而保证音乐连续播放。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 电池优化豁免
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("电池优化豁免", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "允许应用忽略系统电池优化策略，防止系统在后台强制暂停播放。" +
                    "点击后将跳转系统设置，请手动选择「不优化」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { vm.requestBatteryExemption(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = NekoDefaults.outlinedButtonColors()
                ) { Text("申请电池优化豁免") }
            }
            // 厂商自启动白名单提示
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("厂商自启动白名单（手动）", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "小米 / 华为 / OPPO / vivo 等厂商 ROM 有独立的后台限制，需在系统「自启动管理」" +
                    "或「省电策略」中手动将本应用设置为允许自启动或无限制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            // ========== 悬浮歌词 ==========
            Text("悬浮歌词", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("双行显示", style = MaterialTheme.typography.bodyLarge)
                    Text("同时显示当前行与翻译/下一行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.floatingLyricDoubleRow,
                    onCheckedChange = { vm.setFloatingLyricDoubleRow(it) })
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示翻译", style = MaterialTheme.typography.bodyLarge)
                    Text("外语歌曲显示翻译行（需歌词含翻译）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.floatingLyricShowTranslation,
                    onCheckedChange = { vm.setFloatingLyricShowTranslation(it) })
            }
            Text("在全屏播放器中点击「悬浮词」开启悬浮窗，双行/翻译设置即时生效。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()

            // ========== 下载 ==========
            Text("下载", style = MaterialTheme.typography.titleMedium)
            val dirPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri: Uri? ->
                if (uri != null) {
                    // 持久化读写权限，避免重启后失效
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    vm.setDownloadDir(uri.toString())
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("下载目录", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (s.downloadDirUri.isBlank()) "默认：音乐/NekoPlayer"
                    else "自定义：${Uri.parse(s.downloadDirUri).lastPathSegment ?: s.downloadDirUri}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { dirPicker.launch(null) },
                        modifier = Modifier.weight(1f),
                        colors = NekoDefaults.outlinedButtonColors()
                    ) { Text("选择目录") }
                    if (s.downloadDirUri.isNotBlank()) {
                        OutlinedButton(
                            onClick = { vm.setDownloadDir("") },
                            colors = NekoDefaults.outlinedButtonColors()
                        ) { Text("恢复默认") }
                    }
                }
            }
            OutlinedButton(
                onClick = { showDownloadManager = true },
                modifier = Modifier.fillMaxWidth(),
                colors = NekoDefaults.outlinedButtonColors()
            ) { Text("下载管理") }

            HorizontalDivider()

            // ========== 歌曲缓存 ==========
            Text("歌曲缓存", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用歌曲缓存", style = MaterialTheme.typography.bodyLarge)
                    Text("缓存已播放歌曲，无网络时仍可播放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = s.cacheEnabled, onCheckedChange = { vm.setCacheEnabled(it) })
            }
            OutlinedButton(
                onClick = { vm.refreshCacheList(); showCacheManager = true },
                modifier = Modifier.fillMaxWidth(),
                colors = NekoDefaults.outlinedButtonColors()
            ) { Text("管理已缓存音乐") }

            HorizontalDivider()

            // ========== 网易云 Cookie ==========
            Text("网易云 Cookie", style = MaterialTheme.typography.titleMedium)
            Text(
                "填写网易云 Cookie 可获取更高音质和完整歌曲",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { vm.loadNcCookie(); showCookieManager = true },
                modifier = Modifier.fillMaxWidth(),
                colors = NekoDefaults.outlinedButtonColors()
            ) { Text("管理网易云 Cookie") }
        }
    }
}

// ==================== 缓存管理页 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val items = state.cachedItems
    val selected = state.selectedCacheKeys
    var confirmClearAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshCacheList() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("缓存管理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                // 全选
                IconButton(onClick = {
                    if (selected.size == items.size) {
                        // 全选 -> 全不选
                        items.forEach { vm.toggleCacheSelect(it.key) }
                    } else {
                        vm.selectAllCache()
                    }
                }) {
                    Icon(
                        if (selected.size == items.size && items.isNotEmpty())
                            Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = "全选"
                    )
                }
                // 删除选中
                if (selected.isNotEmpty()) {
                    IconButton(onClick = { vm.clearSelectedCache() }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "删除选中")
                    }
                }
                // 清空所有
                IconButton(onClick = { confirmClearAll = true }) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = "清空全部")
                }
            }
        )

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("暂无缓存") }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.key }) { item ->
                    val isSelected = selected.contains(item.key)
                    // 用纯 Row，不用 ListItem，避免 Material3 Surface 自带不透明背景
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleCacheSelect(item.key) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.song?.pc?.let { "$it?param=80y80" },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.song?.nm ?: "未知歌曲",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                item.song?.ar ?: item.key,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { vm.toggleCacheSelect(item.key) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("清空所有缓存") },
            text = { Text("确定要删除所有已缓存的音乐文件吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = { vm.clearAllCache(); confirmClearAll = false; onBack() },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("确定清空") }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClearAll = false },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }
}

// ==================== Cookie 管理页 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieManagerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    var cookieInput by remember { mutableStateOf(state.ncCookie) }

    LaunchedEffect(state.ncCookie) {
        if (cookieInput != state.ncCookie) cookieInput = state.ncCookie
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("网易云 Cookie") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            Text("Cookie 来源", style = MaterialTheme.typography.titleMedium)
            Text(
                "1. 打开网易云网页版，登录后\n" +
                "2. 按 F12 打开开发者工具 → Application → Cookies\n" +
                "3. 复制完整 Cookie 字符串粘贴至下方",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = cookieInput,
                onValueChange = { cookieInput = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("网易云 Cookie") },
                placeholder = { Text("MUSIC_U=...") },
                maxLines = 8,
                colors = NekoDefaults.textFieldColors()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveNcCookie(cookieInput); onBack() },
                    modifier = Modifier.weight(1f),
                    enabled = cookieInput.isNotBlank()
                ) { Text("保存") }
                OutlinedButton(
                    onClick = { vm.clearNcCookie(); cookieInput = ""; onBack() },
                    modifier = Modifier.weight(1f),
                    colors = NekoDefaults.outlinedButtonColors()
                ) { Text("清除") }
            }

            if (state.ncCookie.isNotEmpty()) {
                Text(
                    "当前 Cookie 已设置（${state.ncCookie.take(30)}…）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "当前未设置 Cookie，将使用游客模式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
