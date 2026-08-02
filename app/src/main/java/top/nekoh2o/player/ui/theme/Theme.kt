package top.nekoh2o.player.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** 网易云红。只作强调色用：歌词渐变、进度条、开关、收藏心形等，不用于普通文字 */
private val Primary = Color(0xFFEC4141)

// App 以壁纸为背景，全局文字统一白色
private val White = Color(0xFFFFFFFF)
private val WhiteVariant = Color(0xE6FFFFFF)  // 次要文字
private val WhiteOutline = Color(0x80FFFFFF)  // 描边
private val WhiteDivider = Color(0x33FFFFFF)  // 分割线

/**
 * 全局主题。
 *
 * @param controlAlpha 控件不透明度，0 全透明、1 不透明。仅在 [translucent] 为 true 时生效。
 * @param translucent 是否开启壁纸背景。开启后所有 surface / container 系列颜色统一按
 *   [controlAlpha] 变半透明，这样 Card、ListItem、TabRow、TopAppBar、AlertDialog、
 *   ModalBottomSheet 等所有 Material3 控件都会跟着透明，不需要在每个调用点单独改颜色。
 */
@Composable
fun NekoTheme(
    controlAlpha: Float = 1f,
    translucent: Boolean = false,
    content: @Composable () -> Unit
) {
    // 始终用深色配色：全局白字需要深色底衬，浅色方案会出现白字白底不可读
    val base = darkColorScheme(
        primary = Primary,
        onPrimary = White,
        onPrimaryContainer = White,
        onSecondary = White,
        onSecondaryContainer = White,
        onTertiary = White,
        onTertiaryContainer = White,
        onBackground = White,
        onSurface = White,
        onSurfaceVariant = WhiteVariant,
        onError = White,
        onErrorContainer = White,
        outline = WhiteOutline,
        outlineVariant = WhiteDivider,
        // 关键：关掉 tonal elevation 染色。否则 Surface 会把 surfaceTint 按海拔叠回控件上，
        // 把刚调透明的颜色重新叠成不透明。
        surfaceTint = Color.Transparent,
        // Snackbar 用的反色，保持深底白字
        inverseSurface = Color(0xFF2A2A2A),
        inverseOnSurface = White
    )

    MaterialTheme(
        colorScheme = if (translucent) base.withControlAlpha(controlAlpha) else base
    ) {
        // 关键：把根级 LocalContentColor 强制成白色。
        // 壁纸开启时 Scaffold 的 containerColor 是 Color.Transparent，Material3 会用
        // contentColorFor(Transparent) 反推文字色，但 Transparent 不匹配任何配色，于是
        // 回退到根级 LocalContentColor（默认黑），导致直接放在 Column/Box 里的裸 Text
        // 变黑不可见，而 Surface/Card/ListItem 内的文字仍是白色——即“部分白字”现象。
        // 在这里统一兜底为白色，Surface 系列仍会各自按 onSurface 覆盖，不受影响。
        CompositionLocalProvider(LocalContentColor provides White, content = content)
    }
}

/**
 * 把 surface / container 系列统一改成半透明，让壁纸透出来。
 *
 * 不动 background：壁纸层由 MainScaffold 自己铺，且壁纸缺失时需要一个不透明兜底色，
 * 否则白字会落到系统窗口底色上看不见。
 */
private fun ColorScheme.withControlAlpha(alpha: Float): ColorScheme {
    val a = alpha.coerceIn(0f, 1f)
    fun Color.dim() = copy(alpha = a)
    return copy(
        surface = surface.dim(),
        surfaceVariant = surfaceVariant.dim(),
        surfaceBright = surfaceBright.dim(),
        surfaceDim = surfaceDim.dim(),
        surfaceContainer = surfaceContainer.dim(),
        surfaceContainerLow = surfaceContainerLow.dim(),
        surfaceContainerLowest = surfaceContainerLowest.dim(),
        surfaceContainerHigh = surfaceContainerHigh.dim(),
        surfaceContainerHighest = surfaceContainerHighest.dim()
    )
}

/**
 * 文字类控件的白色默认值。
 *
 * TextButton / OutlinedButton 的默认 contentColor 是 primary（红），这里统一改白，
 * 同时保留 primary 作为强调色，不影响歌词渐变和 Slider / Switch / 收藏心形。
 */
object NekoDefaults {

    @Composable
    fun textButtonColors(): ButtonColors =
        ButtonDefaults.textButtonColors(contentColor = White)

    @Composable
    fun outlinedButtonColors(): ButtonColors =
        ButtonDefaults.outlinedButtonColors(contentColor = White)

    /** 输入框：文字、标签、光标、描边全部白色 */
    @Composable
    fun textFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = White,
        unfocusedTextColor = White,
        focusedLabelColor = White,
        unfocusedLabelColor = WhiteVariant,
        focusedPlaceholderColor = WhiteVariant,
        unfocusedPlaceholderColor = WhiteVariant,
        cursorColor = White,
        focusedBorderColor = White,
        unfocusedBorderColor = WhiteOutline,
        focusedTrailingIconColor = White,
        unfocusedTrailingIconColor = WhiteVariant,
        focusedLeadingIconColor = White,
        unfocusedLeadingIconColor = WhiteVariant
    )
}
