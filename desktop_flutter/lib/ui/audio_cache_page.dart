import 'package:flutter/material.dart';
import 'package:path/path.dart' as paths;

import '../data/audio_cache.dart';
import '../domain/library.dart';
import '../player_controller.dart';

class AudioCachePage extends StatefulWidget {
  const AudioCachePage({super.key, required this.cache, required this.player});
  final AudioCache cache;
  final PlayerController player;
  @override
  State<AudioCachePage> createState() => _AudioCachePageState();
}

class _AudioCachePageState extends State<AudioCachePage> {
  bool busy = false;
  String? error;
  final selected = <String>{};
  Future<void> action(Future<void> Function() work) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await work();
    } catch (_) {
      if (mounted) setState(() => error = '缓存操作未完成，请检查文件权限和占用。');
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> clear({bool selection = false}) async {
    final files = Set<String>.of(selected);
    if (selection && files.isEmpty) return;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(selection ? '删除选中的 ${files.length} 首缓存？' : '清空歌曲缓存？'),
        content: const Text('只删除本应用缓存索引中的音频，不会删除你的本地音乐或下载。正在播放的缓存文件可能被系统占用。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(selection ? '删除' : '清空'),
          ),
        ],
      ),
    );
    if (mounted && confirm == true) {
      await action(
        () => selection ? widget.cache.remove(files) : widget.cache.clear(),
      );
      if (mounted) setState(selected.clear);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('离线歌曲缓存')),
    body: AnimatedBuilder(
      animation: widget.cache,
      builder: (context, _) => CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('缓存已播放音源'),
                    subtitle: const Text(
                      '默认关闭。开启后单任务保存完整音频供离线使用；额外消耗网络流量。缓存上限 512MB，单首上限 256MB。',
                    ),
                    value: widget.cache.enabled,
                    onChanged: busy || !widget.cache.readable
                        ? null
                        : (value) =>
                              action(() => widget.cache.setEnabled(value)),
                  ),
                  Text(
                    '${widget.cache.entries.length} 首 · ${(widget.cache.totalBytes / 1048576).toStringAsFixed(1)} MB${widget.cache.busy ? ' · 正在缓存一首歌曲' : ''}',
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '缓存优先离线播放，实际缓存音质会保留。不会保存临时播放 URL，不会自动上传；清理只作用于本应用缓存。',
                  ),
                  Wrap(
                    spacing: 8,
                    children: [
                      TextButton.icon(
                        onPressed: busy || !widget.cache.readable
                            ? null
                            : clear,
                        icon: const Icon(Icons.delete_outline),
                        label: const Text('清空缓存'),
                      ),
                      TextButton(
                        onPressed: busy
                            ? null
                            : () => setState(() {
                                if (selected.length ==
                                    widget.cache.entries.length) {
                                  selected.clear();
                                } else {
                                  selected.addAll(
                                    widget.cache.entries.map(
                                      (entry) => entry.file,
                                    ),
                                  );
                                }
                              }),
                        child: const Text('全选 / 取消全选'),
                      ),
                      TextButton(
                        onPressed:
                            busy || selected.isEmpty || !widget.cache.readable
                            ? null
                            : () => clear(selection: true),
                        child: Text('删除所选 (${selected.length})'),
                      ),
                    ],
                  ),
                  if (error ?? widget.cache.error case final String message)
                    Text(message),
                  if (busy || widget.cache.busy)
                    const LinearProgressIndicator(),
                ],
              ),
            ),
          ),
          SliverList.builder(
            itemCount: widget.cache.entries.length,
            itemBuilder: (context, index) {
              final item = widget.cache.entries[index];
              return ListTile(
                title: Text(item.song.title),
                subtitle: Text(
                  '${item.song.artist} · ${item.quality} · ${(item.size / 1048576).toStringAsFixed(1)} MB',
                ),
                leading: Checkbox(
                  value: selected.contains(item.file),
                  onChanged: busy
                      ? null
                      : (value) => setState(() {
                          if (value == true) {
                            selected.add(item.file);
                          } else {
                            selected.remove(item.file);
                          }
                        }),
                ),
                trailing: const Icon(Icons.play_arrow),
                onTap: () => widget.player.play([
                  Track(
                    path: paths.join(widget.cache.directory.path, item.file),
                    title: item.song.title,
                    artist: item.song.artist,
                  ),
                ], 0),
              );
            },
          ),
        ],
      ),
    ),
  );
}
