import 'package:flutter/material.dart';

import '../data/account_library_sync.dart';
import '../data/account_music_repository.dart';
import '../domain/library.dart';
import '../domain/online_music.dart';
import '../player_controller.dart';
import 'playlist_picker.dart';

class AccountMusicPage extends StatefulWidget {
  const AccountMusicPage({
    super.key,
    required this.repository,
    required this.player,
    required this.onComments,
    this.onDownload,
    this.songLoader,
    this.title,
    this.collectionId,
  });
  final AccountMusicRepository repository;
  final PlayerController player;
  final void Function(OnlineSong) onComments;
  final void Function(OnlineSong)? onDownload;
  final Future<AccountPage<OnlineSong>> Function(int)? songLoader;
  final String? title;
  final String? collectionId;

  @override
  State<AccountMusicPage> createState() => _AccountMusicPageState();
}

class _AccountMusicPageState extends State<AccountMusicPage> {
  MusicProvider provider = MusicProvider.netease;
  AccountCollection collection = AccountCollection.playlists;
  AccountPlaylist? selected;
  List<AccountPlaylist> playlists = [];
  List<OnlineSong> songs = [];
  int page = 0;
  int generation = 0;
  bool busy = false;
  bool more = false;
  String? error;
  late String session;
  late final AccountLibrarySync sync;

  @override
  void initState() {
    super.initState();
    sync = AccountLibrarySync(
      sessionKey: () => widget.repository.sessionKey,
      merge: widget.player.mergeCloud,
    )..addListener(syncChanged);
    if (widget.songLoader != null) {
      selected = AccountPlaylist(
        id: widget.collectionId ?? 'browse',
        name: widget.title ?? '歌曲',
      );
    }
    session = widget.repository.sessionKey;
    widget.repository.catalog.accounts.addListener(accountChanged);
    load(reset: true);
  }

  void syncChanged() {
    if (mounted) setState(() {});
  }

  void accountChanged() {
    final next = widget.repository.sessionKey;
    if (session == next) return;
    session = next;
    selected = widget.songLoader == null
        ? null
        : AccountPlaylist(
            id: widget.collectionId ?? 'browse',
            name: widget.title ?? '歌曲',
          );
    load(reset: true);
  }

  @override
  void dispose() {
    generation++;
    sync.dispose();
    widget.repository.catalog.accounts.removeListener(accountChanged);
    super.dispose();
  }

  Future<void> load({bool reset = false}) async {
    if (busy && !reset) return;
    if (reset) sync.cancel();
    final current = ++generation;
    final sessionAtStart = widget.repository.sessionKey;
    final nextPage = reset ? 1 : page + 1;
    setState(() {
      busy = true;
      error = null;
      if (reset) {
        songs = [];
        playlists = [];
        page = 0;
        more = false;
      }
    });
    try {
      if (selected == null &&
          [
            AccountCollection.playlists,
            AccountCollection.following,
          ].contains(collection)) {
        final result = await widget.repository.playlists(
          provider,
          nextPage,
          following: collection == AccountCollection.following,
        );
        if (!mounted ||
            current != generation ||
            sessionAtStart != widget.repository.sessionKey) {
          return;
        }
        final byId = {for (final playlist in playlists) playlist.id: playlist};
        for (final playlist in result.items) {
          byId[playlist.id] = playlist;
        }
        setState(() {
          playlists = byId.values.toList();
          more = result.hasMore;
          page = nextPage;
        });
      } else {
        final result = widget.songLoader != null
            ? await widget.songLoader!(nextPage)
            : selected != null
            ? await widget.repository.playlistSongs(
                provider,
                selected!,
                nextPage,
              )
            : await widget.repository.collection(
                collection,
                nextPage,
                provider: provider,
              );
        if (!mounted ||
            current != generation ||
            sessionAtStart != widget.repository.sessionKey) {
          return;
        }
        final byId = {for (final song in songs) song.key: song};
        for (final song in result.items) {
          byId[song.key] = song;
        }
        setState(() {
          songs = byId.values.toList();
          more = result.hasMore;
          page = nextPage;
        });
      }
    } on MusicSearchException catch (failure) {
      if (mounted && current == generation) {
        setState(() => error = failure.message);
      }
    } catch (_) {
      if (mounted && current == generation) {
        setState(() => error = '读取失败，请检查网络后重试。');
      }
    } finally {
      if (mounted && current == generation) setState(() => busy = false);
    }
  }

  String label(AccountCollection value) => switch (value) {
    AccountCollection.playlists => '账号歌单',
    AccountCollection.likes => '红心歌曲',
    AccountCollection.history => '最近一周记录',
    AccountCollection.recommendations => '每日推荐',
    AccountCollection.newSongs => '新歌速递',
    AccountCollection.following => '关注歌手',
  };

