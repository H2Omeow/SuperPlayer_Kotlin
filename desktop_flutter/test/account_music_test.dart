import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_music_repository.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/player_controller.dart';
import 'package:nekoplayer_desktop/ui/account_music_page.dart';

import 'comments_test.dart' show CommentCatalog;
import 'fakes.dart';

Map<String, dynamic> ncSong(String id) => {
  'id': id,
  'name': '歌曲$id',
  'ar': [
    {'name': 'Neko'},
  ],
  'al': {'id': 4, 'name': '专辑'},
};

void main() {
  late CommentCatalog catalog;
  late AccountMusicRepository repository;
  setUp(() async {
    catalog = CommentCatalog();
    await catalog.accounts.initialize();
    repository = AccountMusicRepository(catalog);
  });

  test(
    'Kugou history, personal radio and followed artists use native response shapes',
    () async {
      await catalog.accounts.update(kugou: {'token': 'secret', 'userid': '1'});
      final row = {
        'hash': '0123456789abcdef0123456789abcdef',
        'songname': 'KG',
      };
      catalog.kg = (path, query, body) async => switch (path) {
        '/playhistory/v1/get_songs' => {
          'status': 1,
          'data': {
            'list': [row],
          },
        },
        '/v2/personal_recommend' => {
          'status': 1,
          'data': {
            'song_list': [row],
          },
        },
        '/v4/follow_list' => {
          'status': 1,
          'data': {
            'info': [
              {'singerid': 9, 'singername': 'Neko'},
            ],
          },
        },
        '/kmr/v1/audio_group/author' => {
          'status': 1,
          'data': {
            'info': [row],
          },
        },
        _ => throw StateError('Unexpected API'),
      };
      expect(
        (await repository.collection(
          AccountCollection.history,
          1,
          provider: MusicProvider.kugou,
        )).items.single.title,
        'KG',
      );
      expect(
        (await repository.collection(
          AccountCollection.recommendations,
          1,
          provider: MusicProvider.kugou,
        )).hasMore,
        isFalse,
      );
      final artists = await repository.playlists(
        MusicProvider.kugou,
        1,
        following: true,
      );
      expect(artists.items.single.artist, isTrue);
      expect(artists.items.single.name, 'Neko');
      expect(
        (await repository.playlistSongs(
          MusicProvider.kugou,
          artists.items.single,
          1,
        )).items.single.title,
        'KG',
      );
    },
  );

  test('account reads require login, and reject remote failures', () async {
    await expectLater(
      repository.playlists(MusicProvider.netease, 1),
      throwsA(isA<MusicSearchException>()),
    );
    await expectLater(
      repository.playlists(MusicProvider.kugou, 1),
      throwsA(isA<MusicSearchException>()),
    );
    await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
    catalog.nc = (_, _) async => {'code': 301};
    await expectLater(
      repository.accountId(),
      throwsA(isA<MusicSearchException>()),
    );
  });

  test(
    'Netease playlist pagination uses verified account and preserves total',
    () async {
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      catalog.nc = (path, data) async {
        if (path == '/api/nuser/account/get') {
          return {
            'code': 200,
            'account': {'id': 123},
          };
        }
        expect(path, '/api/user/playlist');
        expect(data['uid'], '123');
        expect(data['offset'], 50);
        expect(data['limit'], 50);
        return {
          'code': 200,
          'more': false,
          'playlist': [
            {'id': 9, 'name': '歌单', 'trackCount': 200},
          ],
        };
      };
      final result = await repository.playlists(MusicProvider.netease, 2);
      expect(result.items.single.id, '9');
      expect(result.items.single.count, 200);
      expect(result.hasMore, isFalse);
    },
  );

  test(
    'playlist IDs are fetched in bounded ordered batches including missing songs',
    () async {
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      catalog.nc = (path, data) async {
        if (path == '/api/v6/playlist/detail') {
          expect(data['n'], 0);
          return {
            'code': 200,
            'playlist': {
              'trackIds': [
                for (var index = 1; index <= 105; index++) {'id': index},
              ],
            },
          };
        }
        final ids = (jsonDecode(data['c'] as String) as List)
            .map((row) => row['id'])
            .toList();
        expect(ids, hasLength(50));
        expect(ids.first, '51');
        expect(ids.last, '100');
        return {
          'code': 200,
          'songs': [
            for (final id in ids.reversed.skip(1)) ncSong(id as String),
          ],
        };
      };
      final result = await repository.playlistSongs(
        MusicProvider.netease,
        const AccountPlaylist(id: '9', name: '歌单'),
        2,
      );
      expect(result.items.first.id, '51');
      expect(result.items.last.id, '99');
      expect(result.hasMore, isTrue);
    },
  );

  test('Kugou collection uses global ID and exact paging parameters', () async {
    await catalog.accounts.update(kugou: {'token': 'secret', 'userid': '123'});
    catalog.kg = (path, query, body) async {
      if (path == '/v7/get_all_list') {
        expect(body?['page'], 1);
        expect(body?['pagesize'], 50);
        return {
          'status': 1,
          'data': {
            'total': 1,
            'info': [
              {
                'listid': 1,
                'global_collection_id': 'global-1',
                'name': 'KG',
                'count': 51,
              },
            ],
          },
        };
      }
      expect(path, '/pubsongs/v2/get_other_list_file_nofilt');
      expect(query['global_collection_id'], 'global-1');
      expect(query['begin_idx'], '50');
      return {
        'status': 1,
        'data': {
          'total': 51,
          'songs': [
            {
              'FileHash': 'ABCDEF0123456789ABCDEF0123456789',
              'FileName': 'Neko - 音乐',
              'MixSongID': 34,
              'AlbumID': 5,
            },
          ],
        },
      };
    };
    final playlists = await repository.playlists(MusicProvider.kugou, 1);
    expect(playlists.hasMore, isFalse);
    final songs = await repository.playlistSongs(
      MusicProvider.kugou,
      playlists.items.single,
      2,
    );
    expect(songs.hasMore, isFalse);
    expect(songs.items.single.hash, 'abcdef0123456789abcdef0123456789');
    expect(songs.items.single.title, '音乐');
    expect(songs.items.single.artist, 'Neko');
    expect(songs.items.single.albumAudioId, '34');
  });

  test(
    'likes, weekly history, daily recommendations and new songs keep source metadata',
    () async {
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      catalog.nc = (path, data) async => switch (path) {
        '/api/nuser/account/get' => {
          'code': 200,
          'account': {'id': 123},
        },
        '/api/song/like/get' => {
          'code': 200,
          'ids': [1, 2],
        },
        '/api/v3/song/detail' => {
          'code': 200,
          'songs': [ncSong('2'), ncSong('1')],
        },
        '/api/v1/play/record' => {
          'code': 200,
          'weekData': [
            {'song': ncSong('3')},
          ],
        },
        '/api/v3/discovery/recommend/songs' => {
          'code': 200,
          'data': {
            'dailySongs': [ncSong('4')],
          },
        },
        '/api/v1/discovery/new/songs' => {
          'code': 200,
          'data': [ncSong('5')],
        },
        _ => throw StateError('unexpected path'),
      };
      expect(
        (await repository.collection(
          AccountCollection.likes,
          1,
        )).items.map((song) => song.id),
        ['1', '2'],
      );
      expect(
        (await repository.collection(
          AccountCollection.history,
          1,
        )).items.single.id,
        '3',
      );
      expect(
        (await repository.collection(
          AccountCollection.recommendations,
          1,
        )).items.single.id,
        '4',
      );
      expect(
        (await repository.collection(
          AccountCollection.newSongs,
          1,
        )).items.single.album,
        '专辑',
      );
    },
  );

  testWidgets(
    'compact account library ignores stale requests after account changes',
    (tester) async {
      tester.view.physicalSize = const Size(480, 760);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      catalog = CommentCatalog();
      await catalog.accounts.initialize();
      repository = AccountMusicRepository(catalog);
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      final pending = Completer<Map<String, dynamic>>();
      catalog.nc = (path, _) async => path == '/api/nuser/account/get'
          ? {
              'code': 200,
              'account': {'id': 123},
            }
          : pending.future;
      final player = PlayerController(
        engine: FakeEngine(),
        store: MemoryStore(),
      );
      await player.initialize();
      await tester.pumpWidget(
        MaterialApp(
          home: AccountMusicPage(
            repository: repository,
            player: player,
            onComments: (_) {},
          ),
        ),
      );
      await tester.pump();
      await catalog.accounts.update(netease: {}, replace: true);
      await tester.pump();
      pending.complete({
        'code': 200,
        'playlist': [
          {'id': 1, 'name': '旧账号歌单'},
        ],
      });
      await tester.pumpAndSettle();
      expect(find.text('旧账号歌单'), findsNothing);
      expect(find.text('请先在平台账号中登录。'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
      player.dispose();
    },
  );
}
