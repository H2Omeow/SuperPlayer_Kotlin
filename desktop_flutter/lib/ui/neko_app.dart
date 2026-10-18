import 'dart:async';
import 'dart:io';
import 'dart:ui' show ImageFilter;

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart' hide RepeatMode;
import 'package:flutter/services.dart';

import '../domain/library.dart';
import '../domain/online_music.dart';
import '../data/native_music_catalog.dart';
import '../playback/playback_engine.dart';
import '../player_controller.dart';
import '../search_controller.dart' as online;
import 'neko_theme.dart';
import 'online_search_page.dart';
import 'accounts_panel.dart';
import 'downloads_page.dart';
import 'site_panel.dart';
import 'music_videos_page.dart';
import 'comments_page.dart';
import '../data/comments_repository.dart';
import '../site_controller.dart';
import '../data/account_music_repository.dart';
import 'account_music_page.dart';
import 'legacy_import_page.dart';
import '../desktop_integration.dart';
import 'desktop_settings_page.dart';
import '../playback/effects_playback_engine.dart';
import 'audio_effects_page.dart';
import '../data/catalog_browse_repository.dart';
import 'catalog_browse_page.dart';
import 'audio_cache_page.dart';
import '../appearance_controller.dart';
import 'appearance_page.dart';
import 'player_background.dart';

class NekoApp extends StatefulWidget {
  const NekoApp({super.key, required this.controller});

  final PlayerController controller;

  @override
  State<NekoApp> createState() => _NekoAppState();
}

class _NekoAppState extends State<NekoApp> {
  final appearance = AppearanceController();
  @override
  void initState() {
    super.initState();
    unawaited(appearance.initialize());
  }

  @override
  void dispose() {
    appearance.dispose();
    final source = widget.controller.onlineSource;
    if (source is NativeMusicCatalog) source.cache?.dispose();
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: appearance,
    builder: (context, _) => MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'NekoPlayer',
      theme: nekoTheme(
        brightness: Brightness.light,
        controlAlpha: appearance.controls,
        wallpaper: appearance.wallpaperEnabled && appearance.wallpaper != null,
      ),
      darkTheme: nekoTheme(
        controlAlpha: appearance.controls,
        wallpaper: appearance.wallpaperEnabled && appearance.wallpaper != null,
      ),
      themeMode: appearance.theme,
      builder: (context, child) => AppearanceScope(
        controller: appearance,
        child: MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(
              MediaQuery.textScalerOf(context).scale(1) * appearance.scale,
            ),
          ),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (appearance.wallpaperEnabled &&
                  appearance.wallpaper != null) ...[
                ClipRect(
                  child: ImageFiltered(
                    imageFilter: ImageFilter.blur(
                      sigmaX: appearance.blur,
                      sigmaY: appearance.blur,
                    ),
                    child: Image.file(
                      File(appearance.wallpaper!),
                      cacheWidth: 1920,
                      cacheHeight: 1080,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) =>
                          const ColoredBox(color: Color(0xFF111116)),
                    ),
                  ),
                ),
                ColoredBox(
                  color:
                      (Theme.of(context).brightness == Brightness.dark
                              ? const Color(0xFF111116)
                              : const Color(0xFFFCF7F9))
                          .withValues(alpha: appearance.mask),
                ),
              ],
              child ?? const SizedBox(),
            ],
          ),
        ),
      ),
      home: PlayerShell(controller: widget.controller),
    ),
  );
}

class PlayerShell extends StatefulWidget {
  const PlayerShell({super.key, required this.controller});

  final PlayerController controller;

  @override
  State<PlayerShell> createState() => _PlayerShellState();
}

class _PlayerShellState extends State<PlayerShell> {
  late final NativeMusicCatalog catalog;
  late final online.SearchController onlineSearch;
  late final SiteController site;
  late final DesktopIntegration integration;

  @override
  void initState() {
    super.initState();
    catalog = widget.controller.onlineSource is NativeMusicCatalog
        ? widget.controller.onlineSource as NativeMusicCatalog
        : NativeMusicCatalog();
    onlineSearch = online.SearchController(catalog);
    site = SiteController(
      accounts: catalog.accounts,
      player: widget.controller,
    );
    integration = DesktopIntegration(widget.controller);
    unawaited(integration.initialize());
  }

  int destination = 0;
  String query = '';
  String filter = '全部音乐';
  String? playlistId;
  bool importing = false;

  PlayerController get controller => widget.controller;