  Future<void> save([
    AccountLibraryDestination destination = AccountLibraryDestination.playlist,
  ]) async {
    if (sync.busy || busy || songs.isEmpty) return;
    final sourceGeneration = generation;
    final sourceSession = session;
    final name = selected?.name ?? label(collection);
    final id = selected == null
        ? collection.name
        : '${selected!.artist ? 'artist-' : ''}${selected!.id}';
    final sourceProvider = provider;
    final sourceCollection = collection;
    final sourcePlaylist = selected;
    final sourceLoader = widget.songLoader;
    final target = switch (destination) {
      AccountLibraryDestination.playlist => '本机歌单',
      AccountLibraryDestination.favorites => '本机收藏',
      AccountLibraryDestination.history => '本机历史',
    };
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('将全部歌曲合并到$target？'),
        content: Text(
          '将逐页读取「$name」，不只保存当前已加载的歌曲。读取完成后一次合并，取消或读取失败不保存部分结果；不会修改平台账号或下载音频。'
          '${destination == AccountLibraryDestination.history ? '\n本机历史最多保留 200 首，已有记录优先保留。' : ''}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('开始同步'),
          ),
        ],
      ),
    );
    if (confirmed != true ||
        !mounted ||
        generation != sourceGeneration ||
        session != sourceSession ||
        sync.busy) {
      return;
    }
    try {
      final count = await sync.run(
        load:
            sourceLoader ??
            (page) => sourcePlaylist != null
                ? widget.repository.playlistSongs(
                    sourceProvider,
                    sourcePlaylist,
                    page,
                  )
                : widget.repository.collection(
                    sourceCollection,
                    page,
                    provider: sourceProvider,
                  ),
        destination: destination,
        playlistId: 'account-${sourceProvider.name}-$id',
        playlistName: name,
      );
      if (mounted &&
          generation == sourceGeneration &&
          session == sourceSession) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              count == null
                  ? '已取消同步，未保存部分结果。'
                  : '已读取 $count 首歌曲并合并到$target；未修改平台账号。',
            ),
          ),
        );
      }
    } catch (failure) {
      if (mounted &&
          generation == sourceGeneration &&
          session == sourceSession) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              failure is MusicSearchException
                  ? failure.message
                  : '同步失败，请检查网络与本机存储后重试。',
            ),
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final showingPlaylists =
        selected == null &&
        [
          AccountCollection.playlists,
          AccountCollection.following,
        ].contains(collection);
    return Scaffold(
      appBar: AppBar(title: Text(widget.title ?? '平台音乐库')),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1000),
          child: CustomScrollView(
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (widget.songLoader == null)
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            for (final value in [
                              MusicProvider.netease,
                              MusicProvider.kugou,
                            ])
                              ChoiceChip(
                                label: Text(value.label),
                                selected: provider == value,
                                onSelected: (_) {
                                  provider = value;
                                  selected = null;
                                  collection = AccountCollection.playlists;
                                  load(reset: true);
                                },
                              ),
                          ],
                        ),
                      if (widget.songLoader == null) ...[
                        const SizedBox(height: 12),
                        DropdownButtonFormField<AccountCollection>(
                          key: ValueKey(collection),
                          initialValue: collection,
                          isExpanded: true,
                          decoration: const InputDecoration(labelText: '浏览内容'),
                          items: [
                            for (final value
                                in (provider == MusicProvider.netease
                                    ? AccountCollection.values.where(
                                        (value) =>
                                            value !=
                                            AccountCollection.following,
                                      )
                                    : [
                                        AccountCollection.playlists,
                                        AccountCollection.history,
                                        AccountCollection.recommendations,
                                        AccountCollection.following,
                                      ]))
                              DropdownMenuItem(
                                value: value,
                                child: Text(label(value)),
                              ),
                          ],
                          onChanged: (value) {
                            if (value == null) return;
                            collection = value;
                            selected = null;
                            load(reset: true);
                          },
                        ),
                      ],
                      const SizedBox(height: 12),
                      Text(
                        provider == MusicProvider.kugou
                            ? '标准版 / 概念版账号跟随平台账号设置。只读浏览，不修改远端歌单。'
                            : '只读浏览平台内容；保存到本机不等于收藏到平台账号。',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      if (selected != null && widget.songLoader == null)
                        TextButton.icon(
                          onPressed: () {
                            selected = null;
                            load(reset: true);
                          },
                          icon: const Icon(Icons.arrow_back),
                          label: Text('歌单 · ${selected!.name}'),
                        ),
                      if (songs.isNotEmpty)
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            FilledButton.tonalIcon(
                              onPressed: () => widget.player.play(
                                songs.map(Track.fromOnline).toList(),
                                0,
                              ),
                              icon: const Icon(Icons.play_arrow),
                              label: Text('播放已加载 ${songs.length} 首'),
                            ),
                            OutlinedButton.icon(
                              onPressed: sync.busy || busy
                                  ? null
                                  : () => save(),
                              icon: const Icon(Icons.playlist_add),
                              label: const Text('保存全部歌曲到本机歌单'),
                            ),
                            if (widget.songLoader == null &&
                                selected == null &&
                                [
                                  AccountCollection.likes,
                                  AccountCollection.history,
                                ].contains(collection))
                              OutlinedButton.icon(
                                onPressed: sync.busy || busy
                                    ? null
                                    : () => save(
                                        collection == AccountCollection.likes
                                            ? AccountLibraryDestination
                                                  .favorites
                                            : AccountLibraryDestination.history,
                                      ),
                                icon: const Icon(Icons.sync),
                                label: Text(
                                  collection == AccountCollection.likes
                                      ? '全部同步到收藏'
                                      : '全部同步到历史',
                                ),
                              ),
                          ],
                        ),
                    ],
                  ),
                ),
              ),
              if (busy)
                const SliverToBoxAdapter(child: LinearProgressIndicator()),
              if (sync.busy)
                SliverToBoxAdapter(
                  child: ListTile(
                    title: Text('已读取 ${sync.pages} 页 · ${sync.count} 首'),
                    subtitle: Text(
                      sync.committing
                          ? '正在保存到本机…'
                          : sync.cancelled
                          ? '等待当前请求结束后取消…'
                          : '全部读取成功后合并到本机',
                    ),
                    trailing: TextButton(
                      onPressed: sync.cancelled || sync.committing
                          ? null
                          : sync.cancel,
                      child: const Text('取消同步'),
                    ),
                  ),
                ),
              if (error != null)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            error!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                        TextButton(
                          onPressed: busy ? null : () => load(reset: page == 0),
                          child: const Text('重试'),
                        ),
                      ],
                    ),
                  ),
                ),
              if (!busy && error == null && playlists.isEmpty && songs.isEmpty)
                const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(child: Text('暂无内容')),
                )
              else
                SliverList.builder(
                  itemCount:
                      (showingPlaylists ? playlists.length : songs.length) +
                      (more ? 1 : 0),
                  itemBuilder: (context, index) {
                    final count = showingPlaylists
                        ? playlists.length
                        : songs.length;
                    if (index == count) {
                      return Center(
                        child: TextButton(
                          onPressed: busy ? null : load,
                          child: const Text('加载更多'),
                        ),
                      );
                    }
                    if (showingPlaylists) {
                      final playlist = playlists[index];
                      return ListTile(
                        leading: const Icon(Icons.queue_music),
                        title: Text(playlist.name),
                        subtitle: Text('${playlist.count} 首'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () {
                          selected = playlist;
                          load(reset: true);
                        },
                      );
                    }
                    final song = songs[index];
                    return ListTile(
                      leading: const Icon(Icons.music_note),
                      title: Text(song.title),
                      subtitle: Text(song.artist),
                      onTap: () => widget.player.play(
                        songs.map(Track.fromOnline).toList(),
                        index,
                      ),
                      trailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          IconButton(
                            tooltip: '查看评论',
                            icon: const Icon(Icons.comment_outlined),
                            onPressed: () => widget.onComments(song),
                          ),
                          PopupMenuButton<String>(
                            tooltip: '歌曲操作',
                            onSelected: (action) {
                              final track = Track.fromOnline(song);
                              switch (action) {
                                case 'queue':
                                  widget.player.enqueue(track);
                                case 'favorite':
                                  widget.player.toggleFavorite(track);
                                case 'playlist':
                                  showPlaylistPicker(
                                    context,
                                    widget.player,
                                    track,
                                  );
                                case 'download':
                                  widget.onDownload?.call(song);
                              }
                            },
                            itemBuilder: (_) => [
                              const PopupMenuItem(
                                value: 'queue',
                                child: Text('加入播放队列'),
                              ),
                              PopupMenuItem(
                                value: 'favorite',
                                child: Text(
                                  widget.player.favorites.contains(
                                        Track.fromOnline(song).path,
                                      )
                                      ? '取消收藏'
                                      : '收藏',
                                ),
                              ),
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
                          ),
                        ],
                      ),
                    );
                  },
                ),
            ],
          ),
        ),
      ),
    );
  }
}
