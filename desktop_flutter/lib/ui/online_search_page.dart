import 'dart:async';

import 'package:flutter/material.dart';

import '../domain/online_music.dart';
import '../data/plugin_subscriptions.dart';
import '../search_controller.dart' as online;

class OnlineSearchPage extends StatefulWidget {
  const OnlineSearchPage({
    super.key,
    required this.controller,
    this.onPlay,
    this.onEnqueue,
    this.onFavorite,
    this.isFavorite,
    this.onPlaylist,
    this.onDownload,
    this.onComments,
    this.onBrowse,
    this.onAccountLibrary,
    this.onVideos,
    this.subscriptions,
    this.onSubscriptions,
  });

  final online.SearchController controller;
  final void Function(List<OnlineSong> songs, int index)? onPlay;
  final void Function(OnlineSong song)? onEnqueue;
  final void Function(OnlineSong song)? onFavorite;
  final bool Function(OnlineSong song)? isFavorite;
  final void Function(OnlineSong song)? onPlaylist;
  final void Function(OnlineSong song)? onDownload;
  final void Function(OnlineSong song)? onComments;
  final VoidCallback? onBrowse;
  final VoidCallback? onAccountLibrary;
  final VoidCallback? onVideos;
  final PluginSubscriptions? subscriptions;
  final VoidCallback? onSubscriptions;

  @override
  State<OnlineSearchPage> createState() => _OnlineSearchPageState();
}

class _OnlineSearchPageState extends State<OnlineSearchPage> {
  late final TextEditingController input;

  @override
  void initState() {
    super.initState();
    input = TextEditingController(text: widget.controller.keyword);
  }