  void openComments(OnlineSong song) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) =>
            CommentsPage(repository: CommentsRepository(catalog), song: song),
      ),
    );
  }

  @override
  void dispose() {
    integration.dispose();
    onlineSearch.dispose();
    site.dispose();
    super.dispose();
  }

  static const destinations = [
    (label: '主页', icon: Icons.home_outlined, selected: Icons.home_rounded),
    (
      label: '音乐库',
      icon: Icons.library_music_outlined,
      selected: Icons.library_music_rounded,
    ),
    (
      label: '歌单',
      icon: Icons.queue_music_outlined,
      selected: Icons.queue_music_rounded,
    ),
    (label: '发现', icon: Icons.explore_outlined, selected: Icons.explore),
    (label: '设置', icon: Icons.tune_outlined, selected: Icons.tune_rounded),
  ];

  Future<void> importMusic() async {
    if (importing) return;
    setState(() => importing = true);
    try {
      final files = await openFiles(
        acceptedTypeGroups: [
          XTypeGroup(label: '音乐文件', extensions: PlayerController.extensions),
        ],
      );
      if (!mounted) return;
      await controller.importFiles(files.map((file) => file.path).toList());
      if (mounted && files.isNotEmpty) {
        setState(() {
          destination = 1;
          filter = '全部音乐';
        });
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('文件选择未完成，请重试。')));
      }
    } finally {
      if (mounted) setState(() => importing = false);
    }
  }

  Future<void> downloadSong(OnlineSong song) async {
    final downloads = controller.downloads;
    if (downloads == null) return;
    try {
      final qualities = song.provider.gateway != null
          ? ['128k', '320k', 'flac', 'hires']
          : song.provider == MusicProvider.kugou
          ? NativeMusicCatalog.kgLevels
          : NativeMusicCatalog.levels;
      final quality = await showDialog<String>(
        context: context,
        builder: (context) => SimpleDialog(
          title: const Text('选择下载音质'),
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 24, vertical: 8),
              child: Text('可下载音质由平台独立核验；不支持时会报错，不会用试听或低音质冒充。'),
            ),
            for (final quality in qualities)
              SimpleDialogOption(
                onPressed: () => Navigator.pop(context, quality),
                child: Text(quality),
              ),
          ],
        ),
      );
      if (!mounted || quality == null) return;
      final directory =
          AppearanceScope.maybeOf(context)?.downloadDirectory ??
          await getDirectoryPath(confirmButtonText: '下载到此文件夹');
      if (!mounted || directory == null) return;
      await downloads.enqueue(song, directory, quality);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(downloads.error ?? '已加入下载队列；可在设置中查看下载管理。')),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('无法创建下载任务，请检查目录权限。')));
      }
    }
  }

  Future<void> createPlaylist() async {
    final input = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新建歌单'),
        content: TextField(
          controller: input,
          autofocus: true,
          maxLength: 80,
          decoration: const InputDecoration(labelText: '歌单名称'),
          onSubmitted: (value) {
            if (value.trim().isNotEmpty) Navigator.pop(context, value);
          },
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () {
              if (input.text.trim().isNotEmpty) {
                Navigator.pop(context, input.text);
              }
            },
            child: const Text('创建'),
          ),
        ],
      ),
    );
    if (result != null) controller.createPlaylist(result);
    await Future<void>.delayed(const Duration(milliseconds: 300));
    input.dispose();
  }

  void openPlayer() {
    if (controller.current == null) return;
    unawaited(
      showDialog<void>(
        context: context,
        builder: (context) =>
            Dialog.fullscreen(child: FullPlayer(controller: controller)),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, child) => CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.keyO, control: true): () =>
            unawaited(importMusic()),
        const SingleActivator(LogicalKeyboardKey.space, control: true): () =>
            unawaited(controller.toggle()),
        const SingleActivator(
          LogicalKeyboardKey.arrowRight,
          control: true,
        ): () =>
            unawaited(controller.perform(controller.engine.next)),
        const SingleActivator(
          LogicalKeyboardKey.arrowLeft,
          control: true,
        ): () =>
            unawaited(controller.perform(controller.engine.previous)),
      },
      child: Focus(
        autofocus: true,
        child: LayoutBuilder(
          builder: (context, constraints) {
            final wide = constraints.maxWidth >= 840;
            final extended = constraints.maxWidth >= 1200;
            return Scaffold(
              body: SafeArea(
                child: Column(
                  children: [
                    Expanded(
                      child: Row(
                        children: [
                          if (wide)
                            NavigationRail(
                              extended: extended,
                              minExtendedWidth: 204,
                              labelType: extended
                                  ? null
                                  : NavigationRailLabelType.all,
                              selectedIndex: destination,
                              onDestinationSelected: (value) =>
                                  setState(() => destination = value),
                              leading: Padding(
                                padding: const EdgeInsets.symmetric(
                                  vertical: 24,
                                ),
                                child: Text(
                                  extended ? 'NekoPlayer' : 'NEKO',
                                  style: Theme.of(context).textTheme.titleMedium
                                      ?.copyWith(
                                        color: Theme.of(
                                          context,
                                        ).colorScheme.primary,
                                      ),
                                ),
                              ),
                              destinations: destinations
                                  .map(
                                    (entry) => NavigationRailDestination(
                                      icon: Icon(entry.icon),
                                      selectedIcon: Icon(entry.selected),
                                      label: Text(entry.label),
                                    ),
                                  )
                                  .toList(),
                            ),
                          Expanded(
                            child: Column(
                              children: [
                                if (controller.error != null)
                                  MaterialBanner(
                                    content: Text(controller.error!),
                                    actions: [
                                      TextButton(
                                        onPressed: controller.dismissError,
                                        child: const Text('知道了'),
                                      ),
                                    ],
                                  ),
                                Expanded(
                                  child: Padding(
                                    padding: EdgeInsets.all(wide ? 32 : 20),
                                    child: switch (destination) {
                                      0 => home(context),
                                      1 => library(context),
                                      2 => playlistPage(context),
                                      3 => OnlineSearchPage(
                                        controller: onlineSearch,
                                        onEnqueue: (song) => controller.enqueue(
                                          Track.fromOnline(song),
                                        ),
                                        onBrowse: () =>
                                            Navigator.of(context).push(
                                              MaterialPageRoute<void>(
                                                builder: (_) => CatalogBrowsePage(
                                                  repository:
                                                      CatalogBrowseRepository(
                                                        catalog,
                                                      ),
                                                  player: controller,
                                                  onComments: openComments,
                                                ),
                                              ),
                                            ),
                                        onAccountLibrary: () =>
                                            Navigator.of(context).push(
                                              MaterialPageRoute<void>(
                                                builder: (_) => AccountMusicPage(
                                                  repository:
                                                      AccountMusicRepository(
                                                        catalog,
                                                      ),
                                                  player: controller,
                                                  onComments: openComments,
                                                ),
                                              ),
                                            ),
                                        onVideos: () =>
                                            Navigator.of(context).push(
                                              MaterialPageRoute<void>(
                                                builder: (_) => MusicVideosPage(
                                                  catalog: catalog,
                                                  provider:
                                                      onlineSearch.provider,
                                                  volume: controller.volume,
                                                  pauseMusic: () =>
                                                      controller.perform(
                                                        controller.engine.pause,
                                                      ),
                                                ),
                                              ),
                                            ),
                                        onComments: (song) =>
                                            Navigator.of(context).push(
                                              MaterialPageRoute<void>(
                                                builder: (_) => CommentsPage(
                                                  repository:
                                                      CommentsRepository(
                                                        catalog,
                                                      ),
                                                  song: song,
                                                ),
                                              ),
                                            ),
                                        onDownload: controller.downloads == null
                                            ? null
                                            : (song) =>
                                                  unawaited(downloadSong(song)),
                                        onPlay: (songs, index) => unawaited(
                                          controller.play(
                                            songs
                                                .map(Track.fromOnline)
                                                .toList(),
                                            index,
                                          ),
                                        ),
                                      ),
                                      _ => settings(context),
                                    },
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                    PlayerBar(controller: controller, onOpenPlayer: openPlayer),
                  ],
                ),
              ),
              bottomNavigationBar: wide
                  ? null
                  : NavigationBar(
                      selectedIndex: destination,
                      onDestinationSelected: (value) =>
                          setState(() => destination = value),
                      destinations: destinations
                          .map(
                            (entry) => NavigationDestination(
                              icon: Icon(entry.icon),
                              selectedIcon: Icon(entry.selected),
                              label: entry.label,
                            ),
                          )
                          .toList(),
                    ),
            );
          },
        ),
      ),
    ),
  );

  Widget heading(
    BuildContext context,
    String title,
    String subtitle, {
    Widget? action,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 24),
    child: Wrap(
      alignment: WrapAlignment.spaceBetween,
      crossAxisAlignment: WrapCrossAlignment.center,
      runSpacing: 12,
      spacing: 24,
      children: [
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 6),
            Text(
              subtitle,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        ?action,
      ],
    ),
  );

  Widget importButton() => FilledButton.icon(
    onPressed: importing || controller.busy ? null : importMusic,
    icon: const Icon(Icons.add_rounded),
    label: Text(importing || controller.busy ? '正在导入…' : '导入音乐'),
  );

  Widget home(BuildContext context) => ListView(
    children: [
      Text(
        'NEKOPLAYER / 为喜欢留一点时间',
        style: Theme.of(context).textTheme.labelLarge?.copyWith(
          color: Theme.of(context).colorScheme.primary,
        ),
      ),
      const SizedBox(height: 12),
      heading(context, '音乐，刚刚好。', '从一首熟悉的歌开始。', action: importButton()),
      Card(
        color: Theme.of(context).colorScheme.surfaceContainerHigh,
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Row(
            children: [
              Artwork(size: 76, track: controller.current),
              const SizedBox(width: 20),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '继续收听',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      controller.current?.title ?? '为今天挑一首歌',
                      style: Theme.of(context).textTheme.titleLarge,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 6),
                    Text(
                      controller.current?.artist ?? '导入你的本地音乐，建立自己的音乐库。',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (controller.current != null)
                      TextButton.icon(
                        onPressed: openPlayer,
                        icon: const Icon(Icons.open_in_full),
                        label: const Text('打开播放器'),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 20),
      Wrap(
        spacing: 12,
        runSpacing: 12,
        children: [
          statCard(
            context,
            '本地音乐',
            controller.tracks.length,
            Icons.library_music_outlined,
            () => setState(() {
              destination = 1;
              filter = '全部音乐';
            }),
          ),
          statCard(
            context,
            '我的收藏',
            controller.favorites.length,
            Icons.favorite_border,
            () => setState(() {
              destination = 1;
              filter = '我的收藏';
            }),
          ),
          statCard(
            context,
            '我的歌单',
            controller.playlists.length,
            Icons.queue_music,
            () => setState(() => destination = 2),
          ),
        ],
      ),
      const SizedBox(height: 28),
      Text('最近听过', style: Theme.of(context).textTheme.titleLarge),
      const SizedBox(height: 12),
      if (controller.recent.isEmpty)
        const EmptyState(
          icon: Icons.headphones_outlined,
          title: '第一首歌，等你来选',
          description: '导入音乐后开始播放，收听记录就会出现在这里。',
        )
      else
        ...controller.recent
            .take(8)
            .toList()
            .asMap()
            .entries
            .map(
              (entry) => trackTile(
                context,
                entry.value,
                onPlay: () => unawaited(
                  controller.play(
                    controller.recent.take(8).toList(),
                    entry.key,
                  ),
                ),
              ),
            ),
    ],
  );

  Widget statCard(
    BuildContext context,
    String label,
    int count,
    IconData icon,
    VoidCallback onTap,
  ) => SizedBox(
    width: 176,
    child: Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(icon, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: 16),
              Text('$count', style: Theme.of(context).textTheme.headlineSmall),
              Text(
                label,
                style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );

  Widget library(BuildContext context) {
    final base = switch (filter) {
      '我的收藏' =>
        controller.tracks
            .where((track) => controller.favorites.contains(track.path))
            .toList(),
      '最近播放' => controller.recent,
      '播放队列' => controller.queue,
      _ => controller.tracks,
    };
    final needle = query.trim().toLowerCase();
    final results = base
        .where(
          (track) =>
              '${track.title} ${track.artist}'.toLowerCase().contains(needle),
        )
        .toList();
    final queueView = filter == '播放队列';
    final queueIndices = [
      for (var index = 0; index < base.length; index++)
        if ('${base[index].title} ${base[index].artist}'.toLowerCase().contains(
          needle,
        ))
          index,
    ];
    Widget queueTile(int index) => trackTile(
      context,
      results[index],
      queueIndex: queueIndices[index],
      onPlay: () =>
          unawaited(controller.play(controller.queue, queueIndices[index])),
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        heading(
          context,
          '你的音乐库',
          '${controller.tracks.length} 首音乐 · 文件保留在原位置',
          action: importButton(),
        ),
        TextField(
          onChanged: (value) => setState(() => query = value),
          decoration: const InputDecoration(
            prefixIcon: Icon(Icons.search),
            hintText: '搜索本地歌曲、歌手',
          ),
        ),
        const SizedBox(height: 12),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              for (final label in ['全部音乐', '我的收藏', '最近播放', '播放队列'])
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    selected: filter == label,
                    onSelected: (_) => setState(() => filter = label),
                    label: Text(label),
                  ),
                ),
              if (queueView)
                TextButton.icon(
                  onPressed: controller.queue.isEmpty
                      ? null
                      : () async {
                          final confirmed = await showDialog<bool>(
                            context: context,
                            builder: (context) => AlertDialog(
                              title: const Text('清空播放队列？'),
                              content: const Text('将停止当前播放，不会删除音乐文件、收藏或歌单。'),
                              actions: [
                                TextButton(
                                  onPressed: () =>
                                      Navigator.pop(context, false),
                                  child: const Text('取消'),
                                ),
                                FilledButton(
                                  onPressed: () => Navigator.pop(context, true),
                                  child: const Text('清空并停止'),
                                ),
                              ],
                            ),
                          );
                          if (mounted && confirmed == true) {
                            await controller.clearQueue();
                          }
                        },
                  icon: const Icon(Icons.playlist_remove),
                  label: const Text('清空队列'),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        Expanded(
          child: results.isEmpty
              ? const EmptyState(
                  icon: Icons.music_note_outlined,
                  title: '这里还很安静',
                  description: '导入音乐，或调整筛选和搜索关键词。',
                )
              : queueView && needle.isEmpty
              ? ReorderableListView.builder(
                  buildDefaultDragHandles: false,
                  itemCount: results.length,
                  onReorderItem: (from, to) =>
                      unawaited(controller.moveInQueue(from, to)),
                  itemBuilder: (context, index) => Row(
                    key: ValueKey('queue-$index-${results[index].path}'),
                    children: [
                      Expanded(child: queueTile(index)),
                      ReorderableDragStartListener(
                        index: index,
                        child: const Tooltip(
                          message: '拖动调整播放顺序',
                          child: Padding(
                            padding: EdgeInsets.all(12),
                            child: Icon(Icons.drag_handle),
                          ),
                        ),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  itemCount: results.length,
                  itemBuilder: (context, index) => queueView
                      ? queueTile(index)
                      : trackTile(
                          context,
                          results[index],
                          onPlay: () =>
                              unawaited(controller.play(results, index)),
                        ),
                ),
        ),
      ],
    );
  }

  Widget trackTile(
    BuildContext context,
    Track track, {
    required VoidCallback onPlay,
    String? inPlaylist,
    int? queueIndex,
  }) => ListTile(
    contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    selected: controller.current?.path == track.path,
    selectedTileColor: Theme.of(
      context,
    ).colorScheme.primaryContainer.withValues(alpha: 0.35),
    leading: Artwork(size: 44, track: track),
    title: Text(track.title, maxLines: 1, overflow: TextOverflow.ellipsis),
    subtitle: Text(track.artist, maxLines: 1, overflow: TextOverflow.ellipsis),
    onTap: onPlay,
    trailing: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton(
          tooltip: controller.favorites.contains(track.path) ? '取消收藏' : '收藏',
          onPressed: () => controller.toggleFavorite(track),
          icon: Icon(
            controller.favorites.contains(track.path)
                ? Icons.favorite
                : Icons.favorite_border,
            color: controller.favorites.contains(track.path)
                ? Theme.of(context).colorScheme.primary
                : null,
          ),
        ),
        PopupMenuButton<String>(
          tooltip: '歌曲操作',
          onSelected: (value) {
            if (value == 'queue-add') {
              unawaited(controller.enqueue(track));
            } else if (value == 'queue-remove' && queueIndex != null) {
              unawaited(controller.removeFromQueue(queueIndex));
            } else if (value == 'queue-up' && queueIndex != null) {
              unawaited(controller.moveInQueue(queueIndex, queueIndex - 1));
            } else if (value == 'queue-down' && queueIndex != null) {
              unawaited(controller.moveInQueue(queueIndex, queueIndex + 1));
            } else if (value == 'remove' && inPlaylist != null) {
              controller.removeFromPlaylist(inPlaylist, track);
            } else {
              controller.addToPlaylist(value, track);
            }
          },
          itemBuilder: (context) => [
            const PopupMenuItem(value: 'queue-add', child: Text('加入播放队列')),
            if (queueIndex != null) ...[
              const PopupMenuItem(value: 'queue-remove', child: Text('从队列移除')),
              PopupMenuItem(
                value: 'queue-up',
                enabled: queueIndex > 0,
                child: const Text('在队列上移'),
              ),
              PopupMenuItem(
                value: 'queue-down',
                enabled: queueIndex < controller.queue.length - 1,
                child: const Text('在队列下移'),
              ),
            ],
            if (inPlaylist != null)
              const PopupMenuItem(value: 'remove', child: Text('从歌单移除')),
            for (final playlist in controller.playlists)
              PopupMenuItem(
                value: playlist.id,
                child: Text('加入 ${playlist.name}'),
              ),
            if (controller.playlists.isEmpty)
              const PopupMenuItem(enabled: false, child: Text('请先在歌单页创建歌单')),
          ],
        ),
      ],
    ),
  );

  Widget playlistPage(BuildContext context) {
    final selected = controller.playlists
        .where((playlist) => playlist.id == playlistId)
        .firstOrNull;
    final songs = selected == null
        ? <Track>[]
        : controller.playlistTracks(selected);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        heading(
          context,
          selected?.name ?? '我的歌单',
          selected == null ? '把喜欢的音乐，整理成自己的故事。' : '${songs.length} 首 · 本地歌单',
          action: selected == null
              ? FilledButton.icon(
                  onPressed: createPlaylist,
                  icon: const Icon(Icons.add),
                  label: const Text('新建歌单'),
                )
              : TextButton.icon(
                  onPressed: () => setState(() => playlistId = null),
                  icon: const Icon(Icons.arrow_back),
                  label: const Text('全部歌单'),
                ),
        ),
        Expanded(
          child: selected == null
              ? controller.playlists.isEmpty
                    ? const EmptyState(
                        icon: Icons.queue_music,
                        title: '收藏一段属于你的旋律',
                        description: '新建歌单后，在歌曲右侧的菜单中添加音乐。',
                      )
                    : ListView.separated(
                        itemCount: controller.playlists.length,
                        separatorBuilder: (_, index) =>
                            const SizedBox(height: 8),
                        itemBuilder: (context, index) {
                          final playlist = controller.playlists[index];
                          return Card(
                            child: ListTile(
                              contentPadding: const EdgeInsets.all(12),
                              leading: const Artwork(size: 52),
                              title: Text(playlist.name),
                              subtitle: Text('${playlist.paths.length} 首音乐'),
                              onTap: () =>
                                  setState(() => playlistId = playlist.id),
                              trailing: IconButton(
                                tooltip: '删除歌单',
                                icon: const Icon(Icons.delete_outline),
                                onPressed: () async {
                                  final confirmed = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: const Text('删除歌单？'),
                                      content: Text(
                                        '删除“${playlist.name}”不会删除原始音乐文件。',
                                      ),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: const Text('取消'),
                                        ),
                                        FilledButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: const Text('删除'),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (confirmed == true) {
                                    controller.deletePlaylist(playlist.id);
                                  }
                                },
                              ),
                            ),
                          );
                        },
                      )
              : songs.isEmpty
              ? const EmptyState(
                  icon: Icons.playlist_add,
                  title: '歌单还没有歌曲',
                  description: '前往音乐库，通过歌曲菜单加入音乐。',
                )
              : ListView.builder(
                  itemCount: songs.length,
                  itemBuilder: (context, index) => trackTile(
                    context,
                    songs[index],
                    inPlaylist: selected.id,
                    onPlay: () => unawaited(controller.play(songs, index)),
                  ),
                ),
        ),
      ],
    );
  }

  Widget settings(BuildContext context) => ListView(
    children: [
      heading(context, '让音乐更合心意', '播放与应用设置'),
      AccountsPanel(catalog: catalog),
      const SizedBox(height: 16),
      if (AppearanceScope.maybeOf(context)
          case final AppearanceController appearance) ...[
        Card(
          child: ListTile(
            leading: const Icon(Icons.palette_outlined),
            title: const Text('外观与下载位置'),
            subtitle: const Text('深浅主题、壁纸、文字缩放与默认下载目录'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => AppearancePage(controller: appearance),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
      if (catalog.cache != null) ...[
        Card(
          child: ListTile(
            leading: const Icon(Icons.offline_pin_outlined),
            title: const Text('离线歌曲缓存'),
            subtitle: const Text('可选缓存、离线播放与容量管理'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    AudioCachePage(cache: catalog.cache!, player: controller),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
      if (controller.engine is EffectsPlaybackEngine) ...[
        Card(
          child: ListTile(
            leading: const Icon(Icons.equalizer),
            title: const Text('专业音效'),
            subtitle: const Text('原版 C++ 引擎、10 段均衡、混响与母带处理'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => AudioEffectsPage(
                  engine: controller.engine as EffectsPlaybackEngine,
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
      Card(
        child: ListTile(
          leading: const Icon(Icons.subtitles_outlined),
          title: const Text('系统控制与桌面歌词'),
          subtitle: const Text('媒体会话、独立歌词窗口与鼠标穿透'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => DesktopSettingsPage(integration: integration),
            ),
          ),
        ),
      ),
      const SizedBox(height: 16),
      Card(
        child: ListTile(
          leading: const Icon(Icons.move_to_inbox_outlined),
          title: const Text('导入旧版备份'),
          subtitle: const Text('主动选择文件、预览后合并；账号导入默认关闭'),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => LegacyImportPage(
                player: controller,
                accounts: catalog.accounts,
              ),
            ),
          ),
        ),
      ),
      const SizedBox(height: 16),
      SitePanel(controller: site, gateway: catalog.gateway),
      const SizedBox(height: 16),
      Card(
        child: ListTile(
          leading: const Icon(Icons.comment_outlined),
          title: const Text('当前歌曲评论'),
          subtitle: const Text('本站、网易云与酷狗；也可从发现页歌曲菜单打开'),
          enabled: controller.current?.online != null,
          onTap: controller.current?.online == null
              ? null
              : () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => CommentsPage(
                      repository: CommentsRepository(catalog),
                      song: controller.current!.online!,
                    ),
                  ),
                ),
        ),
      ),
      const SizedBox(height: 16),
      if (controller.downloads != null) ...[
        Card(
          child: ListTile(
            leading: const Icon(Icons.download_outlined),
            title: const Text('下载管理'),
            subtitle: const Text('暂停、续传与本地导入'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DownloadsPage(
                  controller: controller.downloads!,
                  onImport: (path) => controller.importFiles([path]),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
      ],
      Card(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('播放设置', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 16),
              const Text('首选在线音质（下一首生效；受账号权益限制）'),
              Wrap(
                spacing: 8,
                children: [
                  for (final quality in [
                    ('standard', '标准'),
                    ('exhigh', '高品质'),
                    ('lossless', '无损'),
                    ('hires', 'Hi-Res'),
                    ('jymaster', '母带'),
                  ])
                    ChoiceChip(
                      label: Text(quality.$2),
                      selected: controller.preferredQuality == quality.$1,
                      onSelected: (_) =>
                          controller.setPreferredQuality(quality.$1),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              const Text('播放速度'),
              Wrap(
                spacing: 8,
                children: [
                  for (final value in [0.5, 0.75, 1.0, 1.25, 1.5, 2.0])
                    ChoiceChip(
                      label: Text('${value}x'),
                      selected: controller.rate == value,
                      onSelected: (_) => controller.setRate(value),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                controller.sleepDeadline == null
                    ? '定时暂停 · 未开启'
                    : '定时暂停 · ${controller.sleepDeadline!.hour.toString().padLeft(2, '0')}:${controller.sleepDeadline!.minute.toString().padLeft(2, '0')}',
              ),
              Wrap(
                spacing: 8,
                children: [
                  for (final minutes in [15, 30, 60, 90])
                    ActionChip(
                      label: Text('$minutes 分钟'),
                      onPressed: () =>
                          controller.setSleepTimer(Duration(minutes: minutes)),
                    ),
                  if (controller.sleepDeadline != null)
                    ActionChip(
                      label: const Text('取消定时'),
                      onPressed: () => controller.setSleepTimer(null),
                    ),
                ],
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 16),
      const Card(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('键盘快捷键'),
              SizedBox(height: 12),
              Text(
                'Ctrl + O    导入音乐\nCtrl + 空格    播放 / 暂停\nCtrl + ← / →    上一首 / 下一首\nEsc    关闭完整播放器',
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: 16),
      const Card(
        child: Padding(
          padding: EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Flutter 桌面迁移预览'),
              SizedBox(height: 12),
              Text(
                '当前提供本地播放、音乐库、收藏、歌单、历史、LRC 歌词和播放设置。\n'
                '已接入搜索、账号会话、在线音源解析、下载与手动云同步；实网播放仍受平台账号权益和设备验证限制。\n'
                '发现页菜单提供分类、平台音乐库和 MV；另有评论、备份导入、缓存、原版 C++ DSP 与桌面歌词。各平台实机验收仍在进行。\n'
                '不会读取或覆盖原版的账号与音乐库。',
              ),
            ],
          ),
        ),
      ),
    ],
  );
}

class Artwork extends StatelessWidget {
  const Artwork({super.key, required this.size, this.track});

  final double size;
  final Track? track;

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(
      borderRadius: BorderRadius.circular(size > 60 ? 20 : 12),
      gradient: LinearGradient(
        colors: [
          Theme.of(context).colorScheme.primaryContainer,
          Theme.of(context).colorScheme.secondaryContainer,
        ],
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
      ),
    ),
    child: _image(context),
  );

  Widget _image(BuildContext context) {
    final uri = Uri.tryParse(track?.online?.artwork ?? '');
    final fallback = Icon(
      Icons.music_note_rounded,
      size: size * 0.42,
      color: Theme.of(context).colorScheme.primary,
    );
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty ||
        uri.userInfo.isNotEmpty) {
      return fallback;
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(size > 60 ? 20 : 12),
      child: Image.network(
        uri.toString(),
        cacheWidth: size > 100 ? 400 : 128,
        cacheHeight: size > 100 ? 400 : 128,
        width: size,
        height: size,
        fit: BoxFit.cover,
        errorBuilder: (_, _, _) => fallback,
      ),
    );
  }
}

class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
  });
  final IconData icon;
  final String title;
  final String description;

  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 32, horizontal: 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 16),
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              description,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    ),
  );
}

String clock(Duration duration) =>
    '${duration.inMinutes.toString().padLeft(2, '0')}:${duration.inSeconds.remainder(60).toString().padLeft(2, '0')}';

class PlayerBar extends AnimatedWidget {
  PlayerBar({super.key, required this.controller, required this.onOpenPlayer})
    : super(listenable: controller.engine);

  final PlayerController controller;
  final VoidCallback onOpenPlayer;

  @override
  Widget build(BuildContext context) => Material(
    color: Theme.of(context).colorScheme.surfaceContainerLow,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final wide = constraints.maxWidth >= 800;
          final track = controller.current;
          final title = Expanded(
            child: InkWell(
              onTap: track == null ? null : onOpenPlayer,
              borderRadius: BorderRadius.circular(12),
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: Row(
                  children: [
                    Artwork(size: 44, track: track),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            track?.title ?? 'NekoPlayer',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          Text(
                            track?.artist ?? '选择一首音乐开始播放',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(
                                  color: Theme.of(
                                    context,
                                  ).colorScheme.onSurfaceVariant,
                                ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (!wide && track != null)
                SeekBar(controller: controller, showClock: false),
              Row(
                children: [
                  title,
                  if (wide)
                    Expanded(
                      flex: 2,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          PlaybackControls(controller: controller),
                          SeekBar(controller: controller),
                        ],
                      ),
                    )
                  else
                    PlaybackControls(controller: controller, compact: true),
                  if (wide)
                    SizedBox(
                      width: 176,
                      child: Row(
                        children: [
                          const Icon(Icons.volume_up_outlined, size: 20),
                          Expanded(
                            child: Slider(
                              value: controller.volume,
                              max: 100,
                              label: '${controller.volume.round()}%',
                              onChanged: controller.setVolume,
                            ),
                          ),
                        ],
                      ),
                    ),
                  if (wide)
                    IconButton(
                      tooltip: '打开播放器',
                      onPressed: track == null ? null : onOpenPlayer,
                      icon: const Icon(Icons.open_in_full),
                    ),
                ],
              ),
            ],
          );
        },
      ),
    ),
  );
}

class PlaybackControls extends StatelessWidget {
  const PlaybackControls({
    super.key,
    required this.controller,
    this.compact = false,
  });

  final PlayerController controller;
  final bool compact;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      if (!compact)
        IconButton(
          tooltip: switch (controller.repeat) {
            RepeatMode.sequential => '顺序播放',
            RepeatMode.loop => '列表循环',
            RepeatMode.single => '单曲循环',
            RepeatMode.shuffle => '随机播放',
          },
          onPressed: controller.cycleRepeat,
          icon: Icon(switch (controller.repeat) {
            RepeatMode.single => Icons.repeat_one,
            RepeatMode.shuffle => Icons.shuffle,
            RepeatMode.sequential => Icons.arrow_right_alt,
            RepeatMode.loop => Icons.repeat,
          }),
        ),
      if (!compact)
        IconButton(
          tooltip: '上一首',
          onPressed: controller.current == null
              ? null
              : () => controller.perform(controller.engine.previous),
          icon: const Icon(Icons.skip_previous_rounded),
        ),
      IconButton.filled(
        tooltip: controller.engine.playing ? '暂停' : '播放',
        onPressed: controller.tracks.isEmpty ? null : controller.toggle,
        icon: Icon(
          controller.engine.playing
              ? Icons.pause_rounded
              : Icons.play_arrow_rounded,
        ),
      ),
      IconButton(
        tooltip: '下一首',
        onPressed: controller.current == null
            ? null
            : () => controller.perform(controller.engine.next),
        icon: const Icon(Icons.skip_next_rounded),
      ),
    ],
  );
}

class SeekBar extends StatefulWidget {
  const SeekBar({super.key, required this.controller, this.showClock = true});
  final PlayerController controller;
  final bool showClock;

  @override
  State<SeekBar> createState() => _SeekBarState();
}

class _SeekBarState extends State<SeekBar> {
  double? dragging;

  @override
  Widget build(BuildContext context) {
    final engine = widget.controller.engine;
    final maximum = engine.duration.inMilliseconds.toDouble().clamp(
      1.0,
      double.maxFinite,
    );
    final value = (dragging ?? engine.position.inMilliseconds.toDouble()).clamp(
      0.0,
      maximum,
    );
    return Row(
      children: [
        if (widget.showClock)
          Text(
            clock(Duration(milliseconds: value.round())),
            style: Theme.of(context).textTheme.labelSmall,
          ),
        Expanded(
          child: Slider(
            value: value,
            max: maximum,
            semanticFormatterCallback: (value) =>
                clock(Duration(milliseconds: value.round())),
            onChanged:
                widget.controller.current == null ||
                    engine.duration == Duration.zero
                ? null
                : (value) => setState(() => dragging = value),
            onChangeEnd: (value) {
              unawaited(
                widget.controller.perform(
                  () => engine.seek(Duration(milliseconds: value.round())),
                ),
              );
              setState(() => dragging = null);
            },
          ),
        ),
        if (widget.showClock)
          Text(
            clock(engine.duration),
            style: Theme.of(context).textTheme.labelSmall,
          ),
      ],
    );
  }
}

class FullPlayer extends StatefulWidget {
  const FullPlayer({super.key, required this.controller});
  final PlayerController controller;

  @override
  State<FullPlayer> createState() => _FullPlayerState();
}

class _FullPlayerState extends State<FullPlayer> {
  final scroll = ScrollController();
  int activeLine = -1;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(update);
    widget.controller.engine.addListener(update);
  }

  void update() {
    final index = widget.controller.lyricIndex;
    if (index == activeLine) return;
    activeLine = index;
    if (index >= 0 && scroll.hasClients) {
      unawaited(
        scroll.animateTo(
          (index * 80 - scroll.position.viewportDimension / 2 + 40).clamp(
            0,
            scroll.position.maxScrollExtent,
          ),
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        ),
      );
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(update);
    widget.controller.engine.removeListener(update);
    scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([widget.controller, widget.controller.engine]),
    builder: (context, child) {
      final controller = widget.controller;
      final track = controller.current;
      final appearance = AppearanceScope.maybeOf(context);
      return Stack(
        fit: StackFit.expand,
        children: [
          if (appearance != null)
            PlayerBackdrop(appearance: appearance, track: track),
          Scaffold(
            backgroundColor: appearance == null ? null : Colors.transparent,
            appBar: AppBar(
              leading: IconButton(
                tooltip: '返回',
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.keyboard_arrow_down),
              ),
              title: const Text('正在播放'),
              actions: [
                if (track != null)
                  IconButton(
                    tooltip: controller.favorites.contains(track.path)
                        ? '取消收藏'
                        : '收藏',
                    onPressed: () => controller.toggleFavorite(track),
                    icon: Icon(
                      controller.favorites.contains(track.path)
                          ? Icons.favorite
                          : Icons.favorite_border,
                    ),
                  ),
              ],
            ),
            body: LayoutBuilder(
              builder: (context, constraints) {
                final lyrics = controller.lyrics.isEmpty
                    ? const EmptyState(
                        icon: Icons.lyrics_outlined,
                        title: '让旋律说话',
                        description: '将同名 .lrc 文件放在音频旁边，即可显示同步歌词。',
                      )
                    : ListView.builder(
                        controller: scroll,
                        itemExtent: 80,
                        itemCount: controller.lyrics.length,
                        itemBuilder: (context, index) => InkWell(
                          onTap: () => controller.perform(
                            () => controller.engine.seek(
                              controller.lyrics[index].time,
                            ),
                          ),
                          child: Align(
                            alignment: Alignment.centerLeft,
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 24,
                              ),
                              child: Text(
                                '${controller.lyrics[index].text}${controller.showTranslation && controller.lyrics[index].translation.isNotEmpty ? '\n${controller.lyrics[index].translation}' : ''}',
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                                style: Theme.of(context).textTheme.titleLarge
                                    ?.copyWith(
                                      fontWeight: index == controller.lyricIndex
                                          ? FontWeight.bold
                                          : FontWeight.normal,
                                      color: index == controller.lyricIndex
                                          ? Theme.of(
                                              context,
                                            ).colorScheme.primary
                                          : Theme.of(
                                              context,
                                            ).colorScheme.onSurfaceVariant,
                                    ),
                              ),
                            ),
                          ),
                        ),
                      );
                final detail = SingleChildScrollView(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      children: [
                        Artwork(size: 180, track: track),
                        const SizedBox(height: 24),
                        Text(
                          track?.title ?? '尚未播放',
                          style: Theme.of(context).textTheme.headlineSmall,
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 8),
                        Text(track?.artist ?? '', textAlign: TextAlign.center),
                        const SizedBox(height: 24),
                        SeekBar(controller: controller),
                        PlaybackControls(controller: controller),
                      ],
                    ),
                  ),
                );
                return constraints.maxWidth >= 840
                    ? Row(
                        children: [
                          Expanded(child: Center(child: detail)),
                          Expanded(child: lyrics),
                        ],
                      )
                    : Column(
                        children: [
                          SizedBox(
                            height: constraints.maxHeight * 0.58,
                            child: detail,
                          ),
                          Expanded(child: lyrics),
                        ],
                      );
              },
            ),
          ),
        ],
      );
    },
  );
}
