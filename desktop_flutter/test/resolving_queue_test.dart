import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/playback/playback_engine.dart';
import 'package:nekoplayer_desktop/playback/resolving_queue.dart';

class QueueSource implements OnlineAudioSource {
  int requests = 0;
  bool fail = false;
  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) async {
    requests++;
    if (fail) throw const MusicSearchException('权限不足');
    return ResolvedAudio(
      Uri.https('audio.example', '/${song.id}/$requests'),
      preferred,
      headers: {'Referer': 'https://example.test/'},
    );
  }

  @override
  Future<String> lyrics(OnlineSong song) async => '';
}

void main() {
  test(
    'resolved headers reach the audio loader and clear for local tracks',
    () async {
      final loaded = <ResolvedAudio>[];
      final queue = ResolvingQueue(
        source: QueueSource(),
        load: (_) async => fail('URI-only loader must not discard headers'),
        loadAudio: (audio) async => loaded.add(audio),
      );
      await queue.open([
        Track.fromOnline(
          const OnlineSong(
            provider: MusicProvider.netease,
            id: '1',
            title: '歌',
            artist: '歌手',
          ),
        ),
      ], 0);
      expect(loaded.single.headers, {'Referer': 'https://example.test/'});
      await queue.open([
        const Track(path: '/tmp/local.mp3', title: 'Local', artist: ''),
      ], 0);
      expect(loaded.last.headers, isEmpty);
    },
  );
  test(
    'editing queue preserves stream and next resolves fresh after reorder',
    () async {
      final source = QueueSource();
      final items = [
        for (final id in ['1', '2', '3'])
          Track.fromOnline(
            OnlineSong(
              provider: MusicProvider.netease,
              id: id,
              title: id,
              artist: 'Neko',
            ),
          ),
      ];
      var loads = 0;
      final queue = ResolvingQueue(
        source: source,
        load: (_) async {
          loads++;
        },
      );
      await queue.open(items, 1);
      queue.replace([items[2], items[1], items[0]], 1);
      expect(source.requests, 1);
      expect(loads, 1);
      expect(queue.current?.online?.id, '2');
      expect(() => queue.replace(items, 0), throwsArgumentError);
      expect(queue.current?.online?.id, '2');
      await queue.advance(1);
      expect(queue.current?.online?.id, '1');
      expect(source.requests, 2);
      queue.clear();
      expect(queue.current, isNull);
      expect(await queue.advance(1), isFalse);
    },
  );

  final tracks = [
    for (var index = 0; index < 3; index++)
      Track.fromOnline(
        OnlineSong(
          provider: MusicProvider.netease,
          id: '$index',
          title: '歌曲$index',
          artist: 'Neko',
        ),
      ),
  ];

  test('resolves each selection and repeats with a fresh URL', () async {
    final source = QueueSource();
    final loaded = <Uri>[];
    final queue = ResolvingQueue(
      source: source,
      load: (uri) async => loaded.add(uri),
    );
    await queue.open(tracks, 0);
    await queue.advance(1);
    expect(queue.current?.path, 'netease:1');
    queue.repeat = RepeatMode.single;
    await queue.advance(1, automatic: true);
    expect(queue.current?.path, 'netease:1');
    expect(loaded[1], isNot(loaded[2]));
    expect(source.requests, 3);
  });

  test(
    'sequential stops at end, loop wraps, manual skip ignores single repeat',
    () async {
      final queue = ResolvingQueue(source: QueueSource(), load: (_) async {});
      await queue.open(tracks, 2);
      expect(await queue.advance(1, automatic: true), isFalse);
      expect(queue.index, 2);
      queue.repeat = RepeatMode.loop;
      expect(await queue.advance(1, automatic: true), isTrue);
      expect(queue.index, 0);
      queue.repeat = RepeatMode.single;
      await queue.advance(1);
      expect(queue.index, 1);
      await queue.advance(-1);
      expect(queue.index, 0);
    },
  );

  test('shuffle avoids immediately repeating current song', () async {
    final queue = ResolvingQueue(
      source: QueueSource(),
      load: (_) async {},
      random: Random(5),
    );
    await queue.open(tracks, 0);
    queue.repeat = RepeatMode.shuffle;
    for (var count = 0; count < 20; count++) {
      final previous = queue.index;
      await queue.advance(1, automatic: true);
      expect(queue.index, isNot(previous));
    }
  });

  test('failed resolve or load does not replace active queue', () async {
    final source = QueueSource();
    var failLoad = false;
    final queue = ResolvingQueue(
      source: source,
      load: (_) async {
        if (failLoad) throw StateError('output failed');
      },
    );
    await queue.open(tracks, 1);
    source.fail = true;
    await expectLater(
      queue.open([tracks.first], 0),
      throwsA(isA<MusicSearchException>()),
    );
    expect(queue.current?.path, 'netease:1');
    source.fail = false;
    failLoad = true;
    await expectLater(queue.advance(1), throwsStateError);
    expect(queue.index, 1);
    failLoad = false;
    await queue.advance(1);
    expect(queue.current?.path, 'netease:2');
  });

  test('local paths remain file URIs and need no online source', () async {
    final localPath = Platform.isWindows
        ? r'C:\music\夜 晚.flac'
        : '/music/夜 晚.flac';
    Uri? loaded;
    final queue = ResolvingQueue(load: (uri) async => loaded = uri);
    await queue.open([Track(path: localPath, title: '夜晚', artist: 'Neko')], 0);
    expect(loaded?.scheme, 'file');
    expect(loaded?.toFilePath(), localPath);
  });
}
