import 'dart:convert';
import 'dart:io';

import 'package:encrypt/encrypt.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/builtin_music_plugin.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

Map<String, dynamic> payload(CatalogRequest request) {
  final encoded = Uri.splitQueryString(request.body!)['params']!;
  final bytes = [
    for (var offset = 0; offset < encoded.length; offset += 2)
      int.parse(encoded.substring(offset, offset + 2), radix: 16),
  ];
  final clear = Encrypter(
    AES(Key.fromUtf8('e82ckenh8dichen8'), mode: AESMode.ecb),
  ).decrypt(Encrypted.fromBase64(base64Encode(bytes)));
  return jsonDecode(clear.split('-36cd479b6b5-')[1]) as Map<String, dynamic>;
}

const song = OnlineSong(
  provider: MusicProvider.netease,
  id: '123',
  title: 'Song',
  artist: 'Neko',
);
const pluginSong = OnlineSong(
  provider: MusicProvider.gatewayTencent,
  id: 'AbC',
  title: 'Song',
  artist: 'Neko',
);

Map<String, dynamic> discovery() => {
  'code': 200,
  'data': {
    'l': {'size': 1},
    'h': {'size': 2},
    'sq': {'size': 3},
    'hr': {'size': 4},
  },
};

void main() {
  test('quality labels cover the exact native quality catalogs', () {
    expect(
      AudioQuality.supported(
        MusicProvider.netease,
      ).map((quality) => quality.id),
      NativeMusicCatalog.levels,
    );
    expect(
      AudioQuality.supported(MusicProvider.kugou).map((quality) => quality.id),
      NativeMusicCatalog.kgLevels,
    );
    expect(
      AudioQuality.supported(
        MusicProvider.gatewayTencent,
      ).map((quality) => quality.id),
      ['128k', '192k', '320k', 'flac', 'hires'],
    );
  });

  test(
    'download permissions are discovered once and exclude trials and silent downgrades',
    () async {
      var discoveries = 0;
      final probes = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          final body = payload(request);
          if (request.uri.path.contains('music/detail')) {
            discoveries++;
            return discovery();
          }
          expect(request.uri.path, contains('/download/url/'));
          expect(body['id'], '123');
          expect(body.containsKey('ids'), isFalse);
          final quality = body['level'] as String;
          probes.add(quality);
          return {
            'code': 200,
            'data': {
              'code': 200,
              'url': 'https://audio.example/$quality',
              'level': quality == 'lossless' ? 'standard' : quality,
              'size': quality == 'hires' ? 10000 : 100,
              if (quality == 'exhigh') 'freeTrialInfo': {'end': 30},
            },
          };
        },
      );
      final qualities = await catalog.downloadable(song);
      expect(discoveries, 1);
      expect(probes, ['standard', 'exhigh', 'lossless', 'hires']);
      expect(qualities.map((quality) => quality.id), ['hires', 'standard']);
      expect(qualities.first.size, 10000);
      expect(qualities.first.label, 'Hi-Res');
    },
  );

  test(
    'unavailable formats produce an empty list rather than guessed permissions',
    () async {
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('music/detail')) return discovery();
          return {
            'code': 200,
            'data': {'code': 404, 'url': null},
          };
        },
      );
      expect(await catalog.downloadable(song), isEmpty);
    },
  );

  test(
    'network errors and permission rejection abort discovery without more requests',
    () async {
      for (final failure in ['network', 'permission']) {
        var probes = 0;
        final catalog = NativeMusicCatalog(
          initializeSessions: false,
          transport: (request) async {
            if (request.uri.path.contains('music/detail')) return discovery();
            probes++;
            if (failure == 'network') throw const SocketException('offline');
            return {'code': 429};
          },
        );
        await expectLater(
          catalog.downloadable(song),
          throwsA(isA<MusicSearchException>()),
        );
        expect(probes, 1);
      }
    },
  );

  test(
    'Kugou download discovery uses exact per-format hashes and rejects trial and downgraded streams',
    () async {
      const hash = '0123456789abcdef0123456789abcdef';
      const alternate = 'abcdef0123456789abcdef0123456789';
      var discoveries = 0;
      final probes = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('privilege')) {
            discoveries++;
            expect(jsonDecode(request.body!)['behavior'], 'download');
            return {
              'status': 1,
              'data': [
                {
                  'quality': '128',
                  'hash': hash,
                  'is_publish': 1,
                  'info': {'filesize': 100},
                  'relate_goods': [
                    {
                      'quality': '320',
                      'hash': hash,
                      'is_publish': 1,
                      'info': {'filesize': 200},
                    },
                    {
                      'quality': 'flac',
                      'hash': alternate,
                      'is_publish': 1,
                      'info': {'filesize': 300},
                    },
                    {
                      'quality': 'high',
                      'hash': hash,
                      'is_publish': 1,
                      'info': {'filesize': 400},
                    },
                  ],
                },
              ],
            };
          }
          final query = request.uri.queryParameters;
          final quality = query['quality']!;
          probes.add(quality);
          expect(query['behavior'], 'download');
          if (quality == 'flac') expect(query['hash'], alternate);
          return {
            'status': 1,
            'data': {
              'quality': quality == 'high' ? '128' : quality,
              'url': ['https://audio.example/$quality'],
              'fileSize': 300,
              'is_free_part': quality == '320' ? 1 : 0,
            },
          };
        },
      );
      final qualities = await catalog.downloadable(
        const OnlineSong(
          provider: MusicProvider.kugou,
          id: '42',
          title: 'Song',
          artist: 'Neko',
          hash: hash,
        ),
      );
      expect(discoveries, 1);
      expect(probes, ['128', '320', 'flac', 'high']);
      expect(qualities.map((quality) => quality.id), ['flac', '128']);
      expect(qualities.first.size, 300);
    },
  );

  test('Kugou device verification stops all further download probes', () async {
    const hash = '0123456789abcdef0123456789abcdef';
    var probes = 0;
    final catalog = NativeMusicCatalog(
      initializeSessions: false,
      transport: (request) async {
        if (request.uri.path.contains('privilege')) {
          return {
            'status': 1,
            'data': [
              {
                'quality': '128',
                'hash': hash,
                'is_publish': 1,
                'info': {'filesize': 100},
                'relate_goods': [
                  {
                    'quality': '320',
                    'hash': hash,
                    'is_publish': 1,
                    'info': {'filesize': 200},
                  },
                ],
              },
            ],
          };
        }
        probes++;
        return {'status': 0, 'errcode': 20028};
      },
    );
    await expectLater(
      catalog.downloadable(
        const OnlineSong(
          provider: MusicProvider.kugou,
          id: '42',
          title: 'Song',
          artist: 'Neko',
          hash: hash,
        ),
      ),
      throwsA(isA<MusicSearchException>()),
    );
    expect(probes, 1);
  });

  test(
    'Kugou user ID change with an unchanged token invalidates discovery',
    () async {
      const hash = '0123456789abcdef0123456789abcdef';
      final accounts = AccountStore();
      await accounts.update(kugou: {'token': 'shared-token', 'userid': '1'});
      var probes = 0;
      final catalog = NativeMusicCatalog(
        accounts: accounts,
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('privilege')) {
            await accounts.update(kugou: {'userid': '2'});
            return {
              'status': 1,
              'data': [
                {
                  'quality': '128',
                  'hash': hash,
                  'is_publish': 1,
                  'info': {'filesize': 100},
                },
              ],
            };
          }
          probes++;
          return {};
        },
      );
      await expectLater(
        catalog.downloadable(
          const OnlineSong(
            provider: MusicProvider.kugou,
            id: '42',
            title: 'Song',
            artist: 'Neko',
            hash: hash,
          ),
        ),
        throwsA(isA<MusicSearchException>()),
      );
      expect(probes, 0);
    },
  );

  test(
    'all Kugou premium preferences map to their native quality identifiers',
    () async {
      const hash = '0123456789abcdef0123456789abcdef';
      for (final pair in {
        'jyeffect': 'viper_clear',
        'sky': 'viper_atmos',
        'jymaster': 'super',
        'dolby': 'viper_atmos',
      }.entries) {
        final catalog = NativeMusicCatalog(
          initializeSessions: false,
          transport: (request) async {
            if (request.uri.path.contains('privilege')) {
              return {
                'status': 1,
                'data': [
                  {
                    'quality': pair.value,
                    'hash': hash,
                    'is_publish': 1,
                    'info': {'filesize': 100},
                  },
                ],
              };
            }
            expect(request.uri.queryParameters['quality'], pair.value);
            return {
              'status': 1,
              'quality': pair.value,
              'url': 'https://audio.example/song',
            };
          },
        );
        final audio = await catalog.resolveDownload(
          const OnlineSong(
            provider: MusicProvider.kugou,
            id: '42',
            title: 'Song',
            artist: 'Neko',
            hash: hash,
          ),
          pair.key,
        );
        expect(audio.quality, pair.value);
      }
    },
  );

  test('cancellation stops before discovery and between probes', () async {
    var accepted = false;
    var calls = 0;
    final catalog = NativeMusicCatalog(
      initializeSessions: false,
      transport: (request) async {
        calls++;
        if (request.uri.path.contains('music/detail')) return discovery();
        accepted = false;
        return {
          'code': 200,
          'data': {'code': 404},
        };
      },
    );
    await expectLater(
      catalog.downloadable(song, accept: () => accepted),
      throwsA(isA<MusicSearchException>()),
    );
    expect(calls, 0);
    accepted = true;
    await expectLater(
      catalog.downloadable(song, accept: () => accepted),
      throwsA(isA<MusicSearchException>()),
    );
    expect(calls, 2);
  });

  test('account changes during a probe discard the entire result', () async {
    final accounts = AccountStore();
    await accounts.update(netease: {'MUSIC_U': 'before'});
    var probes = 0;
    final catalog = NativeMusicCatalog(
      accounts: accounts,
      initializeSessions: false,
      transport: (request) async {
        if (request.uri.path.contains('music/detail')) return discovery();
        probes++;
        await accounts.update(netease: {'MUSIC_U': 'after'});
        return {
          'code': 200,
          'data': {
            'code': 200,
            'level': 'standard',
            'url': 'https://audio.example/song',
          },
        };
      },
    );
    await expectLater(
      catalog.downloadable(song),
      throwsA(isA<MusicSearchException>()),
    );
    expect(probes, 1);
  });

  group('independent plugin download probes', () {
    late AccountStore accounts;
    setUp(() {
      accounts = AccountStore();
    });
    tearDown(() => accounts.dispose());

    NativeMusicCatalog catalogWith(
      Future<Map<String, dynamic>> Function(CatalogRequest) transport,
    ) => NativeMusicCatalog(
      accounts: accounts,
      musicPlugin: BuiltinMusicPlugin(
        transport: (request) async {
          expect(request.uri.scheme, 'https');
          expect(request.uri.host, 'animemusic.bzxhkj.com');
          expect(request.uri.path, '/v1/index.php/music/url');
          expect(request.headers, {
            'Accept': 'application/json',
            'User-Agent': 'NekoPlayer/animemusic-1',
          });
          expect(request.uri.queryParameters['source'], 'tx');
          expect(request.uri.queryParameters['musicId'], pluginSong.id);
          expect(request.body, isNull);
          return transport(request);
        },
      ),
    );

    test(
      'only exact complete formats are offered and probes are sequential',
      () async {
        var active = 0;
        var peak = 0;
        final probes = <String>[];
        final catalog = catalogWith((request) async {
          active++;
          if (active > peak) peak = active;
          await Future<void>.delayed(const Duration(milliseconds: 1));
          active--;
          final quality = request.uri.queryParameters['quality']!;
          probes.add(quality);
          return {
            'code': 200,
            'url': 'https://audio.example/$quality',
            'quality': quality == 'flac' ? '128k' : quality,
            if (quality == '320k') 'freeTrialInfo': {'end': 30},
          };
        });
        expect(accounts.siteToken, isEmpty);
        final qualities = await catalog.downloadable(pluginSong);
        expect(probes, ['128k', '192k', '320k', 'flac', 'flac24bit']);
        expect(peak, 1);
        expect(qualities.map((quality) => quality.id), [
          'hires',
          '192k',
          '128k',
        ]);
      },
    );

    test('plugin rate limits stop discovery after one request', () async {
      var probes = 0;
      final catalog = catalogWith((request) async {
        probes++;
        return {'code': 429};
      });
      await expectLater(
        catalog.downloadable(pluginSong),
        throwsA(isA<MusicSearchException>()),
      );
      expect(probes, 1);
    });

    test(
      'site token changes during discovery do not interrupt plugin downloads',
      () async {
        await accounts.update(siteToken: 'site-token-valid-123');
        final probes = <String>[];
        final catalog = catalogWith((request) async {
          final quality = request.uri.queryParameters['quality']!;
          probes.add(quality);
          if (probes.length == 1) {
            await accounts.update(siteToken: 'different-token-123456');
          } else if (probes.length == 2) {
            await accounts.update(siteToken: '');
          }
          return {
            'code': 200,
            'url': 'https://audio.example/$quality',
            'quality': quality,
          };
        });
        final qualities = await catalog.downloadable(pluginSong);
        expect(probes, ['128k', '192k', '320k', 'flac', 'flac24bit']);
        expect(qualities.map((quality) => quality.id), [
          'hires',
          'flac',
          '320k',
          '192k',
          '128k',
        ]);
        expect(accounts.siteToken, isEmpty);
        final audio = await catalog.resolveDownload(pluginSong, 'hires');
        expect(audio.quality, 'hires');
        expect(audio.uri, Uri.parse('https://audio.example/flac24bit'));
        expect(probes.last, 'flac24bit');
        expect(probes, hasLength(6));
      },
    );
  });
}
