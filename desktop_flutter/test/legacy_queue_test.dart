import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/legacy_import.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/player_controller.dart';

import 'fakes.dart';

const first = OnlineSong(
  provider: MusicProvider.netease,
  id: '1',
  title: 'First',
  artist: 'Neko',
);
const second = OnlineSong(
  provider: MusicProvider.netease,
  id: '2',
  title: 'Second',
  artist: 'Neko',
);
const third = OnlineSong(
  provider: MusicProvider.netease,
  id: '3',
  title: 'Third',
  artist: 'Neko',
);

class ImportEngine extends FakeEngine {
  int opens = 0;
  int clears = 0;
  bool failClear = false;

  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) async {
    opens++;
    await super.open(tracks, index, play: play);
  }

  @override
  Future<void> clearQueue() async {
    clears++;
    if (failClear) throw StateError('clear failed');
    await super.clearQueue();
  }
}

class NoNetworkSource implements OnlineAudioSource {
  int requests = 0;
  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) async {
    requests++;
    throw StateError('Unexpected network request');
  }

  @override
  Future<String> lyrics(OnlineSong song) async {
    requests++;
    return '';
  }
}

List<int> varint(int value) {
  final bytes = <int>[];
  while (value >= 128) {
    bytes.add((value & 127) | 128);
    value >>= 7;
  }
  return [...bytes, value];
}

List<int> message(int tag, List<int> bytes) => [
  tag,
  ...varint(bytes.length),
  ...bytes,
];

