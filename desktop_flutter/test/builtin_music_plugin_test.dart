import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/builtin_music_plugin.dart';
import 'package:nekoplayer_desktop/data/cloud_library.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

const song = OnlineSong(
  provider: MusicProvider.gatewayTencent,
  id: 'AbC123',
  title: '夜晚',
  artist: 'Neko',
);

void main() {
  test('invalid endpoints and route overrides never reach transport', () async {
    var calls = 0;
    final plugin = BuiltinMusicPlugin(
      transport: (_) async {
        calls++;
        return {'code': 200};
      },
    );
    for (final endpoint in [
      '../other',
      'usage',
      'session',
      'music/../search',
    ]) {
      await expectLater(
        plugin.request(endpoint, {}),
        throwsA(isA<MusicSearchException>()),
      );
    }
    for (final query in [
      {'route': 'music/url', 'platform': 'tx'},
      {'platform': 'unknown'},
      {'source': 'unknown'},
    ]) {
      await expectLater(
        plugin.request('music/search', query),
        throwsA(isA<MusicSearchException>()),
      );
    }
    expect(calls, 0);
  });

  test('concurrent requests use HTTPS REST with intact query values', () async {
    final requests = <CatalogRequest>[];
    final plugin = BuiltinMusicPlugin(
      transport: (request) async {
        requests.add(request);
        expect(request.uri.scheme, 'https');
        expect(request.uri.host, 'animemusic.bzxhkj.com');
        expect(request.uri.hasQuery, isTrue);
        expect(request.uri.queryParameters.containsKey('route'), isFalse);
        expect(request.body, isNull);
        expect(request.captureCookies, isFalse);
        expect(request.headers, {
          'Accept': 'application/json',
          'User-Agent': 'NekoPlayer/animemusic-1',
        });
        return {'code': 200, 'data': []};
      },
    );
    await Future.wait([
      plugin.request('music/search', {
        'platform': 'tx',
        'keyword': '夜晚 & + /',
        'page': '1',
      }),
      plugin.request('music/lyric', {'platform': 'tx', 'musicId': 'AbC/+'}),
    ]);
    expect(requests.map((request) => request.uri.path), [
      '/v1/index.php/music/search',
      '/v1/index.php/music/lyric',
    ]);
    expect(requests.first.uri.queryParameters['keyword'], '夜晚 & + /');
    expect(requests.last.uri.queryParameters['musicId'], 'AbC/+');
  });

  for (final failure in ['HTTP', 'JSON']) {
    test('$failure 404 remembers the fallback query route', () async {
      final requests = <CatalogRequest>[];
      final plugin = BuiltinMusicPlugin(
        transport: (request) async {
          requests.add(request);
          if (requests.length == 1) {
            if (failure == 'HTTP') throw MusicHttpException(404);
            return {'code': 404};
          }
          expect(request.uri.path, '/v1/index.php');
          expect(request.uri.scheme, 'https');
          return {'code': 200, 'data': []};
        },
      );
      final query = {'platform': 'tx', 'keyword': '夜晚 & + /', 'page': '2'};
      await plugin.request('music/search', query);
      await plugin.request('music/lyric', {'platform': 'tx', 'musicId': 'AbC'});
      expect(requests, hasLength(3));
      expect(requests.first.uri.path, '/v1/index.php/music/search');
      expect(requests.first.uri.queryParameters, query);
      expect(requests[1].uri.queryParameters, {
        'route': 'music/search',
        ...query,
      });
      expect(requests.last.uri.queryParameters, {
        'route': 'music/lyric',
        'platform': 'tx',
        'musicId': 'AbC',
      });
    });

    test('$failure errors other than 404 never retry another route', () async {
      for (final status in [401, 403, 429, 500]) {
        var calls = 0;
        final plugin = BuiltinMusicPlugin(
          transport: (_) async {
            calls++;
            if (failure == 'HTTP') throw MusicHttpException(status);
            return {'code': status};
          },
        );
        final result = plugin.request('music/search', {'platform': 'tx'});
        if (failure == 'JSON' && status == 500) {
          expect((await result)['code'], status);
        } else {
          await expectLater(result, throwsA(isA<MusicSearchException>()));
        }
        expect(calls, 1, reason: '$failure $status');
      }
    });
  }

  test('a query route returning 404 is not retried again', () async {
    final requests = <CatalogRequest>[];
    final plugin = BuiltinMusicPlugin(
      transport: (request) async {
        requests.add(request);
        throw MusicHttpException(404);
      },
    );
    await expectLater(
      plugin.request('music/search', {'platform': 'tx'}),
      throwsA(isA<MusicHttpException>()),
    );
    expect(requests.map((request) => request.uri.path), [
      '/v1/index.php/music/search',
      '/v1/index.php',
    ]);
  });

  test('transport and malformed responses never change routes', () async {
    for (final failure in [
      TimeoutException('timeout'),
      const SocketException('offline'),
      const HandshakeException('invalid certificate'),
      const FormatException('invalid response'),
    ]) {
      var calls = 0;
      final plugin = BuiltinMusicPlugin(
        transport: (_) async {
          calls++;
          throw failure;
        },
      );
      await expectLater(
        plugin.request('music/search', {'platform': 'tx'}),
        throwsA(isA<MusicSearchException>()),
      );
      expect(calls, 1);
    }
    var calls = 0;
    final plugin = BuiltinMusicPlugin(
      transport: (_) async {
        calls++;
        return {'data': []};
      },
    );
    await expectLater(
      plugin.request('music/search', {'platform': 'tx'}),
      throwsA(isA<MusicSearchException>()),
    );
    expect(calls, 1);
  });

  test('catalog searches without login or forwarded credentials', () async {
    final accounts = AccountStore();
    addTearDown(accounts.dispose);
    final requests = <CatalogRequest>[];
    final catalog = NativeMusicCatalog(
      accounts: accounts,
      musicPlugin: BuiltinMusicPlugin(
        transport: (request) async {
          requests.add(request);
          return {
            'code': 200,
            'isEnd': true,
            'data': [
              {'id': song.id, 'title': song.title, 'artist': song.artist},
            ],
          };
        },
      ),
    );
    expect(accounts.siteToken, isEmpty);
    expect(
      (await catalog.search(song.provider, song.title, 1)).songs.single.key,
      song.key,
    );
    await accounts.update(
      siteToken: 'site-token-valid-123',
      netease: {'MUSIC_U': 'netease-secret'},
      kugou: {'token': 'kugou-secret', 'userid': '123'},
    );
    await catalog.search(song.provider, song.title, 1);
    expect(requests, hasLength(2));
    for (final request in requests) {
      expect(request.uri, requests.first.uri);
      expect(request.headers, {
        'Accept': 'application/json',
        'User-Agent': 'NekoPlayer/animemusic-1',
      });
      expect(request.uri.queryParameters, {
        'platform': 'tx',
        'keyword': song.title,
        'page': '1',
        'limit': '${NativeMusicCatalog.pageSize}',
      });
      expect(request.body, isNull);
    }
  });

  test('catalog preserves identity and maps 192k and 24-bit audio', () async {
    final qualities = <String>[];
    final catalog = NativeMusicCatalog(
      musicPlugin: BuiltinMusicPlugin(
        transport: (request) async {
          final query = request.uri.queryParameters;
          if (request.uri.path == '/v1/index.php/music/search') {
            expect(query['platform'], 'tx');
            return {
              'code': 200,
              'isEnd': false,
              'data': [
                {'id': song.id, 'title': song.title, 'artist': song.artist},
              ],
            };
          }
          expect(request.uri.path, '/v1/index.php/music/url');
          expect(query['source'], 'tx');
          expect(query['musicId'], song.id);
          final quality = query['quality']!;
          qualities.add(quality);
          return {
            'code': 200,
            'url': 'https://audio.example/song.flac',
            'quality': quality,
            'format': 'flac',
          };
        },
      ),
    );
    final result = await catalog.search(song.provider, song.title, 1);
    final found = result.songs.single;
    expect(result.hasMore, isTrue);
    expect(found.key, 'animemusic-tx:AbC123');
    expect(
      (await catalog.resolve(found, preferred: 'lossless')).quality,
      'flac',
    );
    expect((await catalog.resolveDownload(found, 'higher')).quality, '192k');
    final audio = await catalog.resolveDownload(found, 'hires');
    expect(audio.quality, 'hires');
    expect(audio.format, 'flac');
    expect(qualities, ['flac', '192k', 'flac24bit']);
    final track = CloudLibrary.decodeSong({
      'id': 123,
      'nm': song.title,
      'ar': song.artist,
      'source': 'animemusic-tx',
      'providerMediaId': song.id,
    });
    expect(track!.online!.id, song.id);
    expect(CloudLibrary.songKey(CloudLibrary.encodeSong(track)), found.key);
  });

  test('catalog retains word timing and lyric translation', () async {
    final requests = <CatalogRequest>[];
    final catalog = NativeMusicCatalog(
      musicPlugin: BuiltinMusicPlugin(
        transport: (request) async {
          requests.add(request);
          expect(request.uri.path, '/v1/index.php/music/lyric/word');
          expect(request.uri.queryParameters, {
            'platform': 'tx',
            'musicId': song.id,
            'name': song.title,
            'interval': '200',
          });
          return {
            'code': 200,
            'lyric': '[1000,1000](1000,400,0)夜(1400,600,0)晚',
            'tlyric': '[00:01.00]Night',
          };
        },
      ),
    );
    final line = parseLrc(await catalog.lyrics(song)).single;
    expect(requests, hasLength(1));
    expect(line.text, '夜晚');
    expect(line.translation, 'Night');
    expect(line.words.map((word) => word.text), ['夜', '晚']);
    expect(line.words.map((word) => word.time.inMilliseconds), [1000, 1400]);
    expect(line.words.map((word) => word.duration.inMilliseconds), [400, 600]);
  });

  test(
    'Bilibili lyric matching tries another source after a failure',
    () async {
      final platforms = <String>[];
      final plugin = BuiltinMusicPlugin(
        transport: (request) async {
          final platform = request.uri.queryParameters['platform']!;
          platforms.add(platform);
          if (platform == 'kw') throw const SocketException('offline');
          if (request.uri.path.endsWith('/search')) {
            return {
              'code': 200,
              'data': [
                {'id': 'matched', 'title': song.title},
              ],
            };
          }
          return {'code': 200, 'lyric': '[00:01]匹配歌词'};
        },
      );
      final result = await plugin.lyrics(
        OnlineSong(
          provider: MusicProvider.gatewayBilibili,
          id: 'BV123',
          title: song.title,
          artist: song.artist,
        ),
      );
      expect(result['lyric'], '[00:01]匹配歌词');
      expect(platforms, ['kw', 'wy', 'wy']);
    },
  );

  for (final failure in ['empty', 'unavailable', 'HTTP']) {
    test('$failure word lyrics fall back to line lyrics', () async {
      final requests = <CatalogRequest>[];
      final plugin = BuiltinMusicPlugin(
        transport: (request) async {
          requests.add(request);
          if (request.uri.path.endsWith('/word')) {
            if (failure == 'HTTP') throw MusicHttpException(500);
            return {'code': failure == 'empty' ? 200 : 500, 'lyric': '  '};
          }
          expect(request.uri.path, '/v1/index.php/music/lyric');
          expect(
            request.uri.queryParameters,
            requests.first.uri.queryParameters,
          );
          return {'code': 200, 'lyric': '[00:01.00]夜晚'};
        },
      );
      expect((await plugin.lyrics(song))['lyric'], '[00:01.00]夜晚');
      expect(requests, hasLength(2));
    });
  }
}
