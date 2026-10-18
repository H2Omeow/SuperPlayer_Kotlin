import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/cloud_library.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/data/source_gateway.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

const token = 'site-token-valid-123';
final key = List<int>.generate(32, (index) => index);
Map<String, dynamic> session(int time) => {
  'keyId': 'key-123',
  'key': key.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join(),
  'expires': time + 3600000,
};

void main() {
  test('unsigned or out-of-scope requests never reach transport', () async {
    var calls = 0;
    final accounts = AccountStore();
    final gateway = SourceGateway(
      accounts: accounts,
      transport: (_) async {
        calls++;
        return {};
      },
    );
    await expectLater(
      gateway.request('music/search', {}),
      throwsA(isA<MusicSearchException>()),
    );
    await accounts.update(siteToken: token);
    await expectLater(
      gateway.request('../other', {}),
      throwsA(isA<MusicSearchException>()),
    );
    expect(calls, 0);
  });

  test(
    'session is shared concurrently and signature uses exact encoded path',
    () async {
      final accounts = AccountStore();
      await accounts.update(siteToken: token);
      var sessions = 0;
      final time = DateTime.now().millisecondsSinceEpoch;
      final requests = <CatalogRequest>[];
      final gateway = SourceGateway(
        accounts: accounts,
        clock: () => time,
        nonce: () => 'nonce123',
        transport: (request) async {
          requests.add(request);
          expect(request.uri.host, 'nekoh2o.top');
          expect(request.headers['Authorization'], 'Bearer $token');
          if (request.uri.path.endsWith('/session')) {
            sessions++;
            expect(request.body, '');
            await Future<void>.delayed(const Duration(milliseconds: 10));
            return session(time);
          }
          final path = request.uri.toString().substring(
            'https://nekoh2o.top'.length,
          );
          final canonical =
              'GET\n$path\n$time\nnonce123\n${sha256.convert(const <int>[])}';
          expect(
            request.headers['X-Source-Signature'],
            Hmac(sha256, key).convert(utf8.encode(canonical)).toString(),
          );
          return {'code': 200, 'data': []};
        },
      );
      await Future.wait([
        gateway.request('music/search', {
          'keyword': '夜晚 & + /',
          'platform': 'tx',
        }),
        gateway.request('music/lyric', {'musicId': 'AbC'}),
      ]);
      expect(sessions, 1);
      expect(requests.where((request) => request.body == null), hasLength(2));
    },
  );

  test('account switch discards stale session response', () async {
    final accounts = AccountStore();
    await accounts.update(siteToken: token);
    final pending = Completer<Map<String, dynamic>>();
    var calls = 0;
    final gateway = SourceGateway(
      accounts: accounts,
      transport: (_) {
        calls++;
        return pending.future;
      },
    );
    final request = gateway.request('usage', {});
    await accounts.update(siteToken: 'different-token-123456');
    pending.complete(session(DateTime.now().millisecondsSinceEpoch));
    await expectLater(request, throwsA(isA<MusicSearchException>()));
    expect(calls, 1);
  });

  test(
    'HTTP 401 invalidates session without silently retrying quota-consuming requests',
    () async {
      final accounts = AccountStore();
      await accounts.update(siteToken: token);
      var sessions = 0;
      var calls = 0;
      final gateway = SourceGateway(
        accounts: accounts,
        transport: (request) async {
          if (request.uri.path.endsWith('/session')) {
            sessions++;
            return session(DateTime.now().millisecondsSinceEpoch);
          }
          calls++;
          throw MusicHttpException(401);
        },
      );
      for (var count = 0; count < 2; count++) {
        await expectLater(
          gateway.request('usage', {}),
          throwsA(isA<MusicHttpException>()),
        );
      }
      expect(sessions, 2);
      expect(calls, 2);
    },
  );

  test(
    'gateway search, lyric and quality resolution retain provider identity',
    () async {
      final accounts = AccountStore();
      await accounts.update(siteToken: token);
      final gateway = SourceGateway(
        accounts: accounts,
        transport: (request) async {
          final query = request.uri.queryParameters;
          if (request.uri.path.endsWith('/session')) {
            return session(DateTime.now().millisecondsSinceEpoch);
          }
          if (request.uri.path.endsWith('/search')) {
            expect(query['platform'], 'tx');
            return {
              'code': 200,
              'isEnd': false,
              'data': [
                {'id': 'AbC123', 'title': '夜晚', 'artist': 'Neko'},
              ],
            };
          }
          expect(query['source'], 'tx');
          expect(query['musicId'], 'AbC123');
          if (request.uri.path.endsWith('/lyric')) {
            return {'code': 200, 'lyric': '[00:01.00]夜晚'};
          }
          expect(query['quality'], 'flac');
          return {
            'code': 200,
            'url': 'https://audio.example/song.flac',
            'format': 'flac',
          };
        },
      );
      final catalog = NativeMusicCatalog(
        accounts: accounts,
        sourceGateway: gateway,
      );
      final result = await catalog.search(
        MusicProvider.gatewayTencent,
        '夜晚',
        1,
      );
      final song = result.songs.single;
      expect(result.hasMore, isTrue);
      expect(song.key, 'animemusic-tx:AbC123');
      expect(await catalog.lyrics(song), '[00:01.00]夜晚');
      expect(
        (await catalog.resolve(song, preferred: 'lossless')).quality,
        'flac',
      );
      expect((await catalog.resolveDownload(song, 'lossless')).format, 'flac');
      final track = CloudLibrary.decodeSong({
        'id': 123,
        'nm': '夜晚',
        'ar': 'Neko',
        'source': 'animemusic-tx',
        'providerMediaId': 'AbC123',
      });
      expect(track!.online!.id, 'AbC123');
      expect(
        CloudLibrary.songKey(CloudLibrary.encodeSong(track)),
        'animemusic-tx:AbC123',
      );
    },
  );
}