void main() {
  Map<String, String> values({int index = 3, int position = 23456}) => {
    'play_queue': jsonEncode([
      {'id': 1, 'nm': 'First', 'ar': 'Neko'},
      {'id': 9, 'source': 'local', 'path': '/private/file.mp3'},
      {'id': 2, 'nm': 'Second', 'ar': 'Neko'},
      {'id': 1, 'nm': 'First', 'ar': 'Neko'},
    ]),
    'play_index': '$index',
    'play_position': '$position',
  };

  test(
    'queue parsing retains duplicates and remaps selection after filtering',
    () {
      final parsed = LegacyPlaybackQueue.parse(values())!;
      expect(parsed.tracks.map((track) => track.path), [
        'netease:1',
        'netease:2',
        'netease:1',
      ]);
      expect(parsed.session.paths, ['netease:1', 'netease:2', 'netease:1']);
      expect(parsed.session.index, 2);
      expect(parsed.session.position.inMilliseconds, 23456);
      expect(parsed.skipped, 1);
      expect(parsed.selectionChanged, isFalse);
    },
  );

  test('unavailable selection advances to the next surviving item at zero', () {
    final parsed = LegacyPlaybackQueue.parse(values(index: 1))!;
    expect(parsed.session.index, 1);
    expect(parsed.session.position, Duration.zero);
    expect(parsed.selectionChanged, isTrue);
    final wrapped = LegacyPlaybackQueue.parse({
      'play_queue': jsonEncode([
        {'id': 1},
        {'source': 'unknown', 'id': 9},
      ]),
      'play_index': '1',
      'play_position': '999',
    })!;
    expect(wrapped.session.index, 0);
    expect(wrapped.session.position, Duration.zero);
  });

  test(
    'missing, empty and defaulted Android playback state are distinguished',
    () {
      expect(LegacyPlaybackQueue.parse({}), isNull);
      expect(LegacyPlaybackQueue.parse({'play_queue': '[]'}), isNull);
      final parsed = LegacyPlaybackQueue.parse({'play_queue': '[{"id":1}]'})!;
      expect(parsed.session.index, 0);
      expect(parsed.session.position, Duration.zero);
    },
  );

  test(
    'queue parsing rejects corrupt structure, foreign paths and bad progress',
    () {
      for (final data in [
        {'play_index': '0'},
        {'play_position': '1'},
        {'play_queue': 'not-json'},
        {'play_queue': '{}'},
        {'play_queue': '[null]'},
        {'play_queue': '[{"source":"local","path":"/private/file"}]'},
        {...values(), 'play_index': '-1'},
        {...values(), 'play_index': '4'},
        {...values(), 'play_index': '1.5'},
        {...values(), 'play_position': '-1'},
        {...values(), 'play_position': '604800001'},
        {...values(), 'play_position': 'invalid'},
      ]) {
        expect(() => LegacyPlaybackQueue.parse(data), throwsFormatException);
      }
    },
  );

  test(
    'Kugou and gateway queue identities preserve platform-specific metadata',
    () {
      const hash = 'ABCDEF0123456789ABCDEF0123456789';
      final parsed = LegacyPlaybackQueue.parse({
        'play_queue': jsonEncode([
          {
            'source': 'kugou',
            'id': 42,
            'hash': hash,
            'albumId': '12',
            'albumAudioId': 34,
            'nm': 'Kugou',
            'ar': 'Artist',
            'pc': 'https://cover.example/image',
          },
          {
            'source': 'animemusic-tx',
            'id': 0,
            'providerSource': 'tx',
            'providerMediaId': 'song-mid',
            'nm': 'Gateway',
            'ar': 'Artist',
          },
        ]),
        'play_index': '1',
        'play_position': '1234',
      })!;
      expect(parsed.tracks[0].path, 'kugou:${hash.toLowerCase()}');
      expect(parsed.tracks[0].online!.albumId, '12');
      expect(parsed.tracks[0].online!.albumAudioId, '34');
      expect(parsed.tracks[0].online!.artwork, 'https://cover.example/image');
      expect(parsed.tracks[1].path, 'animemusic-tx:song-mid');
      expect(parsed.session.index, 1);
      expect(parsed.session.position.inMilliseconds, 1234);
    },
  );

  group('explicit queue backup import', () {
    late Directory directory;
    setUp(
      () async => directory = await Directory.systemTemp.createTemp(
        'neko-queue-import-',
      ),
    );
    tearDown(() async => directory.delete(recursive: true));

    test(
      'Android XML queue is ignored by default and does not import settings or credentials',
      () async {
        final file = File('${directory.path}/settings.xml');
        final original =
            '<map><string name="play_queue">${values()['play_queue']}</string>'
            '<int name="play_index" value="3"/><long name="play_position" value="23456"/>'
            '<float name="playback_speed" value="1.5"/>'
            '<string name="nc_cookie">MUSIC_U=private</string></map>';
        await file.writeAsString(original);
        await expectLater(
          LegacyImport.readFiles([file.path]),
          throwsFormatException,
        );
        final imported = await LegacyImport.readFiles([
          file.path,
        ], includeQueue: true);
        expect(imported.queue!.session.index, 2);
        expect(imported.queue!.session.position.inMilliseconds, 23456);
        expect(imported.playback.count, 0);
        expect(imported.hasCredentials, isFalse);
        expect(imported.decoded.tracks, isEmpty);
        expect(await file.readAsString(), original);
        final settings = await LegacyImport.readFiles([
          file.path,
        ], includeSettings: true);
        expect(settings.queue, isNull);
        expect(settings.playback.rate, 1.5);
      },
    );

    test(
      'typed DataStore queue and progress are restored with millisecond precision',
      () async {
        final file = File('${directory.path}/player.preferences_pb');
        await file.writeAsBytes([
          for (final entry in {
            'play_queue': message(42, utf8.encode(values()['play_queue']!)),
            'play_index': [24, ...varint(3)],
            'play_position': [32, ...varint(23456)],
          }.entries)
            ...message(10, [
              ...message(10, utf8.encode(entry.key)),
              ...message(18, entry.value),
            ]),
        ]);
        final imported = await LegacyImport.readFiles([
          file.path,
        ], includeQueue: true);
        expect(imported.queue!.tracks.length, 3);
        expect(imported.queue!.session.index, 2);
        expect(imported.queue!.session.position.inMilliseconds, 23456);
      },
    );

    test(
      'malformed unused queue cannot prevent a normal library import',
      () async {
        final file = File('${directory.path}/user_data.xml');
        await file.writeAsString(
          '<map><string name="my_favorites">[{"id":1}]</string>'
          '<string name="play_queue">invalid-json</string></map>',
        );
        final library = await LegacyImport.readFiles([file.path]);
        expect(library.decoded.favorites, {'netease:1'});
        expect(library.queue, isNull);
        await expectLater(
          LegacyImport.readFiles([file.path], includeQueue: true),
          throwsFormatException,
        );
      },
    );
  });

  group('deferred player queue import', () {
    late PlayerController player;
    late ImportEngine engine;
    late MemoryStore store;
    late NoNetworkSource source;
    final incoming = [
      Track.fromOnline(first),
      Track.fromOnline(second),
      Track.fromOnline(first),
    ];
    final session = PlaybackSession(
      paths: incoming.map((track) => track.path).toList(),
      index: 2,
      position: const Duration(milliseconds: 23456),
    );

    setUp(() async {
      engine = ImportEngine();
      store = MemoryStore();
      source = NoNetworkSource();
      player = PlayerController(
        engine: engine,
        store: store,
        onlineSource: source,
      );
      await player.initialize();
    });
    tearDown(() async {
      await player.flushed;
      player.dispose();
    });

    test(
      'import pauses playback and persists a duplicate queue without resolving any audio',
      () async {
        const local = Track(
          path: '/old/music.wav',
          title: 'Local',
          artist: 'Before',
        );
        player.tracks = [
          local,
          Track.fromOnline(
            const OnlineSong(
              provider: MusicProvider.netease,
              id: '1',
              title: 'Local metadata',
              artist: 'Local artist',
            ),
          ),
        ];
        player.queue = [local];
        player.favorites = {local.path};
        player.history = [local.path];
        player.playlists = const [
          LocalPlaylist(id: 'old', name: 'Keep', paths: ['/old/music.wav']),
        ];
        engine.currentPath = local.path;
        engine.playing = true;
        await player.importPlaybackQueue(incoming, session);
        expect(engine.playing, isFalse);
        expect(engine.opens, 0);
        expect(engine.currentPath, isNull);
        expect(source.requests, 0);
        expect(player.queue.map((track) => track.path), session.paths);
        expect(player.queue[2].title, 'Local metadata');
        final saved = LibraryData.decode(store.value!);
        expect(saved.session!.toJson(), session.toJson());
        expect(saved.tracks.length, 3);
        expect(saved.favorites, {local.path});
        expect(saved.history, [local.path]);
        expect(saved.playlists.single.name, 'Keep');
        await player.toggle();
        expect(engine.opens, 1);
        expect(engine.index, 2);
        expect(engine.position.inMilliseconds, 23456);
        expect(engine.playing, isTrue);
      },
    );

    test(
      'pending queue append, reorder and removal preserve selection without autoplay',
      () async {
        await player.importPlaybackQueue(incoming, session);
        await player.moveInQueue(2, 0);
        expect(player.snapshot.session!.index, 0);
        expect(player.snapshot.session!.position.inMilliseconds, 23456);
        await player.enqueue(Track.fromOnline(third));
        expect(player.queue.map((track) => track.path), [
          'netease:1',
          'netease:1',
          'netease:2',
          'netease:3',
        ]);
        await player.removeFromQueue(1);
        expect(player.snapshot.session!.position.inMilliseconds, 23456);
        await player.removeFromQueue(0);
        expect(player.queue.map((track) => track.path), [
          'netease:2',
          'netease:3',
        ]);
        expect(player.snapshot.session!.index, 0);
        expect(player.snapshot.session!.position, Duration.zero);
        await player.removeFromQueue(0);
        await player.removeFromQueue(0);
        expect(player.queue, isEmpty);
        expect(player.snapshot.session, isNull);
        expect(engine.opens, 0);
        expect(source.requests, 0);
      },
    );

    test('import takes a snapshot of caller-owned queue lists', () async {
      final tracks = List<Track>.of(incoming);
      final paths = List<String>.of(session.paths);
      final imported = player.importPlaybackQueue(
        tracks,
        PlaybackSession(paths: paths, index: 2, position: session.position),
      );
      tracks.clear();
      paths.clear();
      await imported;
      expect(player.queue.length, 3);
      expect(player.snapshot.session!.paths.length, 3);
    });

    test(
      'storage failure is reported and an explicit retry can recover',
      () async {
        store.failWrite = true;
        await expectLater(
          player.importPlaybackQueue(incoming, session),
          throwsStateError,
        );
        expect(store.value, isNull);
        expect(engine.opens, 0);
        expect(source.requests, 0);
        store.failWrite = false;
        await player.importPlaybackQueue(incoming, session);
        expect(LibraryData.decode(store.value!).session!.index, 2);
        expect(player.error, isNull);
      },
    );

    test(
      'failure to stop the old engine never replaces queue or writes storage',
      () async {
        player.queue = [Track.fromOnline(third)];
        engine.failClear = true;
        await expectLater(
          player.importPlaybackQueue(incoming, session),
          throwsStateError,
        );
        expect(player.queue.single.path, 'netease:3');
        expect(store.value, isNull);
        expect(player.tracks, isEmpty);
        expect(engine.opens, 0);
      },
    );

    test(
      'queue import refuses to overwrite an unreadable local library',
      () async {
        final brokenStore = MemoryStore()..value = 'damaged-library';
        final brokenEngine = ImportEngine();
        final broken = PlayerController(
          engine: brokenEngine,
          store: brokenStore,
        );
        await broken.initialize();
        try {
          await expectLater(
            broken.importPlaybackQueue(incoming, session),
            throwsA(isA<MusicSearchException>()),
          );
          expect(brokenStore.value, 'damaged-library');
          expect(brokenEngine.clears, 0);
          expect(brokenEngine.opens, 0);
        } finally {
          broken.dispose();
        }
      },
    );

    test(
      'pending selection remains exact after edits and explicit resume',
      () async {
        await player.importPlaybackQueue(incoming, session);
        await player.removeFromQueue(0);
        expect(player.snapshot.session!.index, 1);
        expect(player.snapshot.session!.position, session.position);
        await player.moveInQueue(0, 1);
        expect(player.snapshot.session!.index, 0);
        expect(player.snapshot.session!.position, session.position);
        await player.toggle();
        expect(engine.currentPath, 'netease:1');
        expect(engine.index, 0);
        expect(engine.position, session.position);
        expect(engine.playing, isTrue);
      },
    );

    test(
      'pending queue cannot grow beyond the session storage limit',
      () async {
        final large = List<Track>.filled(
          PlaybackSession.maxQueueLength,
          Track.fromOnline(first),
        );
        await player.importPlaybackQueue(
          large,
          PlaybackSession(
            paths: large.map((track) => track.path).toList(),
            index: 0,
            position: Duration.zero,
          ),
        );
        await player.enqueue(Track.fromOnline(third));
        expect(player.queue.length, PlaybackSession.maxQueueLength);
        expect(player.error, contains('最多'));
        expect(engine.opens, 0);
        expect(source.requests, 0);
        expect(
          LibraryData.decode(store.value!).session!.paths.length,
          PlaybackSession.maxQueueLength,
        );
      },
    );

    test(
      'oversized play request cannot create an unreadable saved session',
      () async {
        final large = List<Track>.filled(
          PlaybackSession.maxQueueLength + 1,
          Track.fromOnline(first),
        );
        await player.play(large, 0);
        expect(player.queue, isEmpty);
        expect(player.error, contains('最多'));
        expect(engine.opens, 0);
        expect(store.value, isNull);
      },
    );

    test(
      'invalid queue identities are rejected before stopping the engine',
      () async {
        for (final tracks in [
          <Track>[],
          [const Track(path: '/private/file', title: 'Local', artist: '')],
          [Track.fromOnline(third)],
          [
            const Track(
              path: 'file:///private',
              title: 'Wrong path',
              artist: '',
              online: first,
            ),
          ],
        ]) {
          await expectLater(
            player.importPlaybackQueue(tracks, session),
            throwsFormatException,
          );
        }
        expect(engine.clears, 0);
        expect(engine.opens, 0);
        expect(store.value, isNull);
      },
    );
  });
}
