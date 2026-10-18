import 'package:flutter/material.dart';

import '../domain/audio_effects.dart';
import '../playback/effects_playback_engine.dart';

class AudioEffectsPage extends StatelessWidget {
  const AudioEffectsPage({super.key, required this.engine});
  final EffectsPlaybackEngine engine;

  Widget slider(
    String label,
    double value,
    void Function(double) change, {
    double minimum = 0,
    double maximum = 100,
  }) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('$label · ${value.round()}${minimum < 0 ? ' dB' : '%'}'),
      Slider(
        value: value.clamp(minimum, maximum),
        min: minimum,
        max: maximum,
        divisions: (maximum - minimum).round(),
        onChanged: engine.changing ? null : change,
      ),
    ],
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('专业音效')),
    body: AnimatedBuilder(
      animation: engine,
      builder: (context, _) {
        final settings = engine.effects;
        return Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 820),
            child: ListView(
              padding: const EdgeInsets.all(24),
              children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('启用原版 C++ 专业音效'),
                  value: settings.enabled,
                  subtitle: const Text(
                    '使用原版 DSP、48kHz 双声道 PCM 和系统音频设备；需要 FFmpeg / ffprobe。切换播放模式会重新解析当前音源。',
                  ),
                  onChanged: engine.changing
                      ? null
                      : (value) =>
                            engine.configure(settings.copyWith(enabled: value)),
                ),
                const Text(
                  '音效参数实时生效；速度变化和跳转会重启低线程解码。母带仅开放原版实际实现的 15–34 号处理链，不用普通 EQ 冒充。重启应用后默认关闭专业模式。',
                ),
                if (engine.effectsError != null)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    child: Text(
                      engine.effectsError!,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  ),
                if (engine.changing) const LinearProgressIndicator(),
                const SizedBox(height: 24),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    OutlinedButton(
                      onPressed: engine.changing
                          ? null
                          : () => engine.configure(
                              AudioEffects(enabled: settings.enabled),
                            ),
                      child: const Text('重置全部音效'),
                    ),
                    for (final preset in AudioEffects.presets.entries)
                      ActionChip(
                        label: Text(preset.key),
                        onPressed: engine.changing
                            ? null
                            : () => engine.configure(
                                settings.copyWith(eq: preset.value),
                              ),
                      ),
                  ],
                ),
                const SizedBox(height: 24),
                const Text('10 段均衡器'),
                const SizedBox(height: 16),
                for (var index = 0; index < 10; index++)
                  slider(
                    '${AudioEffects.frequencies[index]} Hz',
                    settings.values[index],
                    (value) {
                      final bands = List<double>.of(settings.values.take(10));
                      bands[index] = value;
                      engine.configure(settings.copyWith(eq: bands));
                    },
                    minimum: -15,
                    maximum: 15,
                  ),
                slider(
                  '低音增强',
                  settings.bass,
                  (value) => engine.configure(settings.copyWith(bass: value)),
                ),
                slider(
                  '立体声宽度',
                  settings.width,
                  (value) => engine.configure(settings.copyWith(width: value)),
                ),
                slider(
                  '混响湿声',
                  settings.wet,
                  (value) => engine.configure(settings.copyWith(wet: value)),
                ),
                slider(
                  '混响空间',
                  settings.room,
                  (value) => engine.configure(settings.copyWith(room: value)),
                ),
                slider(
                  '混响阻尼',
                  settings.damping,
                  (value) =>
                      engine.configure(settings.copyWith(damping: value)),
                ),
                slider(
                  '响度增益',
                  settings.loudness,
                  (value) =>
                      engine.configure(settings.copyWith(loudness: value)),
                ),
                const SizedBox(height: 12),
                DropdownButtonFormField<int>(
                  key: ValueKey(settings.mastering),
                  initialValue: settings.values[16].toInt(),
                  isExpanded: true,
                  decoration: const InputDecoration(labelText: '母带处理链'),
                  items: [
                    const DropdownMenuItem(value: 0, child: Text('关闭')),
                    for (var index = 15; index <= 34; index++)
                      DropdownMenuItem(
                        value: index,
                        child: Text('原版母带 #$index'),
                      ),
                  ],
                  onChanged: engine.changing
                      ? null
                      : (value) {
                          if (value != null) {
                            engine.configure(
                              settings.copyWith(mastering: value),
                            );
                          }
                        },
                ),
                const SizedBox(height: 16),
                slider(
                  '母带混合',
                  settings.mix,
                  (value) => engine.configure(settings.copyWith(mix: value)),
                ),
              ],
            ),
          ),
        );
      },
    ),
  );
}
