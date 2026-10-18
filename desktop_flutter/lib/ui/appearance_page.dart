import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';

import '../appearance_controller.dart';
import '../domain/online_music.dart';

class AppearancePage extends StatelessWidget {
  const AppearancePage({super.key, required this.controller});
  final AppearanceController controller;
  Future<void> pickWallpaper(BuildContext context) async {
    try {
      final file = await openFile(
        acceptedTypeGroups: [
          const XTypeGroup(
            label: '图片',
            extensions: ['png', 'jpg', 'jpeg', 'webp'],
          ),
        ],
      );
      if (file != null) await controller.localWallpaper(file.path);
    } catch (failure) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              failure is MusicSearchException
                  ? failure.message
                  : '无法选择图片，请检查文件权限。',
            ),
          ),
        );
      }
    }
  }

  Widget slider(
    String label,
    double value,
    double min,
    double max,
    void Function(double) change,
  ) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('$label · ${value.toStringAsFixed(2)}'),
      Slider(
        value: value,
        min: min,
        max: max,
        divisions: 20,
        onChanged: change,
      ),
    ],
  );

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('外观与下载位置')),
    body: AnimatedBuilder(
      animation: controller,
      builder: (context, _) => Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 760),
          child: ListView(
            padding: const EdgeInsets.all(24),
            children: [
              const Text('主题'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [
                  for (final value in ThemeMode.values)
                    ChoiceChip(
                      label: Text(switch (value) {
                        ThemeMode.system => '跟随系统',
                        ThemeMode.light => '浅色',
                        ThemeMode.dark => '深色',
                      }),
                      selected: value == controller.theme,
                      onSelected: (_) => controller.update(themeMode: value),
                    ),
                ],
              ),
              const SizedBox(height: 20),
              slider(
                '界面文字缩放',
                controller.scale,
                .85,
                1.4,
                (value) => controller.update(textScale: value),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('壁纸背景'),
                value: controller.wallpaperEnabled,
                onChanged: controller.wallpaper == null
                    ? null
                    : (value) => controller.update(showWallpaper: value),
              ),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton(
                    onPressed: () => pickWallpaper(context),
                    child: const Text('选择本地图片'),
                  ),
                  OutlinedButton(
                    onPressed: controller.busy
                        ? null
                        : controller.randomWallpaper,
                    child: const Text('从本站随机加载横屏壁纸'),
                  ),
                  TextButton(
                    onPressed: () => controller.update(clearWallpaper: true),
                    child: const Text('恢复纯色背景'),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const Text('随机壁纸仅在点击时请求本站；本地图片路径不会上传。模糊范围限制为 20，避免过高 GPU 占用。'),
              const SizedBox(height: 16),
              slider(
                '背景遮罩',
                controller.mask,
                .35,
                .95,
                (value) => controller.update(darkness: value),
              ),
              slider(
                '背景模糊',
                controller.blur,
                0,
                20,
                (value) => controller.update(blurRadius: value),
              ),
              slider(
                '控件背景不透明度',
                controller.controls,
                .55,
                1,
                (value) => controller.update(controlAlpha: value),
              ),
              const SizedBox(height: 20),
              const Text('全屏播放器背景'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final mode in PlayerBackground.values)
                    ChoiceChip(
                      label: Text(switch (mode) {
                        PlayerBackground.solid => '纯色',
                        PlayerBackground.cover => '歌曲封面',
                        PlayerBackground.wallpaper => '已选择的壁纸',
                      }),
                      selected: controller.playerBackground == mode,
                      onSelected: (_) => controller.update(playerMode: mode),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              slider(
                '播放器遮罩',
                controller.playerMask,
                .35,
                .95,
                (value) => controller.update(playerDarkness: value),
              ),
              slider(
                '播放器背景模糊',
                controller.playerBlur,
                0,
                20,
                (value) => controller.update(playerBlurRadius: value),
              ),
              const Text('与全局背景独立；没有封面或壁纸时使用纯色，不自动请求随机壁纸。'),
              const SizedBox(height: 20),
              const Text('默认下载目录'),
              const SizedBox(height: 12),
              Text(controller.downloadDirectory ?? '每次下载时选择'),
              Wrap(
                spacing: 8,
                children: [
                  OutlinedButton(
                    onPressed: () async {
                      try {
                        final directory = await getDirectoryPath();
                        if (directory != null) {
                          controller.update(downloads: directory);
                        }
                      } catch (_) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('目录选择未完成。')),
                          );
                        }
                      }
                    },
                    child: const Text('选择下载目录'),
                  ),
                  TextButton(
                    onPressed: () => controller.update(resetDownloads: true),
                    child: const Text('恢复每次询问'),
                  ),
                ],
              ),
              if (controller.busy) const LinearProgressIndicator(),
              if (controller.error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 16),
                  child: Text(controller.error!),
                ),
            ],
          ),
        ),
      ),
    ),
  );
}
