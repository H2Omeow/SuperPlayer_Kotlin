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
import androidx.compose.ui.draw.rotate
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
    val context = LocalContext.current

    // 子页面状态
    var showCacheManager by remember { mutableStateOf(false) }
    var showCookieManager by remember { mutableStateOf(false) }
    var showDownloadManager by remember { mutableStateOf(false) }
    var showCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    // 子管理页面
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

    // 分类详情页
    if (showCategory != null) {
        SettingsCategoryScreen(
            category = showCategory!!,
            vm = vm,
            onBack = { showCategory = null },
            onOpenCacheManager = { showCacheManager = true },
            onOpenCookieManager = { showCookieManager = true },
            onOpenDownloadManager = { showDownloadManager = true }
        )
        return
    }

    // 主设置页：类别列表
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("设置") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCategoryItem(
                title = "个性化设置",
                description = "背景、模糊、透明度等外观选项",
                onClick = { showCategory = SettingsCategory.PERSONALIZATION }
            )
            SettingsCategoryItem(
                title = "播放与歌词",
                description = "音质、悬浮歌词显示选项",
                onClick = { showCategory = SettingsCategory.PLAYBACK }
            )
            SettingsCategoryItem(
                title = "下载与存储",
                description = "下载目录、缓存管理",
                onClick = { showCategory = SettingsCategory.STORAGE }
            )
            SettingsCategoryItem(
                title = "账户信息",
                description = "Cookie 管理、云端同步",
                onClick = { showCategory = SettingsCategory.ACCOUNT }
            )
            SettingsCategoryItem(
                title = "后台与高级选项",
                description = "电池优化、自启动权限",
                onClick = { showCategory = SettingsCategory.ADVANCED }
            )
        }
    }
}

enum class SettingsCategory {
    PERSONALIZATION, PLAYBACK, STORAGE, ACCOUNT, ADVANCED
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.rotate(180f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCategoryScreen(
    category: SettingsCategory,
    vm: PlayerViewModel,
    onBack: () -> Unit,
    onOpenCacheManager: () -> Unit,
    onOpenCookieManager: () -> Unit,
    onOpenDownloadManager: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val s = state.settings
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(getCategoryTitle(category)) },
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
            when (category) {
                SettingsCategory.PERSONALIZATION -> PersonalizationSettings(vm, s)
                SettingsCategory.PLAYBACK -> PlaybackSettings(vm, state, s)
                SettingsCategory.STORAGE -> StorageSettings(vm, s, context, onOpenCacheManager, onOpenDownloadManager)
                SettingsCategory.ACCOUNT -> AccountSettings(vm, onOpenCookieManager)
                SettingsCategory.ADVANCED -> AdvancedSettings(vm, context)
            }
        }
    }
}

private fun getCategoryTitle(category: SettingsCategory): String = when (category) {
    SettingsCategory.PERSONALIZATION -> "个性化设置"
    SettingsCategory.PLAYBACK -> "播放与歌词"
    SettingsCategory.STORAGE -> "下载与存储"
    SettingsCategory.ACCOUNT -> "账户信息"
    SettingsCategory.ADVANCED -> "后台与高级选项"
}

@Composable
private fun PersonalizationSettings(vm: PlayerViewModel, s: top.nekoh2o.player.data.model.AppSettings) {
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

    Text("控件透明度", style = MaterialTheme.typography.titleMedium)
    Column {
        Text("控件透明度 (${(s.controlAlpha * 100).toInt()}%)",
            style = MaterialTheme.typography.titleSmall)
        Slider(value = s.controlAlpha, onValueChange = { vm.setControlAlpha(it) },
            valueRange = 0f..1f)
        Text("与壁纸个性化联动，值越低控件越透明",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaybackSettings(vm: PlayerViewModel, state: top.nekoh2o.player.ui.UiState, s: top.nekoh2o.player.data.model.AppSettings) {
    Text("音质选择", style = MaterialTheme.typography.titleMedium)
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
}

@Composable
private fun StorageSettings(
    vm: PlayerViewModel,
    s: top.nekoh2o.player.data.model.AppSettings,
    context: Context,
    onOpenCacheManager: () -> Unit,
    onOpenDownloadManager: () -> Unit
) {
    Text("下载目录", style = MaterialTheme.typography.titleMedium)
    val dirPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setDownloadDir(uri.toString())
        }
    }
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
    OutlinedButton(
        onClick = onOpenDownloadManager,
        modifier = Modifier.fillMaxWidth(),
        colors = NekoDefaults.outlinedButtonColors()
    ) { Text("下载管理") }

    HorizontalDivider()

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
        onClick = { vm.refreshCacheList(); onOpenCacheManager() },
        modifier = Modifier.fillMaxWidth(),
        colors = NekoDefaults.outlinedButtonColors()
    ) { Text("管理已缓存音乐") }
}