  @override
  void dispose() {
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([widget.controller, widget.subscriptions]),
    builder: (context, child) {
      final controller = widget.controller;
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '发现音乐',
                  style: Theme.of(context).textTheme.headlineMedium,
                ),
              ),
              if (widget.onSubscriptions != null)
                IconButton(
                  tooltip: '音源订阅',
                  onPressed: widget.onSubscriptions,
                  icon: const Icon(Icons.extension_outlined),
                ),
              if (widget.onBrowse != null ||
                  widget.onAccountLibrary != null ||
                  widget.onVideos != null)
                PopupMenuButton<String>(
                  tooltip: '更多音乐内容',
                  icon: const Icon(Icons.explore_outlined),
                  onSelected: (value) {
                    switch (value) {
                      case 'browse':
                        widget.onBrowse?.call();
                      case 'library':
                        widget.onAccountLibrary?.call();
                      case 'videos':
                        widget.onVideos?.call();
                    }
                  },
                  itemBuilder: (_) => [
                    if (widget.onBrowse != null)
                      const PopupMenuItem(
                        value: 'browse',
                        child: Text('歌手、专辑与推荐歌单'),
                      ),
                    if (widget.onAccountLibrary != null)
                      const PopupMenuItem(
                        value: 'library',
                        child: Text('平台音乐库'),
                      ),
                    if (widget.onVideos != null)
                      const PopupMenuItem(
                        value: 'videos',
                        child: Text('音乐 MV'),
                      ),
                  ],
                ),
            ],
          ),
          const SizedBox(height: 8),
          const Text('选择曲库或订阅音源，搜索并播放音乐。'),
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (final provider in MusicProvider.values)
                  if (widget.subscriptions == null || provider.gateway == null)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(provider.label),
                        selected:
                            controller.pluginSource == null &&
                            controller.provider == provider,
                        onSelected: (_) => controller.selectProvider(provider),
                      ),
                    ),
                for (final source in widget.subscriptions?.sources ?? [])
                  if (source.capabilities.contains('search'))
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(
                          source.builtin
                              ? '内置 · ${source.name.replaceAll('-xm', '')}'
                              : source.name,
                        ),
                        selected: controller.pluginSource?.id == source.id,
                        onSelected: (_) => controller.selectPlugin(source),
                      ),
                    ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: input,
            maxLength: 200,
            textInputAction: TextInputAction.search,
            onSubmitted: (value) => unawaited(controller.search(value)),
            decoration: InputDecoration(
              hintText: '搜索歌曲、歌手',
              counterText: '',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: IconButton(
                tooltip: '搜索',
                onPressed: () => unawaited(controller.search(input.text)),
                icon: const Icon(Icons.arrow_forward),
              ),
            ),
          ),
          if (controller.loading) ...[
            const SizedBox(height: 12),
            const LinearProgressIndicator(),
          ],
          if (controller.error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                controller.error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          const SizedBox(height: 12),
          Expanded(
            child: controller.songs.isEmpty
                ? SingleChildScrollView(
                    padding: const EdgeInsets.symmetric(vertical: 24),
                    child: Center(
                      child: Text(
                        controller.loading
                            ? '正在搜索…'
                            : controller.error != null
                            ? '可以重新搜索，或切换音乐平台。'
                            : controller.keyword.isEmpty
                            ? '输入关键词，发现下一首喜欢的音乐。'
                            : '没有找到相关歌曲，试试其他关键词。',
                        textAlign: TextAlign.center,
                      ),
                    ),
                  )
                : ListView.builder(
                    itemCount: controller.songs.length + 1,
                    itemBuilder: (context, index) {
                      if (index == controller.songs.length) {
                        return Padding(
                          padding: const EdgeInsets.all(12),
                          child: Center(
                            child: controller.hasMore
                                ? OutlinedButton(
                                    onPressed: controller.loading
                                        ? null
                                        : controller.loadMore,
                                    child: Text(
                                      controller.loading ? '正在加载…' : '加载更多',
                                    ),
                                  )
                                : const Text('已显示全部结果'),
                          ),
                        );
                      }
                      final song = controller.songs[index];
                      return ListTile(
                        onTap: widget.onPlay == null
                            ? null
                            : () => widget.onPlay!(controller.songs, index),
                        trailing:
                            widget.onComments != null ||
                                widget.onFavorite != null ||
                                widget.onPlaylist != null ||
                                widget.onEnqueue != null
                            ? PopupMenuButton<String>(
                                tooltip: '歌曲操作',
                                itemBuilder: (_) => [
                                  if (widget.onComments != null)
                                    const PopupMenuItem(
                                      value: 'comments',
                                      child: Text('查看评论'),
                                    ),
                                  if (widget.onEnqueue != null)
                                    const PopupMenuItem(
                                      value: 'queue',
                                      child: Text('加入播放队列'),
                                    ),
                                  if (widget.onFavorite != null)
                                    PopupMenuItem(
                                      value: 'favorite',
                                      child: Text(
                                        widget.isFavorite?.call(song) == true
                                            ? '取消收藏'
                                            : '收藏',
                                      ),
                                    ),
                                  if (widget.onPlaylist != null)
                                    const PopupMenuItem(
                                      value: 'playlist',
                                      child: Text('加入本机歌单'),
                                    ),
                                  if (widget.onDownload != null)
                                    const PopupMenuItem(
                                      value: 'download',
                                      child: Text('下载歌曲'),
                                    ),
                                ],
                                onSelected: (value) {
                                  if (value == 'comments') {
                                    widget.onComments?.call(song);
                                  } else if (value == 'queue') {
                                    widget.onEnqueue?.call(song);
                                  } else if (value == 'favorite') {
                                    widget.onFavorite?.call(song);
                                  } else if (value == 'playlist') {
                                    widget.onPlaylist?.call(song);
                                  } else {
                                    widget.onDownload?.call(song);
                                  }
                                },
                              )
                            : widget.onDownload == null
                            ? widget.onPlay == null
                                  ? null
                                  : const Icon(Icons.play_circle_outline)
                            : IconButton(
                                tooltip: '下载歌曲',
                                onPressed: () => widget.onDownload!(song),
                                icon: const Icon(Icons.download_outlined),
                              ),
                        leading: const Icon(Icons.music_note_outlined),
                        title: Text(
                          song.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          [
                            song.artist,
                            song.album,
                            song.provider.label,
                          ].where((value) => value.isNotEmpty).join(' · '),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                      );
                    },
                  ),
          ),
        ],
      );
    },
  );
}
