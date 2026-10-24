import 'dart:async';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/playback/playback_engine.dart';
import 'package:nekoplayer_desktop/player_controller.dart';

import 'fakes.dart';

class PendingEngine extends FakeEngine {
  int opens = 0;
  int seeks = 0;
  int pauses = 0;
  int nextCalls = 0;
  int previousCalls = 0;
  Completer<void>? opening;

  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) async {
    opens++;
    await opening?.future;
    await super.open(tracks, index, play: play);
  }

  @override
  Future<void> seek(Duration position) async {
    seeks++;
    await super.seek(position);
  }

  @override
  Future<void> pause() async {
    pauses++;
    await super.pause();
  }

  @override
  Future<void> next() async {
    nextCalls++;
  }

  @override
  Future<void> previous() async {
    previousCalls++;
  }
}

class CountingLyrics implements OnlineAudioSource {
  final songs = <String>[];

  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) async {
    throw StateError('No audio should be resolved by the fake engine');
  }

  @override
  Future<String> lyrics(OnlineSong song) async {
    songs.add(song.id);
    return '[00:00.00]${song.title}';
  }
}

final songs = [
  for (final id in ['1', '2', '1'])
    Track.fromOnline(
      OnlineSong(
        provider: MusicProvider.netease,
        id: id,
        title: 'Song $id',
        artist: 'Neko',
      ),
    ),
];

