import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/data/source_gateway.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/ui/music_videos_page.dart';
import 'package:nekoplayer_desktop/video_search_controller.dart';

MusicVideo video(String id) => MusicVideo(
  provider: MusicProvider.netease,
  id: id,
  title: 'MV $id',
  artist: 'Neko',
);

void main() {
  test(
    'video search drops stale responses and retries a failed page',
    () async {
      final pending = Completer<SearchVideoPage>();
      var failPage = true;
      final pages = <int>[];
      final controller = VideoSearchController((provider, query, page) async {
        pages.add(page);
        if (query == 'old') return pending.future;
        if (page == 2 && failPage) throw const MusicSearchException('暂时失败');
        return SearchVideoPage([
          video('1'),
          if (page == 2) video('2'),
        ], page == 1);
      });
      final old = controller.search('old');
      await controller.search('new');
      pending.complete(SearchVideoPage([video('old')], false));
      await old;
      expect(controller.videos.single.id, '1');
      await controller.loadMore();
      expect(controller.videos, hasLength(1));
      expect(controller.error, '暂时失败');
      failPage = false;
      await controller.loadMore();
      expect(controller.videos.map((video) => video.id), ['1', '2']);
      expect(pages, [1, 1, 2, 2]);
      controller.dispose();
    },
  );

  test('native and external MV routes use signed gateway endpoints', () async {
    final accounts = AccountStore();
    await accounts.update(siteToken: 'site-token-valid-123');
    final gateway = SourceGateway(
      accounts: accounts,
      transport: (request) async {
        if (request.uri.path.endsWith('/session')) {
          return {
            'key': List.filled(32, '01').join(),
            'keyId': 'key',
            'expires': DateTime.now().millisecondsSinceEpoch + 3600000,
          };
        }
        final external = request.uri.path.startsWith('/api/music-source/');
        final query = request.uri.queryParameters;
        expect(
          query[external ? 'platform' : 'source'],
          external ? 'tx' : 'netease',
        );
        if (request.uri.path.endsWith('/search')) {
          return {
            'code': 200,
            'isEnd': false,
            'data': [
              {'id': 'video-id', 'title': 'MV 夜晚', 'artist': 'Neko'},
            ],
          };
        }
        if (external) expect(query['quality'], '1080');
        return {'code': 200, 'url': 'https://video.example/music.mp4'};
      },
    );
    final catalog = NativeMusicCatalog(
      accounts: accounts,
      sourceGateway: gateway,
    );
    for (final provider in [
      MusicProvider.netease,
      MusicProvider.gatewayTencent,
    ]) {
      final result = await catalog.searchVideos(provider, '夜晚', 1);
      expect(result.videos.single.id, 'video-id');
      expect(result.hasMore, isTrue);
      expect((await catalog.videoUrl(result.videos.single)).scheme, 'https');
    }
  });

  test('invalid video URLs never reach the playback engine', () async {
    final accounts = AccountStore();
    await accounts.update(siteToken: 'site-token-valid-123');
    final catalog = NativeMusicCatalog(
      accounts: accounts,
      sourceGateway: SourceGateway(
        accounts: accounts,
        transport: (request) async {
          if (request.uri.path.endsWith('/session')) {
            return {
              'key': List.filled(32, '01').join(),
              'keyId': 'key',
              'expires': DateTime.now().millisecondsSinceEpoch + 3600000,
            };
          }
          return {'code': 200, 'url': 'file:///etc/passwd'};
        },
      ),
    );
    await expectLater(
      catalog.videoUrl(video('1')),
      throwsA(isA<MusicSearchException>()),
    );
  });

  testWidgets(
    'compact MV search is usable without initializing video playback',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final accounts = AccountStore();
      final catalog = NativeMusicCatalog(accounts: accounts);
      await tester.pumpWidget(
        MaterialApp(
          home: MusicVideosPage(catalog: catalog, pauseMusic: () async {}),
        ),
      );
      expect(find.text('音乐 MV'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '夜晚');
      await tester.tap(find.byTooltip('搜索 MV'));
      await tester.pumpAndSettle();
      expect(find.textContaining('请先登录本站账号'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
