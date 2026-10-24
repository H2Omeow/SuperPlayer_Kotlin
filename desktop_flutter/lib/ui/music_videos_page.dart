import 'dart:async';

import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

import '../data/native_music_catalog.dart';
import '../domain/online_music.dart';
import '../video_search_controller.dart';

class MusicVideosPage extends StatefulWidget {
  const MusicVideosPage({
    super.key,
    required this.catalog,
    required this.pauseMusic,
    this.provider = MusicProvider.netease,
    this.volume = 75,
  });
  final NativeMusicCatalog catalog;
  final Future<void> Function() pauseMusic;
  final MusicProvider provider;
  final double volume;
  @override
  State<MusicVideosPage> createState() => _MusicVideosPageState();
}

class _MusicVideosPageState extends State<MusicVideosPage> {
  late final VideoSearchController search = VideoSearchController(
    widget.catalog.searchVideos,
    provider: widget.provider,
  );
  final input = TextEditingController();
  bool resolving = false;
  String? error;

  Future<void> play(MusicVideo video) async {
    if (resolving) return;
    setState(() {
      resolving = true;
      error = null;
    });
    try {
      final uri = await widget.catalog.videoUrl(video);
      if (!mounted) return;
      await widget.pauseMusic();
      if (!mounted) return;
      await Navigator.of(context).push(
        MaterialPageRoute<void>(
          builder: (_) => _VideoPlaybackPage(
            uri: uri,
            title: video.title,
            volume: widget.volume,
          ),
        ),
      );
    } catch (failure) {
      if (mounted) {
        setState(
          () => error = failure is MusicSearchException
              ? failure.message
              : 'MV 暂时无法播放，请检查网络与播放设备。',
        );
      }
    } finally {
      if (mounted) setState(() => resolving = false);
    }
  }

  @override
  void dispose() {
    search.dispose();
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('音乐 MV')),
    body: Padding(
      padding: const EdgeInsets.all(20),
      child: ListenableBuilder(
        listenable: search,
        builder: (context, _) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('内置 Animemusic 插件提供 MV，免本站登录。播放视频时暂停音乐。'),
            const SizedBox(height: 12),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  for (final provider in MusicProvider.values)
                    Padding(
                      padding: const EdgeInsets.only(right: 8),
                      child: ChoiceChip(
                        label: Text(provider.label),
                        selected: search.provider == provider,
                        onSelected: resolving
                            ? null
                            : (_) => search.selectProvider(provider),
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
              onSubmitted: search.search,
              decoration: InputDecoration(
                hintText: '搜索 MV、歌手',
                counterText: '',
                suffixIcon: IconButton(
                  tooltip: '搜索 MV',
                  onPressed: () => search.search(input.text),
                  icon: const Icon(Icons.search),
                ),
              ),
            ),
            if (search.loading || resolving) const LinearProgressIndicator(),
            if (search.error != null || error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  error ?? search.error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            const SizedBox(height: 12),
            Expanded(
              child: search.videos.isEmpty
                  ? const Center(child: Text('输入关键词搜索音乐 MV。'))
                  : ListView.builder(
                      itemCount: search.videos.length + 1,
                      itemBuilder: (context, index) {
                        if (index == search.videos.length) {
                          return search.hasMore
                              ? Center(
                                  child: OutlinedButton(
                                    onPressed: search.loading
                                        ? null
                                        : search.loadMore,
                                    child: const Text('加载更多'),
                                  ),
                                )
                              : const SizedBox.shrink();
                        }
                        final video = search.videos[index];
                        return ListTile(
                          leading: const Icon(Icons.video_library_outlined),
                          title: Text(
                            video.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(video.artist),
                          trailing: const Icon(Icons.play_circle_outline),
                          onTap: resolving ? null : () => play(video),
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

class _VideoPlaybackPage extends StatefulWidget {
  const _VideoPlaybackPage({
    required this.uri,
    required this.title,
    required this.volume,
  });
  final Uri uri;
  final String title;
  final double volume;
  @override
  State<_VideoPlaybackPage> createState() => _VideoPlaybackPageState();
}

class _VideoPlaybackPageState extends State<_VideoPlaybackPage> {
  late final Player player;
  late final VideoController video;
  StreamSubscription<String>? errors;
  String? error;

  @override
  void initState() {
    super.initState();
    player = Player(
      configuration: const PlayerConfiguration(
        title: 'NekoPlayer MV',
        bufferSize: 16 * 1024 * 1024,
      ),
    );
    video = VideoController(player);
    errors = player.stream.error.listen((_) {
      if (mounted) setState(() => error = '视频解码失败，请检查视频地址与播放依赖。');
    });
    unawaited(
      (() async {
        await player.setVolume(widget.volume.clamp(0, 100));
        await player.open(Media(widget.uri.toString()));
      })().catchError((Object _) {
        if (mounted) setState(() => error = '视频无法播放，请稍后重试。');
      }),
    );
  }

  @override
  void dispose() {
    unawaited(errors?.cancel());
    unawaited(player.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    appBar: AppBar(title: Text(widget.title)),
    body: Stack(
      children: [
        Video(controller: video),
        if (error != null)
          Center(
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Text(error!),
              ),
            ),
          ),
      ],
    ),
  );
}