void main() {
  late PendingEngine engine;
  late MemoryStore store;
  late CountingLyrics source;
  late PlayerController player;

  Future<void> importQueue({int index = 2}) => player.importPlaybackQueue(
    songs,
    PlaybackSession(
      paths: songs.map((track) => track.path).toList(),
      index: index,
      position: const Duration(seconds: 23),
    ),
  );

  setUp(() async {
    engine = PendingEngine();
    store = MemoryStore();
    source = CountingLyrics();
    player = PlayerController(
      engine: engine,
      store: store,
      onlineSource: source,
      random: Random(7),
    );
    await player.initialize();
  });
  tearDown(() async {
    await player.flushed;
    player.dispose();
  });

  test(
    'restart preserves imported selection and progress without opening the engine',
    () async {
      await importQueue();
      await player.flushed;
      player.dispose();
      engine = PendingEngine();
      source = CountingLyrics();
      player = PlayerController(
        engine: engine,
        store: store,
        onlineSource: source,
      );
      await player.initialize();
      expect(player.sessionPending, isTrue);
      expect(player.queueIndex, 2);
      expect(player.position, const Duration(seconds: 23));
      expect(engine.opens, 0);
      expect(source.songs, isEmpty);
      expect(player.history, isEmpty);
      await player.resume();
      expect(engine.opens, 1);
      expect(engine.index, 2);
      expect(engine.position, const Duration(seconds: 23));
      expect(engine.playing, isTrue);
    },
  );

  test(
    'deferred queue exposes exact selection and progress without opening audio or fetching lyrics',
    () async {
      await importQueue();
      expect(player.sessionPending, isTrue);
      expect(player.current!.online!.id, '1');
      expect(player.queueIndex, 2);
      expect(player.position, const Duration(seconds: 23));
      expect(player.duration, Duration.zero);
      engine.notifyListeners();
      await player.pause();
      await player.importPlaybackPreferences(speed: 1.5);
      await Future<void>.delayed(Duration.zero);
      expect(engine.opens, 0);
      expect(engine.pauses, 0);
      expect(source.songs, isEmpty);
      expect(player.history, isEmpty);
      expect(player.snapshot.session!.index, 2);
    },
  );

  test(
    'stop resets imported progress without decoding and resume records history and fetches lyrics',
    () async {
      await importQueue();
      await player.stop();
      await player.flushed;
      expect(player.position, Duration.zero);
      expect(engine.opens, 0);
      expect(engine.seeks, 0);
      expect(source.songs, isEmpty);
      expect(LibraryData.decode(store.value!).session!.position, Duration.zero);
      await player.toggle();
      await Future<void>.delayed(Duration.zero);
      expect(player.sessionPending, isFalse);
      expect(engine.opens, 1);
      expect(engine.playing, isTrue);
      expect(player.queueIndex, 2);
      expect(player.history.first, songs[2].path);
      expect(source.songs, ['1']);
      expect(player.lyrics.single.text, 'Song 1');
    },
  );

  test(
    'next and previous resolve the intended imported occurrence with wraparound and zero progress',
    () async {
      await importQueue();
      await player.next();
      expect(engine.index, 0);
      expect(engine.position, Duration.zero);
      expect(engine.playing, isTrue);
      expect(engine.opens, 1);
      expect(engine.nextCalls, 0);
      await importQueue(index: 0);
      await player.previous();
      expect(engine.index, 2);
      expect(engine.position, Duration.zero);
      expect(engine.previousCalls, 0);
      await player.next();
      await player.previous();
      expect(engine.nextCalls, 1);
      expect(engine.previousCalls, 1);
    },
  );

  test(
    'single-repeat manual next advances while shuffle selects another queue occurrence',
    () async {
      await importQueue(index: 0);
      player.setRepeatMode(RepeatMode.single);
      await player.next();
      expect(engine.index, 1);
      await importQueue(index: 1);
      player.setRepeatMode(RepeatMode.shuffle);
      await player.next();
      expect(engine.index, isNot(1));
      expect(engine.playing, isTrue);
    },
  );

  test(
    'failed next retains the requested occurrence for a later explicit retry',
    () async {
      await importQueue();
      engine.failOpen = true;
      await player.next();
      await player.flushed;
      expect(player.sessionPending, isTrue);
      expect(player.queueIndex, 0);
      expect(player.position, Duration.zero);
      expect(player.error, isNotNull);
      expect(source.songs, isEmpty);
      expect(LibraryData.decode(store.value!).session!.index, 0);
      engine.failOpen = false;
      await player.toggle();
      expect(engine.index, 0);
      expect(engine.playing, isTrue);
      expect(player.sessionPending, isFalse);
    },
  );

  test(
    'seek updates and bounds deferred progress without contacting the engine',
    () async {
      await importQueue();
      await player.seek(const Duration(seconds: 45));
      expect(player.position, const Duration(seconds: 45));
      expect(engine.opens, 0);
      expect(engine.seeks, 0);
      await player.seek(const Duration(days: 20));
      expect(player.position, PlaybackSession.maxPosition);
      await player.seek(const Duration(seconds: -1));
      expect(player.position, Duration.zero);
      await player.seek(const Duration(seconds: 45));
      await player.toggle();
      expect(engine.position, const Duration(seconds: 45));
      await player.seek(const Duration(days: 1));
      expect(engine.position, engine.duration);
      await player.stop();
      expect(engine.position, Duration.zero);
      expect(engine.playing, isFalse);
    },
  );

  test(
    'toggle queued behind import examines state at execution instead of capturing an empty library',
    () async {
      final importing = importQueue();
      final playing = player.toggle();
      await Future.wait([importing, playing]);
      expect(engine.opens, 1);
      expect(engine.index, 2);
      expect(engine.position, const Duration(seconds: 23));
      expect(engine.playing, isTrue);
    },
  );

  test(
    'two toggles queued during a slow restore open only once and finish paused',
    () async {
      await importQueue();
      engine.opening = Completer<void>();
      final first = player.toggle();
      final second = player.toggle();
      await Future<void>.delayed(Duration.zero);
      engine.opening!.complete();
      await Future.wait([first, second]);
      expect(engine.opens, 1);
      expect(engine.playing, isFalse);
    },
  );

  test(
    'repeated explicit play requests during restoration remain playing',
    () async {
      await importQueue();
      engine.opening = Completer<void>();
      final first = player.resume();
      final second = player.resume();
      await Future<void>.delayed(Duration.zero);
      engine.opening!.complete();
      await Future.wait([first, second]);
      expect(engine.opens, 1);
      expect(engine.playing, isTrue);
      await player.pause();
      await player.resume();
      await player.resume();
      expect(engine.playing, isTrue);
      expect(engine.opens, 1);
    },
  );

  test(
    'current metadata follows the engine occurrence when duplicate identities have different labels',
    () async {
      final first = songs.first;
      final second = Track(
        path: first.path,
        title: 'Second occurrence',
        artist: 'Other',
        online: first.online,
      );
      await player.play([first, second], 1);
      expect(player.current!.title, 'Second occurrence');
      expect(player.queueIndex, 1);
    },
  );
}
