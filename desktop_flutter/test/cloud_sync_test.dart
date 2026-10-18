import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/cloud_library.dart';
import 'package:nekoplayer_desktop/data/site_client.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/player_controller.dart';
import 'package:nekoplayer_desktop/site_controller.dart';

import 'fakes.dart';

const token = 'site-token-valid-123';
Map<String, dynamic> remoteSong(String id, {String source = 'netease'}) => {
  'id': int.parse(id),
  'nm': '歌曲$id',
  'ar': 'Neko',
  'source': source,
};

void main() {
  test(
    'cloud merge unions playlist songs, keeps settings and never uploads local paths',
    () {
      const localFile = Track(
        path: '/private/music/secret.mp3',
        title: 'private',
        artist: 'local',
      );
      final localSong = Track.fromOnline(
        const OnlineSong(
          provider: MusicProvider.netease,
          id: '1',
          title: '歌曲1',
          artist: 'Neko',
        ),
      );
      final local = LibraryData(
        tracks: [localFile, localSong],
        favorites: {localFile.path, localSong.path},
        history: [localFile.path, localSong.path],
        playlists: [
          LocalPlaylist(
            id: 'p',
            name: '本机名称',
            paths: [localFile.path, localSong.path],
          ),
        ],
        volume: 34,
        rate: 1.5,
        preferredQuality: 'lossless',
      );
      final remote = <String, dynamic>{
        'favorites': [
          remoteSong('2'),
          remoteSong('3', source: 'animemusic-future'),
        ],
        'history': [remoteSong('2')],
        'playlists': [
          {
            'id': 'p',
            'name': '云端名称',
            'songs': [
              remoteSong('2'),
              remoteSong('3', source: 'animemusic-future'),
            ],
          },
        ],
        'futureField': 'preserved',
      };
      final merged = CloudLibrary.merge(local, CloudLibrary.decode(remote));
      expect(merged.playlists.single.name, '本机名称');
      expect(merged.playlists.single.paths, [
        localFile.path,
        localSong.path,
        'netease:2',
      ]);
      expect(merged.volume, 34);
      expect(merged.preferredQuality, 'lossless');
      final encoded = CloudLibrary.encode(merged, remote);
      expect(encoded['futureField'], 'preserved');
      expect((encoded['favorites'] as List), hasLength(3));
      expect(
        ((encoded['playlists'] as List).single as Map)['songs'],
        hasLength(3),
      );
      expect(jsonEncode(encoded), isNot(contains('/private/')));
      expect(jsonEncode(encoded), contains('animemusic-future'));
    },
  );

  test('malformed remote data is rejected before merge', () {
    expect(
      () => CloudLibrary.decode({'favorites': 'invalid'}),
      throwsFormatException,
    );
    expect(
      () => CloudLibrary.decode({
        'playlists': [
          {
            'id': '1',
            'songs': [1],
          },
        ],
      }),
      throwsFormatException,
    );
  });

  group('site controller', () {
    late AccountStore accounts;
    late MemoryStore library;
    late PlayerController player;
    late List<SiteRequest> requests;
    late Future<Map<String, dynamic>> Function(SiteRequest request) handler;
    late SiteController site;

    setUp(() async {
      accounts = AccountStore();
      await accounts.initialize();
      library = MemoryStore();
      player = PlayerController(engine: FakeEngine(), store: library);
      await player.initialize();
      requests = [];
      handler = (request) async => {
        'code': 0,
        'user': {'id': '123', 'nickname': 'Neko'},
        'data': {},
      };
      site = SiteController(
        accounts: accounts,
        player: player,
        client: SiteClient(
          transport: (request) async {
            requests.add(request);
            expect(request.uri.scheme, 'https');
            expect(request.uri.host, 'player.nekoh2o.top');
            expect(request.token, isNotEmpty);
            return handler(request);
          },
        ),
      );
    });
    tearDown(() async {
      site.dispose();
      await player.flushed;
      player.dispose();
    });

    test(
      'login validates token before storing and does not auto-sync',
      () async {
        await site.importToken('nekoplayer://auth?token=$token');
        expect(accounts.siteToken, token);
        expect(site.user?['id'], '123');
        expect(requests, hasLength(1));
        expect(requests.single.uri.path, '/auth/sso/me');
        handler = (_) async => {'code': -1};
        await site.importToken('invalid-token-123456');
        expect(accounts.siteToken, token);
        expect(site.message, contains('未确认操作成功'));
      },
    );

    test(
      'sync preserves local credentials by default without uploading them',
      () async {
        await accounts.update(
          siteToken: token,
          netease: {'MUSIC_U': 'local-secret'},
        );
        handler = (request) async {
          if (request.method == 'GET') {
            return {
              'code': 0,
              'data': {
                'favorites': [remoteSong('2')],
                'ncCookie': 'MUSIC_U=cloud-secret',
              },
            };
          }
          expect(jsonEncode(request.body), isNot(contains('local-secret')));
          expect(request.body?['ncCookie'], 'MUSIC_U=cloud-secret');
          return {'code': 0};
        };
        await site.sync();
        expect(player.favorites, {'netease:2'});
        expect(accounts.netease['MUSIC_U'], 'local-secret');
        expect(requests.map((request) => request.method), ['GET', 'PUT']);
        expect(site.message, contains('未上传本机平台凭据'));
      },
    );

    test(
      'opt-in credential sync fills missing slots but keeps local accounts',
      () async {
        await accounts.update(
          siteToken: token,
          netease: {'MUSIC_U': 'local-nc'},
          kugou: {'token': 'local-kg', 'userid': '123'},
          platform: 0,
        );
        handler = (request) async {
          if (request.method == 'GET') {
            return {
              'code': 0,
              'data': {
                'ncCookie': 'MUSIC_U=remote-nc',
                'kgCookies': [
                  'token=remote-kg; userid=1',
                  'token=remote-lite; userid=2',
                ],
              },
            };
          }
          expect(request.body?['ncCookie'], contains('local-nc'));
          expect((request.body?['kgCookies'] as List)[0], contains('local-kg'));
          expect(
            (request.body?['kgCookies'] as List)[1],
            contains('remote-lite'),
          );
          return {'code': 0};
        };
        await site.sync(includeCredentials: true);
        expect(accounts.netease['MUSIC_U'], 'local-nc');
        expect(accounts.kugou(0)['token'], 'local-kg');
        expect(accounts.kugou(1)['token'], 'remote-lite');
        expect(accounts.platform, 0);
      },
    );

    test(
      'opt-in sync preserves partial local token rather than overwriting it',
      () async {
        await accounts.update(
          siteToken: token,
          kugou: {'token': 'local-partial'},
        );
        handler = (request) async => request.method == 'GET'
            ? {
                'code': 0,
                'data': {
                  'kgPlatform': 1,
                  'kgCookies': [
                    'token=remote; userid=1',
                    'token=lite; userid=2',
                  ],
                },
              }
            : {'code': 0};
        await site.sync(includeCredentials: true);
        expect(accounts.kugou(0)['token'], 'local-partial');
        expect(accounts.platform, 0);
        expect(
          (requests.last.body?['kgCookies'] as List).first,
          contains('local-partial'),
        );
      },
    );

    test(
      'opt-in sync adopts remote slot and handles legacy token without userid',
      () async {
        await accounts.update(siteToken: token);
        handler = (request) async => request.method == 'GET'
            ? {
                'code': 0,
                'data': {'kgPlatform': 1, 'kgToken': 'legacy-token'},
              }
            : {'code': 0};
        await site.sync(includeCredentials: true);
        expect(accounts.platform, 1);
        expect(accounts.kugou()['token'], 'legacy-token');
        expect(requests.last.body?['kgPlatform'], 1);
      },
    );

    test('failed pull does not mutate library or issue push', () async {
      await accounts.update(siteToken: token);
      handler = (_) async => {
        'code': 1,
        'data': {
          'favorites': [remoteSong('2')],
        },
      };
      await site.sync();
      expect(player.tracks, isEmpty);
      expect(requests, hasLength(1));
      expect(site.message, isNot(contains('合并成功')));
    });

    test(
      'failed local save prevents push and remote rejection is not success',
      () async {
        await accounts.update(siteToken: token);
        handler = (request) async => request.method == 'GET'
            ? {
                'code': 0,
                'data': {
                  'favorites': [remoteSong('2')],
                },
              }
            : {'code': 1};
        library.failWrite = true;
        await site.sync();
        expect(requests, hasLength(1));
        library.failWrite = false;
        requests.clear();
        await site.sync();
        expect(requests, hasLength(2));
        expect(site.message, contains('未确认操作成功'));
      },
    );

    test(
      'loopback SSO validates random callback path then verifies token',
      () async {
        await site.login((loginUri) async {
          expect(loginUri.host, 'account.nekoh2o.top');
          final callback = Uri.parse(loginUri.queryParameters['redirect']!);
          expect(callback.host, '127.0.0.1');
          expect(callback.path, startsWith('/auth/'));
          final client = HttpClient();
          try {
            final badRequest = await client.getUrl(
              callback.replace(
                path: '/wrong',
                queryParameters: {'token': token},
              ),
            );
            final badResponse = await badRequest.close();
            expect(badResponse.statusCode, 400);
            await badResponse.drain<void>();
            final request = await client.getUrl(
              callback.replace(queryParameters: {'token': token}),
            );
            final response = await request.close();
            expect(response.statusCode, 200);
            expect(
              response.headers.value(HttpHeaders.cacheControlHeader),
              'no-store',
            );
            final text = await utf8.decoder.bind(response).join();
            expect(text, isNot(contains(token)));
          } finally {
            client.close(force: true);
          }
          return true;
        });
        expect(accounts.siteToken, token);
        expect(site.busy, isFalse);
      },
    );

    test('browser login can be cancelled without storing any token', () async {
      await site.login((_) async {
        site.cancelLogin();
        return true;
      });
      expect(accounts.siteToken, isEmpty);
      expect(site.busy, isFalse);
      expect(requests, isEmpty);
    });
  });
}
