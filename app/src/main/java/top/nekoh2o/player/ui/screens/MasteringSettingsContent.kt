package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.nekoh2o.player.data.model.AudioEffectSettings
import top.nekoh2o.player.data.model.MasteringPresets

@Composable
internal fun MasteringSettingsContent(
    settings: AudioEffectSettings,
    onPreset: (Int) -> Unit,
    onMix: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = MasteringPresets.available.firstOrNull { it.id == settings.masteringPresetId }
    Text("母带处理", style = MaterialTheme.typography.titleMedium)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected?.name ?: "关闭母带处理", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择母带预设")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("关闭母带处理") }, onClick = { onPreset(0); expanded = false })
            MasteringPresets.available.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = { onPreset(preset.id); expanded = false }
                )
            }
        }
    }
    if (selected != null) {
        EffectSlider("母带混合", "", settings.masteringMix, onMix)
    } else if (settings.masteringPresetId != 0) {
        Text("已保存的母带预设暂不可用", color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(4.dp))
}
