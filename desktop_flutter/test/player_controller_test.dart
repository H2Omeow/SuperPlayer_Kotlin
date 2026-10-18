import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/player_controller.dart';
import 'package:nekoplayer_desktop/playback/playback_engine.dart';

import 'fakes.dart';

void main() {
  late Directory directory;
  late FakeEngine engine;
  late MemoryStore store;
  late PlayerController controller;

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('nekoplayer-test-');
    engine = FakeEngine();
    store = MemoryStore();
    controller = PlayerController(engine: engine, store: store);
    await controller.initialize();
  });

  tearDown(() async {
    await controller.flushed;
    controller.dispose();
    await directory.delete(recursive: true);
  });

  Future<File> song([String name = 'Neko - 夜晚.FLAC']) =>
      File('${directory.path}/$name').writeAsString('fake-audio');

  test(
    'queue append and reorder preserve playback, removal and final clear work',
    () async {
      final first = await song('First.mp3');
      final second = await song('Second.mp3');
      final third = await song('Third.mp3');
      await controller.importFiles([first.path, second.path, third.path]);
      await controller.play(controller.tracks.take(2).toList(), 1);
      await engine.seek(const Duration(seconds: 23));
      await engine.pause();
      await controller.enqueue(controller.tracks[2]);
      expect(controller.queue.map((track) => track.path), [
        first.path,
        second.path,
        third.path,
      ]);
      expect(engine.currentPath, second.path);
      expect(engine.playing, isFalse);
      await controller.moveInQueue(1, 2);
      expect(engine.index, 2);
      expect(engine.currentPath, second.path);
      expect(engine.position, const Duration(seconds: 23));
      await controller.removeFromQueue(0);
      expect(engine.index, 1);
      expect(engine.currentPath, second.path);
      await controller.removeFromQueue(1);
      expect(controller.queue.single.path, third.path);
      expect(engine.currentPath, third.path);
      await controller.removeFromQueue(0);
      expect(controller.queue, isEmpty);
      expect(controller.current, isNull);
      expect(engine.playing, isFalse);
      await controller.enqueue(controller.tracks.first);
      expect(engine.currentPath, first.path);
      expect(engine.playing, isTrue);
    },
  );

  test(
    'restores duplicate queue entries and position without autoplay',
    () async {
      final first = await song('First.mp3');
      final second = await song('Second.mp3');
      await controller.importFiles([first.path, second.path]);
      await controller.play([
        controller.tracks[0],
        controller.tracks[1],
        controller.tracks[0],
      ], 2);
      await engine.seek(const Duration(seconds: 37));
      controller.setRepeatMode(RepeatMode.single);
      await controller.perform(() async {});
      controller.setVolume(52);
      await controller.flushed;
      final restoredEngine = FakeEngine();
      final restored = PlayerController(engine: restoredEngine, store: store);
      await restored.initialize();
      expect(restored.queue.map((track) => track.path), [
        first.path,
        second.path,
        first.path,
      ]);
      expect(restoredEngine.index, 2);
      expect(restoredEngine.position, const Duration(seconds: 37));
      expect(restoredEngine.playing, isFalse);
      expect(restored.repeat, RepeatMode.single);
      await restored.toggle();
      expect(restoredEngine.playing, isTrue);
      restored.dispose();
      await restored.flushed;
    },
  );

  test(
    'failed restore preserves saved session for an explicit retry',
    () async {
      final audio = await song();
      await controller.importFiles([audio.path]);
      await controller.play(controller.tracks, 0);
      controller.setRate(1.25);
      await controller.flushed;
      final restoredEngine = FakeEngine()..failOpen = true;
      final restored = PlayerController(engine: restoredEngine, store: store);
      await restored.initialize();
      expect(restored.error, isNotNull);
      expect(restored.snapshot.session!.paths, [audio.path]);
      restoredEngine.failOpen = false;
      await restored.toggle();
      expect(restoredEngine.currentPath, audio.path);
      expect(restoredEngine.playing, isTrue);
      await restored.clearQueue();
      await restored.flushed;
      expect(LibraryData.decode(store.value!).session, isNull);
      restored.dispose();
      await restored.flushed;
    },
  );

  test(
    'import validates files, deduplicates and parses filename labels',
    () async {
      final audio = await song();
      final text = await song('not-music.txt');
      await controller.importFiles([
        audio.path,
        audio.path,
        text.path,
        '${directory.path}/missing.mp3',
      ]);
      expect(controller.tracks, hasLength(1));
      expect(controller.tracks.single.title, '夜晚');
      expect(controller.tracks.single.artist, 'Neko');
    },
  );

  test(
    'clearing queue stops playback without removing library or favorites',
    () async {
      final audio = await song();
      await controller.importFiles([audio.path]);
      controller.toggleFavorite(controller.tracks.single);
      await controller.play(controller.tracks, 0);
      await controller.clearQueue();
      expect(controller.queue, isEmpty);
      expect(controller.current, isNull);
      expect(engine.playing, isFalse);
      expect(controller.tracks.single.path, audio.path);
      expect(controller.favorites, contains(audio.path));
    },
  );

  test(
    'play records history and persisted favorites can be restored',
    () async {
      final audio = await song();
      await controller.importFiles([audio.path]);
      await controller.play(controller.tracks, 0);
      controller.toggleFavorite(controller.tracks.single);
      await controller.flushed;
      final restored = LibraryData.decode(store.value!);
      expect(engine.playing, isTrue);
      expect(restored.history, [audio.path]);
      expect(restored.favorites, {audio.path});
    },
  );

  test('playlist membership is unique and can be removed', () async {
    final audio = await song();
    await controller.importFiles([audio.path]);
    controller.createPlaylist('夜晚');
    final id = controller.playlists.single.id;
    controller.addToPlaylist(id, controller.tracks.single);
    controller.addToPlaylist(id, controller.tracks.single);
    expect(controller.playlists.single.paths, hasLength(1));
    controller.removeFromPlaylist(id, controller.tracks.single);
    expect(controller.playlists.single.paths, isEmpty);
    controller.deletePlaylist(id);
    expect(controller.playlists, isEmpty);
  });

  test('queued writes preserve newest state', () async {
    controller.setVolume(10);
    controller.setVolume(20);
    controller.setVolume(90);
    await controller.flushed;
    expect(LibraryData.decode(store.value!).volume, 90);
  });

  test(
    'save failure is reported without uncaught asynchronous errors',
    () async {
      store.failWrite = true;
      controller.createPlaylist('One');
      await controller.flushed;
      expect(controller.error, contains('保存音乐库失败'));
    },
  );

  test('corrupt library is retained and cannot be overwritten', () async {
    final brokenStore = MemoryStore()..value = 'not-json';
    final other = PlayerController(engine: FakeEngine(), store: brokenStore);
    await other.initialize();
    other.createPlaylist('New');
    await other.flushed;
    expect(other.error, contains('已保留原数据'));
    expect(brokenStore.value, 'not-json');
    expect(brokenStore.writes, 0);
    other.dispose();
  });

  test('missing file is not sent to playback engine', () async {
    final audio = await song();
    await controller.importFiles([audio.path]);
    await audio.delete();
    await controller.play(controller.tracks, 0);
    expect(engine.playing, isFalse);
    expect(controller.error, contains('文件已移动或删除'));
  });

  test('engine failures do not break subsequent commands', () async {
    final audio = await song();
    await controller.importFiles([audio.path]);
    engine.failOpen = true;
    await controller.play(controller.tracks, 0);
    expect(controller.error, isNotNull);
    engine.failOpen = false;
    await controller.play(controller.tracks, 0);
    expect(engine.playing, isTrue);
  });

  testWidgets('sleep timer pauses and can be cancelled', (tester) async {
    final timedEngine = FakeEngine()..playing = true;
    final timedController = PlayerController(
      engine: timedEngine,
      store: MemoryStore(),
    );
    timedController.setSleepTimer(const Duration(minutes: 15));
    await tester.pump(const Duration(minutes: 15));
    await tester.pump();
    expect(timedEngine.playing, isFalse);
    expect(timedController.sleepDeadline, isNull);
    timedEngine.playing = true;
    timedController.setSleepTimer(const Duration(minutes: 15));
    timedController.setSleepTimer(null);
    await tester.pump(const Duration(minutes: 16));
    expect(timedEngine.playing, isTrue);
    timedController.dispose();
  });
}
