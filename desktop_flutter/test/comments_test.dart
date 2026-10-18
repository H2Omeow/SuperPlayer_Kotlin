import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/comments_repository.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/data/site_client.dart';
import 'package:nekoplayer_desktop/domain/comments.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/ui/comments_page.dart';

class CommentCatalog extends NativeMusicCatalog {
  Future<Map<String, dynamic>> Function(String path, Map<String, dynamic> data)
  nc = (_, _) async => {'code': 200};
  Future<Map<String, dynamic>> Function(
    String path,
    Map<String, String> query,
    Map<String, dynamic>? body,
  )
  kg = (_, _, _) async => {'status': 1};
  @override
  Future<Map<String, dynamic>> neteaseApi(
    String path,
    Map<String, dynamic> data, {
    bool weapi = false,
  }) => nc(path, data);
  @override
  Future<Map<String, dynamic>> kugouApi(
    String path,
    Map<String, String> query, {
    String router = '',
    Map<String, dynamic>? body,
    String? rawBody,
    bool clear = false,
    bool sign = true,
    String host = 'gateway.kugou.com',
    Map<String, String> extraHeaders = const {},
  }) => kg(path, query, body);
  @override
  Future<void> initializeProvider(MusicProvider provider) async {}
}

const song = OnlineSong(
  provider: MusicProvider.netease,
  id: '123',
  title: '夜晚',
  artist: 'Neko',
);
const kgSong = OnlineSong(
  provider: MusicProvider.kugou,
  id: '456',
  title: '夜晚',
  artist: 'Neko',
  hash: '0123456789abcdef0123456789abcdef',
  albumAudioId: '789',
);
const comment = SongComment(id: '1', user: 'Neko', content: '你好', owned: true);

