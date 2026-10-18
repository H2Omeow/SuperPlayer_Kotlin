import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as paths;
import 'package:nekoplayer_desktop/data/audio_downloader.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/download_controller.dart';

import 'fakes.dart';

class DownloadSource implements OnlineDownloadSource {
  int requests = 0;
  @override
  Future<ResolvedAudio> resolveDownload(OnlineSong song, String quality) async {
    requests++;
    return ResolvedAudio(
      Uri.https('audio.example', '/expires-$requests.mp3'),
      quality,
      format: 'mp3',
      size: 3,
    );
  }
}

class LyricsDownloadSource extends DownloadSource implements OnlineAudioSource {
  bool failLyrics = false;
  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) => resolveDownload(song, preferred);
  @override
  Future<String> lyrics(OnlineSong song) async {
    if (failLyrics) throw const MusicSearchException('lyrics unavailable');
    return '[00:01.00]Original\n[00:01.00]翻译';
  }
}

class TestDownloader extends AudioDownloader {
  int active = 0;
  int maximum = 0;
  bool fail = false;
  bool hold = false;
  @override
  Future<void> download({
    required ResolvedAudio audio,
    required File partial,
    required File destination,
    required DownloadCancellation cancellation,
    required void Function(int received, int? total) progress,
  }) async {
    active++;
    if (active > maximum) maximum = active;
    try {
      do {
        await Future<void>.delayed(const Duration(milliseconds: 5));
        cancellation.check();
      } while (hold);
      if (fail) throw const MusicSearchException('下载被拒绝');
      await destination.writeAsBytes([1, 2, 3]);
      progress(3, 3);
    } finally {
      active--;
    }
  }
}

Future<void> waitFor(bool Function() condition) async {
  final expires = DateTime.now().add(const Duration(seconds: 5));
  while (!condition()) {
    if (DateTime.now().isAfter(expires)) {
      throw StateError('download test timed out');
    }
    await Future<void>.delayed(const Duration(milliseconds: 5));
  }
}

