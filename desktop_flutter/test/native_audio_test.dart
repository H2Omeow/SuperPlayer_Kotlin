import 'dart:convert';

import 'package:encrypt/encrypt.dart';
import 'package:flutter_test/flutter_test.dart';
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
  title: '夜晚',
  artist: 'Neko',
);

void main() {
  test(
    'discovers qualities, rejects trials and tests best remaining quality',
    () async {
      final probes = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          final data = payload(request);
          if (request.uri.path.contains('music/detail')) {
            expect(data['songId'], '123');
            return {
              'code': 200,
              'data': {
                'l': {'size': 1},
                'h': {'size': 3},
                'sq': {'size': 4},
                'hr': {'size': 0},
              },
            };
          }
          final quality = data['level'] as String;
          probes.add(quality);
          expect(data['ids'], '[123]');
          return {
            'code': 200,
            'data': [
              {
                'code': 200,
                'level': quality,
                'url': 'https://audio.example/$quality.flac',
                if (quality == 'exhigh') 'freeTrialInfo': {'end': 30},
              },
            ],
          };
        },
      );
      final result = await catalog.resolve(song);
      expect(probes, ['exhigh', 'lossless']);
      expect(result.quality, 'lossless');
    },
  );

  test(
    'silent downgrade is retained only after checking better formats',
    () async {
      final probes = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('music/detail')) {
            return {
              'code': 200,
              'data': {
                'h': {'size': 3},
                'sq': {'size': 4},
              },
            };
          }
          final quality = payload(request)['level'] as String;
          probes.add(quality);
          return {
            'code': 200,
            'data': [
              {
                'code': 200,
                'level': quality == 'exhigh' ? 'standard' : quality,
                'url': 'https://audio.example/music',
              },
            ],
          };
        },
      );
      expect((await catalog.resolve(song)).quality, 'lossless');
      expect(probes, ['exhigh', 'lossless']);
    },
  );

  test('rejects invalid URL schemes and unknown qualities', () async {
    for (final data in [
      {'url': 'file:///etc/passwd', 'level': 'exhigh'},
      {'url': 'https://audio.example/test', 'level': 'unknown'},
      {'url': 'https://user:password@audio.example/test', 'level': 'exhigh'},
      {'url': 'https://audio.example/test', 'code': 403},
    ]) {
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async => request.uri.path.contains('music/detail')
            ? {
                'code': 200,
                'data': {
                  'h': {'size': 1},
                },
              }
            : {
                'code': 200,
                'data': [data],
              },
      );
      await expectLater(
        catalog.resolve(song),
        throwsA(isA<MusicSearchException>()),
      );
    }
  });

  test(
    'account or rate-limit errors do not fall through to further probes',
    () async {
      var probes = 0;
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('music/detail')) {
            return {
              'code': 200,
              'data': {
                'h': {'size': 1},
                'sq': {'size': 1},
              },
            };
          }
          probes++;
          return {'code': 429};
        },
      );
      await expectLater(
        catalog.resolve(song),
        throwsA(isA<MusicSearchException>()),
      );
      expect(probes, 1);
    },
  );

  test(
    'Kugou uses privilege-specific hash and excludes trial streams',
    () async {
      const hash = '0123456789abcdef0123456789abcdef';
      const alternate = 'abcdef0123456789abcdef0123456789';
      final probes = <String>[];
      final catalog = NativeMusicCatalog(
        initializeSessions: false,
        transport: (request) async {
          if (request.uri.path.contains('privilege')) {
            expect(request.headers['x-router'], 'media.store.kugou.com');
            return {
              'status': 1,
              'data': [
                {
                  'quality': '128',
                  'hash': hash,
                  'is_publish': 1,
                  'info': {'filesize': 1},
                  'relate_goods': [
                    {
                      'quality': 'flac',
                      'hash': alternate,
                      'is_publish': 1,
                      'info': {'filesize': 2},
                    },
                    {
                      'quality': 'high',
                      'hash': hash,
                      'is_publish': 0,
                      'info': {'filesize': 2},
                    },
                  ],
                },
              ],
            };
          }
          final parameters = request.uri.queryParameters;
          final quality = parameters['quality']!;
          probes.add(quality);
          expect(parameters['behavior'], 'play');
          if (quality == 'flac') expect(parameters['hash'], alternate);
          return {
            'status': 1,
            'quality': quality,
            'url': ['https://audio.example/music'],
            'is_free_part': quality == 'flac' ? 1 : 0,
          };
        },
      );
      final result = await catalog.resolve(
        const OnlineSong(
          provider: MusicProvider.kugou,
          id: '1',
          hash: hash,
          title: '夜晚',
          artist: 'Neko',
        ),
        preferred: 'lossless',
      );
      expect(probes, ['flac', '128']);
      expect(result.quality, '128');
    },
  );

  test('both providers return decoded lyrics', () async {
    final catalog = NativeMusicCatalog(
      transport: (request) async {
        if (request.uri.host == 'interface.music.163.com') {
          return {
            'code': 200,
            'lrc': {'lyric': '[00:01.00]夜晚'},
          };
        }
        if (request.uri.path == '/v1/search') {
          return {
            'candidates': [
              {'id': 2, 'accesskey': 'key'},
            ],
          };
        }
        expect(request.uri.queryParameters['accesskey'], 'key');
        return {'content': base64Encode(utf8.encode('[00:02.00]酷狗'))};
      },
    );
    expect(await catalog.lyrics(song), '[00:01.00]夜晚');
    expect(
      await catalog.lyrics(
        const OnlineSong(
          provider: MusicProvider.kugou,
          id: '1',
          title: '夜晚',
          artist: 'Neko',
        ),
      ),
      '[00:02.00]酷狗',
    );
  });
}