@Composable
private fun AccountSettings(vm: PlayerViewModel, onOpenCookieManager: () -> Unit) {
    Text(
        "账户登录与网易云 Cookie 用于云端同步和获取完整音质。登录入口位于「我的」页面。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "填写网易云 Cookie 可获取更高音质和完整歌曲",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = { vm.loadNcCookie(); onOpenCookieManager() },
        modifier = Modifier.fillMaxWidth(),
        colors = NekoDefaults.outlinedButtonColors()
    ) { Text("管理网易云 Cookie") }
}

@Composable
private fun AdvancedSettings(vm: PlayerViewModel, context: Context) {
    Text(
        "以下措施有助于防止系统在后台将应用终止，从而保证音乐连续播放。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Text("电池优化豁免", style = MaterialTheme.typography.titleMedium)
    Text(
        "允许应用忽略系统电池优化策略，防止系统在后台强制暂停播放。" +
                "部分机型仍可能在长时间熄屏后杀死后台播放，请根据情况同时在厂商 ROM 中设置应用自启动白名单。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(
        onClick = { vm.requestIgnoreBatteryOptimizations(context) },
        modifier = Modifier.fillMaxWidth(),
        colors = NekoDefaults.outlinedButtonColors()
    ) { Text("申请电池优化豁免权限") }

    Spacer(Modifier.height(8.dp))

    Text("厂商 ROM 自启动白名单", style = MaterialTheme.typography.titleMedium)
    Text(
        "国产 ROM（如 MIUI、ColorOS、HarmonyOS）往往需要在系统设置中手动将应用添加到自启动白名单，" +
                "才能保证后台播放稳定。请在系统设置 → 应用管理 → 本应用 → 自启动/后台限制 中允许。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ========== 缓存管理页面 ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheManagerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    val cacheList = state.cachedItems
    val selected = state.selectedCacheKeys

    var showClearConfirm by remember { mutableStateOf(false) }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部缓存") },
            text = { Text("确定要清除全部缓存音乐吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearAllCache()
                        showClearConfirm = false
                    },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    colors = NekoDefaults.textButtonColors()
                ) { Text("取消") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("缓存管理") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                Row {
                    IconButton(onClick = {
                        if (selected.size == cacheList.size && cacheList.isNotEmpty()) {
                            // 取消全选
                            cacheList.forEach { vm.toggleCacheSelect(it.key) }
                        } else {
                            // 全选
                            vm.selectAllCache()
                        }
                    }) {
                        Icon(
                            if (selected.size == cacheList.size && cacheList.isNotEmpty())
                                Icons.Default.CheckBox
                            else
                                Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (selected.size == cacheList.size) "取消全选" else "全选"
                        )
                    }
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSelectedCache() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "删除选中")
                        }
                    }
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "清空全部")
                    }
                }
            }
        )

        if (cacheList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无缓存", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(cacheList, key = { it.key }) { item ->
                    val isSelected = selected.contains(item.key)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleCacheSelect(item.key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.song?.pc?.let { "$it?param=100y100" },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.song?.nm ?: "未知歌曲",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.song?.ar ?: item.key,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { vm.toggleCacheSelect(item.key) }
                        )
                    }
                }
            }
        }
    }
}

// ========== Cookie 管理页面 ==========
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieManagerScreen(vm: PlayerViewModel, onBack: () -> Unit) {
    val state by vm.ui.collectAsState()
    var cookieText by remember { mutableStateOf(state.ncCookie) }
    LaunchedEffect(state.ncCookie) { cookieText = state.ncCookie }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Cookie 管理") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "填写网易云 Cookie 可获取更高音质和完整歌曲。" +
                        "在网页版网易云音乐登录后，打开浏览器开发者工具，复制请求头中的 Cookie 值粘贴到此处。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = cookieText,
                onValueChange = { cookieText = it },
                label = { Text("网易云 Cookie") },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                maxLines = 6
            )

            if (cookieText.length > 200) {
                Text(
                    "Cookie 内容已截断显示，实际长度：${cookieText.length} 字符",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.saveNcCookie(cookieText) },
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                OutlinedButton(
                    onClick = {
                        cookieText = ""
                        vm.saveNcCookie("")
                    },
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