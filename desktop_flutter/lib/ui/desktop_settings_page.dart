import 'package:flutter/material.dart';

import '../desktop_integration.dart';

class DesktopSettingsPage extends StatelessWidget {
  const DesktopSettingsPage({super.key, required this.integration});
  final DesktopIntegration integration;
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('系统控制与桌面歌词')),
    body: AnimatedBuilder(
      animation: integration,
      builder: (context, _) => Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('双行桌面歌词'),
                value: integration.player.doubleLyrics,
                onChanged: (value) {
                  integration.player.setLyricPreferences(twoLines: value);
                  integration.configureOverlay();
                },
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('显示歌词翻译'),
                subtitle: const Text('歌词含同时间翻译时优先显示翻译，否则显示下一句。'),
                value: integration.player.showTranslation,
                onChanged: (value) {
                  integration.player.setLyricPreferences(translation: value);
                  integration.configureOverlay();
                },
              ),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.speaker_group_outlined),
                title: const Text('系统媒体控制'),
                subtitle: Text(
                  integration.mediaAvailable
                      ? '已连接系统媒体会话；可使用系统面板和媒体键控制播放。'
                      : '当前环境的系统媒体会话不可用，应用内快捷键仍可使用。',
                ),
              ),
              const SizedBox(height: 20),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('关闭窗口时最小化到托盘'),
                subtitle: Text(
                  integration.trayAvailable
                      ? '本次启动有效；通过托盘菜单显示窗口、控制播放或退出。默认关闭。'
                      : '当前桌面没有可用托盘，关闭窗口会正常退出，不会隐藏到后台。',
                ),
                value: integration.closeToTray,
                onChanged: integration.trayAvailable
                    ? integration.configureTray
                    : null,
              ),
              const SizedBox(height: 20),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('显示独立桌面歌词'),
                subtitle: const Text('显示当前句和下一句；显示开关、锁定、字号和透明度会在重启后保留。'),
                value: integration.overlay,
                onChanged: integration.lyricsAvailable
                    ? (value) => integration.configureOverlay(enabled: value)
                    : null,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('锁定歌词并鼠标穿透'),
                subtitle: const Text('锁定后请回到这里解锁或关闭歌词。'),
                value: integration.locked,
                onChanged: integration.lyricsAvailable
                    ? (value) => integration.configureOverlay(lock: value)
                    : null,
              ),
              const SizedBox(height: 12),
              Text('字号 ${integration.fontSize.round()}'),
              Slider(
                min: 18,
                max: 64,
                divisions: 46,
                value: integration.fontSize,
                onChanged: integration.lyricsAvailable
                    ? (value) => integration.configureOverlay(size: value)
                    : null,
              ),
              Text('不透明度 ${(integration.opacity * 100).round()}%'),
              Slider(
                min: .25,
                max: 1,
                divisions: 15,
                value: integration.opacity,
                onChanged: integration.lyricsAvailable
                    ? (value) => integration.configureOverlay(alpha: value)
                    : null,
              ),
              Text(
                integration.positioningAvailable
                    ? '未锁定时拖动歌词窗口移动；双击切换播放 / 暂停。'
                    : '当前窗口系统不开放自由定位，置顶、位置与鼠标穿透效果受桌面合成器限制。',
              ),
              if (integration.error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 20),
                  child: Text(integration.error!),
                ),
            ],
          ),
        ),
      ),
    ),
  );
}