void main() {
  test(
    'Netease list and replies map quotes, ownership and pagination cursor',
    () async {
      final catalog = CommentCatalog();
      var floor = false;
      catalog.nc = (path, data) async {
        if (floor) {
          expect(path, '/api/resource/comment/floor/get');
          expect(data['parentCommentId'], '1');
          expect(data['time'], 55);
        } else {
          expect(path, '/api/v1/resource/comments/R_SO_4_123');
          expect(data['offset'], 20);
        }
        final result = {
          'comments': [
            {
              'commentId': 1,
              'user': {'userId': 12, 'nickname': 'Neko'},
              'content': '你好',
              'time': 88,
              'likedCount': 3,
              'liked': true,
              'replyCount': 2,
              'beReplied': [
                {
                  'user': {'nickname': 'H2O'},
                  'content': '夜晚',
                },
              ],
            },
          ],
          'more': true,
          'total': 100,
        };
        return {'code': 200, if (floor) 'data': result else ...result};
      };
      final repository = CommentsRepository(catalog);
      final page = await repository.list(
        CommentSource.netease,
        song,
        offset: 20,
        account: '12',
      );
      expect(page.comments.single.owned, isTrue);
      expect(page.comments.single.quotedUser, 'H2O');
      expect(page.comments.single.liked, isTrue);
      expect(page.cursor, '88');
      floor = true;
      final replies = await repository.list(
        CommentSource.netease,
        song,
        parent: comment,
        previous: const CommentPage(cursor: '55'),
      );
      expect(replies.hasMore, isTrue);
    },
  );

  test(
    'mutations require an account and reject invalid content or mismatched source',
    () async {
      final catalog = CommentCatalog();
      final repository = CommentsRepository(catalog);
      await expectLater(
        repository.send(CommentSource.netease, song, 'hello'),
        throwsA(isA<MusicSearchException>()),
      );
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      await expectLater(
        repository.send(CommentSource.netease, song, '  '),
        throwsA(isA<MusicSearchException>()),
      );
      await expectLater(
        repository.send(CommentSource.netease, kgSong, 'hello'),
        throwsA(isA<MusicSearchException>()),
      );
      await expectLater(
        repository.delete(
          CommentSource.netease,
          song,
          const SongComment(id: '1', user: 'other', content: 'hello'),
        ),
        throwsA(isA<MusicSearchException>()),
      );
    },
  );

  test(
    'Netease mutations preserve thread IDs and check platform acknowledgment',
    () async {
      final catalog = CommentCatalog();
      await catalog.accounts.update(netease: {'MUSIC_U': 'secret'});
      final paths = <String>[];
      catalog.nc = (path, data) async {
        paths.add(path);
        expect(data['threadId'], 'R_SO_4_123');
        if (path.endsWith('/reply')) {
          expect(data['commentId'], '1');
          expect(data['content'], 'hello');
        }
        return {'code': 200};
      };
      final repository = CommentsRepository(catalog);
      await repository.send(
        CommentSource.netease,
        song,
        'hello',
        parent: comment,
      );
      await repository.like(CommentSource.netease, song, comment);
      await repository.delete(CommentSource.netease, song, comment);
      expect(paths, [
        '/api/resource/comments/reply',
        '/api/v1/comment/like',
        '/api/resource/comments/delete',
      ]);
      catalog.nc = (_, _) async => {'code': 403};
      await expectLater(
        repository.send(CommentSource.netease, song, 'hello'),
        throwsA(isA<MusicSearchException>()),
      );
    },
  );

  test(
    'Kugou comments retain special resource and mutation-specific key',
    () async {
      final catalog = CommentCatalog();
      await catalog.accounts.update(
        kugou: {'token': 'secret', 'userid': '12', 'dfid': 'device'},
      );
      catalog.kg = (path, query, body) async {
        if (path.contains('cmtlist')) {
          expect(query['mixsongid'], '789');
          return {
            'status': 1,
            'count': 21,
            'childrenid': 'special',
            'list': [
              {
                'id': 1,
                'user_id': 12,
                'user_name': 'Neko',
                'content': 'hello',
                'special_child_name': 'song',
                'like': {'count': 3, 'haslike': 1},
              },
            ],
          };
        }
        expect(query['r'], 'commentsv3/add');
        expect(query['childrenid'], 'special');
        final key =
            '1005OIlwieks28dk2k092lksi2UIkp20489${query['clienttime']}${query['mid']}${jsonEncode(body)}';
        expect(query['key'], md5.convert(utf8.encode(key)).toString());
        return {'status': 1};
      };
      final repository = CommentsRepository(catalog);
      final page = await repository.list(
        CommentSource.kugou,
        kgSong,
        account: '12',
      );
      expect(page.specialId, 'special');
      expect(page.comments.single.owned, isTrue);
      expect(page.hasMore, isTrue);
      await repository.send(CommentSource.kugou, kgSong, 'hello', page: page);
      await expectLater(
        repository.like(CommentSource.kugou, kgSong, comment),
        throwsA(isA<MusicSearchException>()),
      );
    },
  );

  test(
    'site comments support anonymous reading, authenticated reply and deletion',
    () async {
      final catalog = CommentCatalog();
      final methods = <String>[];
      final repository = CommentsRepository(
        catalog,
        site: SiteClient(
          transport: (request) async {
            methods.add(request.method);
            if (request.method == 'GET') {
              expect(request.token, isEmpty);
              expect(request.uri.queryParameters['song'], 'netease:123');
            } else {
              expect(request.token, 'site-token-valid-123');
            }
            if (request.method == 'POST' && request.uri.path == '/comments') {
              expect(request.body?['parentId'], '1');
            }
            return {
              'code': 200,
              'comments': [
                {
                  'id': '1',
                  'userName': 'Neko',
                  'content': 'hello',
                  'owned': true,
                },
              ],
              'total': 1,
            };
          },
        ),
      );
      expect(
        (await repository.list(CommentSource.site, song)).comments.single.owned,
        isTrue,
      );
      await catalog.accounts.update(siteToken: 'site-token-valid-123');
      await repository.send(CommentSource.site, song, 'hello', parent: comment);
      await repository.delete(CommentSource.site, song, comment);
      expect(methods, ['GET', 'POST', 'DELETE']);
    },
  );

  testWidgets(
    'comments remain readable on compact windows and posting requires login',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final catalog = CommentCatalog();
      final repository = CommentsRepository(
        catalog,
        site: SiteClient(
          transport: (_) async => {
            'code': 200,
            'comments': [
              {'id': '1', 'userName': 'Neko', 'content': '测试评论'},
            ],
            'total': 1,
          },
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: CommentsPage(repository: repository, song: song),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('测试评论'), findsOneWidget);
      await tester.tap(find.text('写评论'));
      await tester.pumpAndSettle();
      expect(find.textContaining('请先在设置中登录'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
