import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/plugins/plugin_runtime.dart';
import 'package:nekoplayer_desktop/data/plugins/script_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/music_plugin.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

void main() {
  final library = Platform.environment['NEKO_PLUGIN_LIBRARY'];
  test(
    'native host runs HTTP, timers, storage, raw items and lossless resolution',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      server.listen((request) async {
        request.response.headers.contentType = ContentType.json;
        request.response.write(
          jsonEncode({
            'data': [
              {'id': '1', 'title': '测试', 'secret': 'raw-item'},
            ],
            'isEnd': true,
          }),
        );
        await request.response.close();
      });
      final script =
          '''
      const axios = require('axios');
      const storage = require('musicfree/storage');
      module.exports = {platform:'Fixture',
        async search(query, page, type) {
          const result = await axios.get('http://127.0.0.1:${server.port}/search', {params:{query,page,type}});
          await storage.setItem('count', (await storage.getItem('count') || 0) + 1);
          return result.data;
        },
        async getMediaSource(song, quality) {
          if(song.secret !== 'raw-item') throw new Error('Lost raw metadata');
          return {url:'https://example.test/audio', quality, headers:{Referer:'https://example.test/'}};
        },
        getLyric(){return {rawLrc:'[00:01]原文', translation:'[00:01]译文'}},
        count(){return storage.getItem('count')},
        race(){return Promise.race([
          new Promise(resolve => setTimeout(() => resolve('fast'), 5)),
          new Promise(resolve => setTimeout(() => resolve('slow'), 1000))
        ])}
      };
    ''';
      final runtime = NativePluginRuntime(
        libraryPath: library,
        bundle: () => File('assets/music_plugins/runtime.js').readAsString(),
      );
      final url = Uri.parse('https://example.test/plugin.js');
      final source = MusicPluginSource.fromScript(
        await runtime.inspect(script, url),
        url,
        script,
      );
      final catalog = ScriptMusicCatalog(runtime);
      final page = await catalog.search(source, '测试', 1);
      expect(page.hasMore, isFalse);
      final song = OnlineSong.fromJson(page.songs.single.toJson());
      expect(song.pluginSource!.script, isEmpty);
      expect(await runtime.invoke(source, 'count', []), 1);
      final audio = await catalog.resolve(source, song, 'lossless');
      expect(audio.quality, 'high');
      expect(audio.format, 'flac');
      expect(audio.headers['Referer'], 'https://example.test/');
      final lyrics = parseLrc(await catalog.lyrics(source, song));
      expect(lyrics.single.text, '原文');
      expect(lyrics.single.translation, '译文');
      final clock = Stopwatch()..start();
      expect(await runtime.invoke(source, 'race', []), 'fast');
      expect(clock.elapsed, lessThan(const Duration(milliseconds: 900)));

      const lxScript = '''
        lx.on(lx.EVENT_NAMES.request, async ({action,info}) => action === 'lyric'
          ? {lyric:'[00:01]洛雪歌词', tlyric:'[00:01]LX lyrics'}
          : 'https://example.test/' + info.type);
        lx.send(lx.EVENT_NAMES.inited, {sources:{wy:{type:'music',
          actions:['musicUrl','lyric'], qualitys:['320k','flac']}}});
      ''';
      final lxUrl = Uri.parse('https://example.test/lx.js');
      final lx = MusicPluginSource.fromScript(
        await runtime.inspect(lxScript, lxUrl, format: 'lx'),
        lxUrl,
        lxScript,
      );
      expect(catalog.lxQualities(lx, 'wy').map((quality) => quality.id), [
        '320k',
        'flac',
      ]);
      expect(
        (await catalog.resolveLx(lx, 'wy', song, 'standard')).quality,
        '320k',
      );
      await expectLater(
        catalog.resolveLx(lx, 'wy', song, '128k', exact: true),
        throwsA(isA<AudioUnavailableException>()),
      );
      final lxLyric = parseLrc((await catalog.lxLyrics(lx, 'wy', song))!);
      expect(lxLyric.single.text, '洛雪歌词');
      expect(lxLyric.single.translation, 'LX lyrics');
    },
    skip: library == null
        ? 'Set NEKO_PLUGIN_LIBRARY to the built native runtime'
        : false,
  );
}
