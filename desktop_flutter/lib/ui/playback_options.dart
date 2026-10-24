import 'dart:async';

import 'package:flutter/material.dart';

import '../player_controller.dart';
import 'playlist_picker.dart';

enum PlaybackView { split, record, lyrics }

class PlaybackOptionsButton extends StatelessWidget {
  const PlaybackOptionsButton({
    super.key,
    required this.controller,
    this.view = PlaybackView.split,
    this.onViewChanged,
  });
  final PlayerController controller;
  final PlaybackView view;
  final ValueChanged<PlaybackView>? onViewChanged;

  void select(BuildContext context, String action) {
    switch (action) {
      case 'view-split':
        onViewChanged?.call(PlaybackView.split);
      case 'view-record':
        onViewChanged?.call(PlaybackView.record);
      case 'view-lyrics':
        onViewChanged?.call(PlaybackView.lyrics);
      case 'playlist':
        final track = controller.current;
        if (track != null) {
          unawaited(showPlaylistPicker(context, controller, track));
        }
      case 'queue':
        unawaited(
          showModalBottomSheet<void>(
            context: context,
            isScrollControlled: true,
            builder: (_) => PlaybackQueueSheet(controller: controller),
          ),
        );
      case 'rate':
        unawaited(
          showDialog<void>(
            context: context,
            builder: (context) => AlertDialog(
              title: const Text('播放速度'),
              scrollable: true,
              content: Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final rate in [0.5, 0.75, 1.0, 1.25, 1.5, 2.0])
                    ChoiceChip(
                      label: Text('${rate}x'),
                      selected: controller.rate == rate,
                      onSelected: (_) {
                        controller.setRate(rate);
                        Navigator.pop(context);
                      },
                    ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('关闭'),
                ),
              ],
            ),
          ),
        );
      case 'timer':
        unawaited(
          showDialog<void>(
            context: context,
            builder: (context) => AlertDialog(
              title: const Text('定时暂停'),
              scrollable: true,
              content: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    controller.sleepDeadline == null
                        ? '当前未开启'
                        : '将在 ${controller.sleepDeadline!.hour.toString().padLeft(2, '0')}:${controller.sleepDeadline!.minute.toString().padLeft(2, '0')} 暂停',
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final minutes in [15, 30, 60, 90])
                        ActionChip(
                          label: Text('$minutes 分钟'),
                          onPressed: () {
                            controller.setSleepTimer(
                              Duration(minutes: minutes),
                            );
                            Navigator.pop(context);
                          },
                        ),
                      if (controller.sleepDeadline != null)
                        ActionChip(
                          label: const Text('取消定时'),
                          onPressed: () {
                            controller.setSleepTimer(null);
                            Navigator.pop(context);
                          },
                        ),
                    ],
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('关闭'),
                ),
              ],
            ),
          ),
        );
      case 'translation':
        controller.setLyricPreferences(
          translation: !controller.showTranslation,
        );
    }
  }

  @override
  Widget build(BuildContext context) => PopupMenuButton<String>(
    tooltip: '播放选项',
    icon: const Icon(Icons.more_vert),
    onSelected: (action) => select(context, action),
    itemBuilder: (_) => [
      if (onViewChanged != null) ...[
        for (final mode in PlaybackView.values)
          CheckedPopupMenuItem(
            value: 'view-${mode.name}',
            checked: view == mode,
            child: Text(switch (mode) {
              PlaybackView.split => '封面与歌词',
              PlaybackView.record => '唱片视图',
              PlaybackView.lyrics => '专注歌词',
            }),
          ),
        const PopupMenuDivider(),
      ],
      const PopupMenuItem(value: 'queue', child: Text('播放队列')),
      if (controller.current != null)
        const PopupMenuItem(value: 'playlist', child: Text('加入本机歌单')),
      PopupMenuItem(value: 'rate', child: Text('播放速度 · ${controller.rate}x')),
      PopupMenuItem(
        value: 'timer',
        child: Text(controller.sleepDeadline == null ? '定时暂停' : '定时暂停 · 已开启'),
      ),
      PopupMenuItem(
        value: 'translation',
        child: Text(controller.showTranslation ? '隐藏歌词翻译' : '显示歌词翻译'),
      ),
    ],
  );
}

class PlaybackQueueSheet extends StatelessWidget {
  const PlaybackQueueSheet({super.key, required this.controller});
  final PlayerController controller;

  @override
  Widget build(BuildContext context) => SafeArea(
    child: SizedBox(
      height: MediaQuery.sizeOf(context).height * .8,
      child: ListenableBuilder(
        listenable: Listenable.merge([controller, controller.engine]),
        builder: (context, _) => Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 8, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      '播放队列 · ${controller.queue.length}',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  IconButton(
                    tooltip: '关闭播放队列',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
            ),
            Expanded(
              child: controller.queue.isEmpty
                  ? const Center(child: Text('播放队列为空'))
                  : ReorderableListView.builder(
                      buildDefaultDragHandles: false,
                      itemCount: controller.queue.length,
                      onReorderItem: (from, to) =>
                          unawaited(controller.moveInQueue(from, to)),
                      itemBuilder: (context, index) {
                        final track = controller.queue[index];
                        return ListTile(
                          key: ValueKey('queue-$index-${track.path}'),
                          selected:
                              controller.current != null &&
                              controller.queueIndex == index,
                          leading: ReorderableDragStartListener(
                            index: index,
                            child: const Tooltip(
                              message: '拖动排序',
                              child: Icon(Icons.drag_handle),
                            ),
                          ),
                          title: Text(
                            track.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(
                            track.artist,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          onTap: () => unawaited(
                            controller.play(List.of(controller.queue), index),
                          ),
                          trailing: PopupMenuButton<String>(
                            tooltip: '队列操作 ${index + 1}',
                            onSelected: (action) {
                              switch (action) {
                                case 'up':
                                  unawaited(
                                    controller.moveInQueue(index, index - 1),
                                  );
                                case 'down':
                                  unawaited(
                                    controller.moveInQueue(index, index + 1),
                                  );
                                case 'remove':
                                  unawaited(controller.removeFromQueue(index));
                              }
                            },
                            itemBuilder: (_) => [
                              PopupMenuItem(
                                value: 'up',
                                enabled: index > 0,
                                child: const Text('上移'),
                              ),
                              PopupMenuItem(
                                value: 'down',
                                enabled: index + 1 < controller.queue.length,
                                child: const Text('下移'),
                              ),
                              const PopupMenuItem(
                                value: 'remove',
                                child: Text('从队列移除'),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    ),
  );
}
