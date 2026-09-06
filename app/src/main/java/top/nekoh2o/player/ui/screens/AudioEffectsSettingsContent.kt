package top.nekoh2o.player.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.nekoh2o.player.data.model.AudioEffectEngine
import top.nekoh2o.player.data.model.AudioEffectSettings
import top.nekoh2o.player.data.model.EQPresets
import top.nekoh2o.player.ui.PlayerViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioEffectsSettingsContent(vm: PlayerViewModel) {
    val state by vm.ui.collectAsState()
    val settings = state.settings.audioEffects

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 音效引擎选择
        Text("音效引擎", style = MaterialTheme.typography.titleMedium)
        Text(
            "选择音效处理方式，可随时切换",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        AudioEffectEngine.entries.forEach { engine ->
            Surface(
                onClick = { vm.setAudioEffectEngine(engine) },
                shape = MaterialTheme.shapes.medium,
                color = if (settings.engine == engine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        engine.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (settings.engine == engine) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    when (engine) {
                        AudioEffectEngine.NONE -> {}
                        AudioEffectEngine.SYSTEM -> Text(
                            "✓ 简单快速  ✓ 兼容性好  ✓ 低功耗\n✗ 不同设备效果差异大",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.engine == engine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                        AudioEffectEngine.NATIVE_CPP -> Text(
                            "✓ 专业音质  ✓ 全设备一致  ✓ 高扩展性\n✗ CPU 占用稍高",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.engine == engine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }

        if (settings.engine != AudioEffectEngine.NONE) {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // EQ 预设选择
            Text("均衡器预设", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EQPresets.presets.keys.forEach { presetName ->
                    FilterChip(
                        selected = settings.eqPresetName == presetName,
                        onClick = { vm.setEqPreset(presetName) },
                        label = { Text(presetName) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 10 段均衡器可视化
            Text("自定义均衡器", style = MaterialTheme.typography.titleMedium)
            Text(
                "拖动滑块调节各频段增益（-15dB ~ +15dB）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            EqualizerVisualizer(
                bands = settings.eqBands,
                frequencies = EQPresets.frequencies,
                onBandsChange = { vm.setEqBands(it) }
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))

            // 低音增强
            EffectSlider(
                label = "低音增强",
                description = "增强 80Hz 以下频段",
                value = settings.bassBoost,
                onValueChange = { vm.setBassBoost(it) }
            )

            // 3D 环绕
            EffectSlider(
                label = "3D 环绕",
                description = "立体声场拓宽效果",
                value = settings.virtualizer,
                onValueChange = { vm.setVirtualizer(it) }
            )

            // 空间混响
            EffectSlider(
                label = "空间混响",
                description = "模拟厅堂混响效果",
                value = settings.reverbWet,
                onValueChange = { vm.setReverbWet(it) }
            )

            // 响度增益
            EffectSlider(
                label = "响度增益",
                description = "整体音量提升",
                value = settings.loudnessGain,
                onValueChange = { vm.setLoudnessGain(it) }
            )

            Spacer(Modifier.height(16.dp))

            // 重置按钮
            OutlinedButton(
                onClick = {
                    vm.setEqBands(List(10) { 0f })
                    vm.setBassBoost(0)
                    vm.setVirtualizer(0)
                    vm.setReverbWet(0)
                    vm.setLoudnessGain(0)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("重置所有音效参数")
            }
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    description: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "$value%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 99
        )
    }
}

@Composable
private fun EqualizerVisualizer(
    bands: List<Float>,
    frequencies: List<Int>,
    onBandsChange: (List<Float>) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(220.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        bands.forEachIndexed { index, gain ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // 增益显示
                Text(
                    "${if (gain >= 0) "+" else ""}${gain.toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        gain > 0 -> MaterialTheme.colorScheme.primary
                        gain < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                // 垂直滑条
                Slider(
                    value = gain,
                    onValueChange = { newGain ->
                        val newBands = bands.toMutableList()
                        newBands[index] = newGain
                        onBandsChange(newBands)
                    },
                    valueRange = -15f..15f,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { rotationZ = 270f }
                        .width(180.dp)
                )

                // 频率标签
                Text(
                    if (frequencies[index] >= 1000) {
                        "${frequencies[index] / 1000}k"
                    } else {
                        "${frequencies[index]}"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
