package top.nekoh2o.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFFEC4141) // 网易云红，对应 --primary

@Composable
fun NekoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark)
        darkColorScheme(primary = Primary)
    else
        lightColorScheme(primary = Primary)
    MaterialTheme(colorScheme = colors, content = content)
}
