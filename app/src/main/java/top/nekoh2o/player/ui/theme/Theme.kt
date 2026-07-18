package top.nekoh2o.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFFEC4141) // 网易云红，对应 --primary

// 因 App 以壁纸作为背景，统一使用白色文字，避免深浅背景下可读性不足
private val White = Color(0xFFFFFFFF)
private val WhiteVariant = Color(0xCCFFFFFF) // 次要文字，略降透明度

@Composable
fun NekoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark)
        darkColorScheme(
            primary = Primary,
            onPrimary = White,
            onBackground = White,
            onSurface = White,
            onSurfaceVariant = WhiteVariant,
            onSecondary = White,
            onTertiary = White
        )
    else
        lightColorScheme(
            primary = Primary,
            onPrimary = White,
            onBackground = White,
            onSurface = White,
            onSurfaceVariant = WhiteVariant,
            onSecondary = White,
            onTertiary = White
        )
    MaterialTheme(colorScheme = colors, content = content)
}
