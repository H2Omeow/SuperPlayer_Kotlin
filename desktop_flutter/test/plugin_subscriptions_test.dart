import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/library_store.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/data/plugin_subscriptions.dart';
import 'package:nekoplayer_desktop/data/plugins/plugin_runtime.dart';
import 'package:nekoplayer_desktop/data/plugins/plugin_integrity.dart';
import 'package:nekoplayer_desktop/domain/music_plugin.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

class MemoryStore implements LibraryStore {
  String? value;
  bool fail = false;
  @override
  Future<String?> read() async => value;
  @override
  Future<void> write(String next) async {
    if (fail) throw const FileSystemException('storage unavailable');
    value = next;
  }
}

class FixtureRuntime implements PluginScriptRuntime {
  @override
  Future<Map<String, dynamic>> inspect(
    String script,
    Uri url, {
    String? format,
  }) async => {
    'format': format ?? 'baka',
    'platform': 'Fixture',
    'capabilities': ['search', 'getMediaSource'],
  };
  @override
  Future<Object?> invoke(
    MusicPluginSource source,
    String method,
    List<Object?> args,
  ) async => null;
}

Future<String> bundled() =>
    File('assets/music_plugins/animemusic.json').readAsString();

void main() {
  test('script checksums accept hex and base64 and reject modifications', () {
    const digest = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad';
    verifyPluginChecksum('abc', digest);
    verifyPluginChecksum('abc', 'sha256-ungWv48Bz+pBQUDeXa4iI7ADYaOWF3qctBD/YfIAFa0=');
    expect(() => verifyPluginChecksum('modified', digest), throwsFormatException);
    expect(() => verifyPluginChecksum('abc', 123), throwsFormatException);
  });

  test(
    'local native scripts and JSON URL arrays round trip without executing native metadata',
    () async {
      final store = MemoryStore();
      final subscriptions = PluginSubscriptions(
        store: store,
        bundled: bundled,
        runtime: FixtureRuntime(),
        loader: (_) async => 'module.exports={platform:"Fixture"}',
      );
      addTearDown(subscriptions.dispose);
      await subscriptions.initialize();
      final meta = {
        'format': 'animemusic/1',
        'platform': 'all',
        'platforms': ['kg', 'wy'],
        'name': '聚合测试',
        'api': 'https://example.test/v1',
        'capabilities': ['search'],
      };
      final local = await subscriptions.previewText(
        'const META = ${jsonEncode(meta)};',
        Uri.file('/tmp/plugin.js'),
      );
      await subscriptions.save(local);
      final source = local.sources.single;
      expect(source.supportedPlatforms, ['kg', 'wy']);
      expect(source.api.path, '/v1/index.php');
      final requests = <String>[];
      final catalog = NativeMusicCatalog(
        subscriptions: subscriptions,
        transport: (request) async {
          final platform = request.uri.queryParameters['platform']!;
          requests.add(platform);
          return {
            'code': 200,
            'data': [
              {'id': '1', 'title': platform},
            ],
            'isEnd': true,
          };
        },
      );
      final songs = (await catalog.searchPlugin(source, '歌', 1)).songs;
      expect(requests, ['kg', 'wy']);
      expect(songs.map((song) => song.key).toSet(), hasLength(2));
      final external = await subscriptions.previewText(
        '["https://example.test/a.js"]',
        Uri.parse('https://example.test/sub.json'),
      );
      expect(external.sources.single.format, 'baka');
      final restored = PluginSubscriptions(
        store: store,
        bundled: bundled,
        runtime: FixtureRuntime(),
      );
      addTearDown(restored.dispose);
      await restored.initialize();
      expect(restored.readable, isTrue);
      expect(restored.sources.last.supportedPlatforms, ['kg', 'wy']);
    },
  );

  test(
    'updates preserve variables; failed persistence preserves active state',
    () async {
      final store = MemoryStore();
      final subscriptions = PluginSubscriptions(
        store: store,
        bundled: bundled,
        runtime: FixtureRuntime(),
      );
      addTearDown(subscriptions.dispose);
      await subscriptions.initialize();
      final url = Uri.parse('https://example.test/plugin.js');
      final imported = await subscriptions.previewText(
        'module.exports={platform:"Fixture"}',
        url,
      );
      await subscriptions.save(imported);
      await subscriptions.configure(imported.sources.single, {
        'token': 'configured',
      });
      await subscriptions.save(imported);
      expect(subscriptions.sources.last.variables, {'token': 'configured'});
      store.fail = true;
      await expectLater(
        subscriptions.setEnabled(imported, false),
        throwsA(isA<FileSystemException>()),
      );
      expect(subscriptions.sources.last.id, imported.sources.single.id);
      store.fail = false;
      await subscriptions.setEnabled(imported, false);
      final catalog = NativeMusicCatalog(subscriptions: subscriptions);
      expect(
        () => catalog.searchPlugin(imported.sources.single, '歌', 1),
        throwsA(isA<MusicSearchException>()),
      );
    },
  );

  test(
    'corrupt persistence remains untouched and subscriptions cannot be written',
    () async {
      final store = MemoryStore()..value = '{broken';
      final subscriptions = PluginSubscriptions(
        store: store,
        bundled: bundled,
        runtime: FixtureRuntime(),
      );
      addTearDown(subscriptions.dispose);
      await subscriptions.initialize();
      expect(subscriptions.readable, isFalse);
      expect(subscriptions.sources, hasLength(7));
      await expectLater(
        subscriptions.setEnabled(subscriptions.subscriptions.single, false),
        throwsStateError,
      );
      expect(store.value, '{broken');
      expect(
        () => subscriptionUri('file://remote/plugin.js'),
        throwsFormatException,
      );
      expect(
        () => subscriptionUri('https://user:password@example.test/plugin.js'),
        throwsFormatException,
      );
    },
  );
}