void main() {
  late Directory directory;
  late MemoryStore store;
  late DownloadSource source;
  late TestDownloader downloader;
  late DownloadController controller;
  const song = OnlineSong(
    provider: MusicProvider.netease,
    id: '123',
    title: '夜晚/../test',
    artist: 'Neko',
  );

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('neko-manager-');
    store = MemoryStore();
    source = DownloadSource();
    downloader = TestDownloader();
    controller = DownloadController(
      source: source,
      store: store,
      downloader: downloader,
    );
    await controller.initialize();
  });
  tearDown(() async {
    controller.dispose();
    await controller.flushed;
    await waitFor(() => downloader.active == 0);
    await directory.delete(recursive: true);
  });

  test(
    'completed downloads include translated lyrics without persisting URLs',
    () async {
      controller.dispose();
      controller = DownloadController(
        source: LyricsDownloadSource(),
        store: store,
        downloader: downloader,
      );
      await controller.initialize();
      await controller.enqueue(song, directory.path, 'standard');
      await waitFor(
        () =>
            controller.tasks.single.lyricsOwned &&
            controller.tasks.single.path != null &&
            File(
              paths.setExtension(controller.tasks.single.path!, '.lrc'),
            ).existsSync(),
      );
      final task = controller.tasks.single;
      expect(task.phase, DownloadPhase.completed);
      expect(
        await File(paths.setExtension(task.path!, '.lrc')).readAsString(),
        contains('翻译'),
      );
      await controller.flushed;
      expect(store.value, isNot(contains('audio.example')));
    },
  );

  test(
    'lyrics failures do not lose completed audio or overwrite existing files',
    () async {
      controller.dispose();
      controller = DownloadController(
        source: LyricsDownloadSource(),
        store: store,
        downloader: downloader,
      );
      await controller.initialize();
      downloader.hold = true;
      await controller.enqueue(song, directory.path, 'standard');
      await waitFor(() => controller.tasks.single.path != null);
      final task = controller.tasks.single;
      final lyric = File(paths.setExtension(task.path!, '.lrc'));
      await lyric.writeAsString('original user lyrics');
      downloader.hold = false;
      await waitFor(() => task.error != null);
      expect(task.phase, DownloadPhase.completed);
      expect(await File(task.path!).exists(), isTrue);
      expect(await lyric.readAsString(), 'original user lyrics');
      await waitFor(() => !controller.isActive(task));
      await controller.remove(task, deleteFiles: true);
      expect(await File(task.path!).exists(), isFalse);
      expect(await lyric.readAsString(), 'original user lyrics');
    },
  );

  test(
    'explicit file deletion removes only task-owned audio and lyrics',
    () async {
      controller.dispose();
      controller = DownloadController(
        source: LyricsDownloadSource(),
        store: store,
        downloader: downloader,
      );
      await controller.initialize();
      await controller.enqueue(song, directory.path, 'standard');
      await waitFor(() => controller.tasks.single.lyricsOwned);
      final task = controller.tasks.single;
      final audio = File(task.path!);
      final lyric = File(paths.setExtension(task.path!, '.lrc'));
      await waitFor(() => !controller.isActive(task));
      await controller.remove(task, deleteFiles: true);
      expect(await audio.exists(), isFalse);
      expect(await lyric.exists(), isFalse);
      expect(controller.tasks, isEmpty);
    },
  );

  test(
    'queue is serial, deduplicates active tasks and never persists signed URLs',
    () async {
      downloader.hold = true;
      await controller.enqueue(song, directory.path, 'standard');
      await controller.enqueue(song, directory.path, 'standard');
      await controller.enqueue(
        const OnlineSong(
          provider: MusicProvider.netease,
          id: '456',
          title: '第二首',
          artist: 'Neko',
        ),
        directory.path,
        'lossless',
      );
      expect(controller.tasks, hasLength(2));
      downloader.hold = false;
      await waitFor(
        () => controller.tasks.every(
          (task) => task.phase == DownloadPhase.completed,
        ),
      );
      expect(downloader.maximum, 1);
      expect(source.requests, 2);
      await controller.flushed;
      expect(store.value, isNot(contains('https://')));
      expect(store.value, isNot(contains('expires-')));
      expect(
        controller.tasks.every(
          (task) => File(task.path!).parent.path == directory.path,
        ),
        isTrue,
      );
    },
  );

  test('pause and retry resolve a fresh download URL', () async {
    downloader.hold = true;
    await controller.enqueue(song, directory.path, 'standard');
    await waitFor(() => downloader.active == 1);
    final task = controller.tasks.single;
    controller.pause(task);
    await waitFor(() => downloader.active == 0);
    expect(task.phase, DownloadPhase.paused);
    downloader.hold = false;
    controller.resume(task);
    await waitFor(() => task.phase == DownloadPhase.completed);
    expect(source.requests, 2);
  });

  test(
    'failure can retry and clearing a completed record preserves the file',
    () async {
      downloader.fail = true;
      await controller.enqueue(song, directory.path, 'standard');
      await waitFor(
        () => controller.tasks.single.phase == DownloadPhase.failed,
      );
      final task = controller.tasks.single;
      expect(task.error, '下载被拒绝');
      downloader.fail = false;
      controller.resume(task);
      await waitFor(() => task.phase == DownloadPhase.completed);
      await controller.remove(task);
      expect(controller.tasks, isEmpty);
      expect(await File(task.path!).exists(), isTrue);
    },
  );

  test(
    'restored in-progress tasks remain paused until explicit resume',
    () async {
      final task = DownloadTask(
        id: 'test-123',
        song: song,
        directory: directory.path,
        quality: 'standard',
        phase: DownloadPhase.downloading,
      );
      final data = MemoryStore()
        ..value = jsonEncode({
          'version': 1,
          'tasks': [task.toJson()],
        });
      final restored = DownloadController(
        source: source,
        store: data,
        downloader: downloader,
      );
      await restored.initialize();
      expect(restored.tasks.single.phase, DownloadPhase.paused);
      expect(source.requests, 0);
      restored.dispose();
    },
  );

  test('damaged state is not overwritten by new tasks', () async {
    final data = MemoryStore()..value = 'broken';
    final restored = DownloadController(
      source: source,
      store: data,
      downloader: downloader,
    );
    await restored.initialize();
    await restored.enqueue(song, directory.path, 'standard');
    expect(restored.tasks, isEmpty);
    expect(restored.error, contains('已保护原数据'));
    expect(data.value, 'broken');
    restored.dispose();
  });

  test('filenames reject Windows reserved names and directory separators', () {
    expect(DownloadController.safeName('CON'), 'NekoPlayer-CON');
    expect(DownloadController.safeName('A/B\\C:*?<>|'), isNot(contains('/')));
    expect(DownloadController.safeName('夜晚. '), '夜晚');
  });
}
