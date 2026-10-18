import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/audio_cache.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

import 'fakes.dart';

const song = OnlineSong(
  provider: MusicProvider.netease,
  id: '1',
  title: 'Test',
  artist: 'Neko',
);

void main() {
  late Directory directory;
  late MemoryStore store;
  late AudioCache cache;
  late HttpServer server;
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('neko-cache-test-');
    store = MemoryStore();
    cache = AudioCache(directory: directory, store: store);
    await cache.initialize();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
  });
  tearDown(() async {
    cache.dispose();
    await cache.settled;
    await server.close(force: true);
    await directory.delete(recursive: true);
  });
  ResolvedAudio audio() => ResolvedAudio(
    Uri.parse('http://127.0.0.1:${server.port}/audio?secret=temporary'),
    'exhigh',
    size: 4,
  );

  test('cache is opt-in and persists metadata without signed URLs', () async {
    var requests = 0;
    server.listen((request) {
      requests++;
      request.response.headers.contentType = ContentType('audio', 'mpeg');
      request.response.add([1, 2, 3, 4]);
      unawaited(request.response.close());
    });
    cache.remember(song, audio());
    expect(requests, 0);
    expect(await cache.find(song, 'exhigh'), isNull);
    await cache.setEnabled(true);
    cache.remember(song, audio());
    await cache.settled;
    expect(requests, 1);
    expect(cache.entries, hasLength(1));
    expect(cache.totalBytes, 4);
    expect(store.value, isNot(contains('temporary')));
    expect(store.value, isNot(contains('127.0.0.1')));
    final resolved = await cache.find(song, 'exhigh');
    expect(resolved?.uri.scheme, 'file');
    final restored = AudioCache(directory: directory, store: store);
    await restored.initialize();
    expect(restored.enabled, isTrue);
    expect(restored.entries.single.song.title, 'Test');
    restored.dispose();
  });

  test(
    'selective deletion removes only selected indexed audio and lyrics',
    () async {
      server.listen((request) {
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.add([1, 2, 3, 4]);
        unawaited(request.response.close());
      });
      const second = OnlineSong(
        provider: MusicProvider.netease,
        id: '2',
        title: 'Second',
        artist: 'Neko',
      );
      await cache.setEnabled(true);
      cache.remember(song, audio());
      cache.rememberLyrics(song, '[00:00.00]First');
      await cache.settled;
      cache.remember(second, audio());
      cache.rememberLyrics(second, '[00:00.00]Second');
      await cache.settled;
      await cache.remove({AudioCache.filename(song, 'exhigh'), '../unrelated'});
      expect(cache.entries.single.song.id, '2');
      expect(await cache.lyrics(song), isNull);
      expect(await cache.lyrics(second), contains('Second'));
      expect(await cache.find(second, 'exhigh'), isNotNull);
    },
  );

  test(
    'offline lyrics survive restart and clear only owned indexed sidecars',
    () async {
      server.listen((request) {
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.add([1, 2, 3, 4]);
        unawaited(request.response.close());
      });
      await cache.setEnabled(true);
      cache.remember(song, audio());
      cache.rememberLyrics(song, '[00:00.00]Original\n[00:00.00]翻译');
      await cache.settled;
      expect(await cache.lyrics(song), contains('翻译'));
      final restored = AudioCache(directory: directory, store: store);
      await restored.initialize();
      expect(await restored.lyrics(song), contains('Original'));
      restored.dispose();
      final unrelated = File('${directory.path}/my-lyrics.lrc');
      await unrelated.writeAsString('keep');
      await cache.clear();
      expect(await cache.lyrics(song), isNull);
      expect(await unrelated.readAsString(), 'keep');
      expect(await directory.list().length, 1);
    },
  );

  test(
    'lyrics never overwrite symlink targets or store overlarge content',
    () async {
      server.listen((request) {
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.add([1, 2, 3, 4]);
        unawaited(request.response.close());
      });
      await cache.setEnabled(true);
      cache.remember(song, audio());
      await cache.settled;
      final name = cache.entries.single.file.replaceAll('.audio', '.lrc');
      final outside = File('${directory.path}/original.txt');
      await outside.writeAsString('keep');
      final link = Link('${directory.path}/$name');
      await link.create(outside.path);
      cache.rememberLyrics(song, '[00:00.00]replacement');
      await cache.settled;
      expect(await cache.lyrics(song), isNull);
      expect(await outside.readAsString(), 'keep');
      await link.delete();
      cache.rememberLyrics(song, '翻' * (1024 * 1024));
      await cache.settled;
      expect(await cache.lyrics(song), isNull);
      await cache.clear();
      expect(await outside.readAsString(), 'keep');
    },
    skip: Platform.isWindows ? 'Symlink test requires Unix permissions' : false,
  );

  test(
    'single cache task deduplicates and clearing never deletes unrelated files',
    () async {
      server.listen((request) {
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.add([1, 2, 3, 4]);
        unawaited(request.response.close());
      });
      await cache.setEnabled(true);
      cache.remember(song, audio());
      cache.remember(song, audio());
      await cache.settled;
      expect(cache.entries, hasLength(1));
      final unrelated = File('${directory.path}/my-music.mp3');
      await unrelated.writeAsString('keep');
      await cache.clear();
      expect(cache.entries, isEmpty);
      expect(await unrelated.readAsString(), 'keep');
    },
  );

  test('corrupt index and path traversal protect all original data', () async {
    cache.dispose();
    final encoded = jsonEncode({
      'version': 1,
      'enabled': true,
      'entries': [
        {'file': '../secret.mp3'},
      ],
    });
    store.value = encoded;
    cache = AudioCache(directory: directory, store: store);
    await cache.initialize();
    expect(cache.readable, isFalse);
    expect(cache.enabled, isFalse);
    await cache.setEnabled(true);
    await cache.clear();
    expect(store.value, encoded);
  });

  test(
    'disabling a task cancels and never advertises partial offline audio',
    () async {
      final begun = Completer<void>();
      server.listen((request) async {
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.add([1, 2]);
        await request.response.flush();
        begun.complete();
      });
      await cache.setEnabled(true);
      cache.remember(song, audio());
      await begun.future;
      await cache.setEnabled(false);
      expect(cache.entries, isEmpty);
      expect(cache.enabled, isFalse);
      expect(
        await directory
            .list()
            .where((file) => file.path.endsWith('.part'))
            .length,
        0,
      );
    },
  );
}
