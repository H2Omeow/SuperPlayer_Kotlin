import 'package:flutter/material.dart';

import '../download_controller.dart';

class DownloadsPage extends StatelessWidget {
  const DownloadsPage({
    super.key,
    required this.controller,
    required this.onImport,
    required this.onPlay,
  });
  final DownloadController controller;
  final Future<void> Function(DownloadTask task) onImport;
  final Future<void> Function(DownloadTask task) onPlay;

  Future<void> deleteFiles(BuildContext context, DownloadTask task) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除已下载的文件？'),
        content: Text(
          '将删除这项任务创建的音频${task.lyricsOwned ? '及歌词' : ''}，不会只移除记录。\n\n${task.path ?? ''}\n\n正在播放的文件可能被系统占用。',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('确认删除文件'),
          ),
        ],
      ),
    );
    if (context.mounted && confirm == true) {
      await controller.remove(task, deleteFiles: true);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('下载管理')),
    body: ListenableBuilder(
      listenable: controller,
      builder: (context, _) => Column(
        children: [
          const Padding(
            padding: EdgeInsets.all(16),
            child: Text('单任务下载以降低占用；支持暂停与断点续传。下载权限独立验证，不使用试听或播放链接代替下载。'),
          ),
          if (controller.error != null)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text(controller.error!),
            ),
          Expanded(
            child: controller.tasks.isEmpty
                ? const Center(child: Text('还没有下载任务，可从发现音乐添加。'))
                : ListView.builder(
                    itemCount: controller.tasks.length,
                    itemBuilder: (context, index) {
                      final task = controller.tasks[index];
                      final label = switch (task.phase) {
                        DownloadPhase.queued => '排队中',
                        DownloadPhase.downloading => '下载中',
                        DownloadPhase.paused => '已暂停',
                        DownloadPhase.failed => '失败',
                        DownloadPhase.completed => '已完成',
                      };
                      return Card(
                        margin: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 6,
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                task.song.title,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              Text(
                                '${task.song.artist} · ${task.song.provider.label} · ${task.quality} · $label',
                              ),
                              const SizedBox(height: 8),
                              if (task.phase == DownloadPhase.downloading)
                                LinearProgressIndicator(
                                  value: task.total == null || task.total == 0
                                      ? null
                                      : (task.received / task.total!).clamp(
                                          0,
                                          1,
                                        ),
                                ),
                              Text(
                                '${(task.received / 1048576).toStringAsFixed(1)} MB${task.total == null ? '' : ' / ${(task.total! / 1048576).toStringAsFixed(1)} MB'}',
                              ),
                              if (task.error != null)
                                Text(
                                  task.error!,
                                  style: TextStyle(
                                    color: Theme.of(context).colorScheme.error,
                                  ),
                                ),
                              if (task.phase == DownloadPhase.completed &&
                                  task.path != null)
                                SelectableText(task.path!),
                              Wrap(
                                spacing: 8,
                                children: [
                                  if ([
                                    DownloadPhase.downloading,
                                    DownloadPhase.queued,
                                  ].contains(task.phase))
                                    TextButton(
                                      onPressed: () => controller.pause(task),
                                      child: const Text('暂停'),
                                    ),
                                  if ([
                                    DownloadPhase.paused,
                                    DownloadPhase.failed,
                                  ].contains(task.phase))
                                    TextButton(
                                      onPressed: () => controller.resume(task),
                                      child: Text(
                                        task.phase == DownloadPhase.failed
                                            ? '重试'
                                            : '继续',
                                      ),
                                    ),
                                  if (task.phase == DownloadPhase.completed &&
                                      task.path != null)
                                    FilledButton.tonalIcon(
                                      onPressed: () => onPlay(task),
                                      icon: const Icon(Icons.play_arrow),
                                      label: const Text('播放下载'),
                                    ),
                                  if (task.phase == DownloadPhase.completed &&
                                      task.path != null)
                                    TextButton(
                                      onPressed: () => onImport(task),
                                      child: const Text('加入音乐库'),
                                    ),
                                  if (task.phase == DownloadPhase.completed &&
                                      task.path != null)
                                    TextButton(
                                      onPressed: controller.isActive(task)
                                          ? null
                                          : () => deleteFiles(context, task),
                                      child: const Text('删除下载文件'),
                                    ),
                                  TextButton(
                                    onPressed: () => controller.remove(task),
                                    child: Text(
                                      task.phase == DownloadPhase.downloading
                                          ? '取消下载'
                                          : '移除记录',
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    ),
  );
}
