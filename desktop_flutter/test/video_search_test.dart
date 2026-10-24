import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/builtin_music_plugin.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
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

  test('MV routes use the independent plugin without a site login', () async {
    final accounts = AccountStore();
    addTearDown(accounts.dispose);
    const routes = {
      MusicProvider.netease: ['wy', '1080'],
      MusicProvider.kugou: ['kg', ''],
      MusicProvider.gatewayKugou: ['kg', ''],
      MusicProvider.gatewayKuwo: ['kw', 'MP4BD'],
      MusicProvider.gatewayNetease: ['wy', '1080'],
      MusicProvider.gatewayTencent: ['tx', '1080'],
      MusicProvider.gatewayMigu: ['mg', ''],
      MusicProvider.gatewayBilibili: ['bilibili', '80'],
      MusicProvider.pluginQishui: ['qishui', '1080p'],
    };
    var platform = '';
    var quality = '';
    final requests = <CatalogRequest>[];
    final plugin = BuiltinMusicPlugin(
      transport: (request) async {
        requests.add(request);
        expect(request.uri.scheme, 'https');
        expect(request.uri.host, 'animemusic.bzxhkj.com');
        expect(request.headers, {
          'Accept': 'application/json',
          'User-Agent': 'NekoPlayer/animemusic-1',
        });
        expect(request.body, isNull);
        final query = request.uri.queryParameters;
        expect(query['platform'], platform);
        if (request.uri.path.endsWith('/search')) {
          expect(request.uri.path, '/v1/index.php/music/mv/search');
          expect(query['keyword'], '夜晚');
          expect(query['page'], '1');
          expect(query['limit'], '${NativeMusicCatalog.pageSize}');
          return {
            'code': 200,
            'isEnd': false,
            'data': [
              {'id': 'video-id', 'title': 'MV 夜晚', 'artist': 'Neko'},
            ],
          };
        }
        expect(request.uri.path, '/v1/index.php/music/mv/url');
        expect(query['id'], 'video-id');
        expect(query['quality'], quality);
        return {'code': 200, 'url': 'https://video.example/music.mp4'};
      },
    );
    final catalog = NativeMusicCatalog(accounts: accounts, musicPlugin: plugin);
    expect(accounts.siteToken, isEmpty);
    for (final entry in routes.entries) {
      final provider = entry.key;
      platform = entry.value[0];
      quality = entry.value[1];
      final result = await catalog.searchVideos(provider, '夜晚', 1);
      expect(result.videos.single.id, 'video-id');
      expect(result.videos.single.provider, provider);
      expect(result.hasMore, isTrue);
      expect((await catalog.videoUrl(result.videos.single)).scheme, 'https');
    }
    expect(requests, hasLength(routes.length * 2));
  });

  test('invalid video URLs never reach the playback engine', () async {
    for (final url in [
      'file:///etc/passwd',
      'javascript:alert(1)',
      '/music.mp4',
      'https://user:password@video.example/music.mp4',
    ]) {
      var calls = 0;
      final catalog = NativeMusicCatalog(
        musicPlugin: BuiltinMusicPlugin(
          transport: (_) async {
            calls++;
            return {'code': 200, 'url': url};
          },
        ),
      );
      await expectLater(
        catalog.videoUrl(video('1')),
        throwsA(isA<MusicSearchException>()),
      );
      expect(calls, 1);
    }
  });

  testWidgets(
    'compact MV search is usable without initializing video playback',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(360, 640));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      final accounts = AccountStore();
      addTearDown(accounts.dispose);
      var calls = 0;
      final catalog = NativeMusicCatalog(
        accounts: accounts,
        musicPlugin: BuiltinMusicPlugin(
          transport: (request) async {
            calls++;
            expect(request.uri.path, '/v1/index.php/music/mv/search');
            expect(request.uri.queryParameters['keyword'], '夜晚');
            return {
              'code': 200,
              'isEnd': true,
              'data': [
                {'id': 'video-id', 'title': 'MV 夜晚', 'artist': 'Neko'},
              ],
            };
          },
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: MusicVideosPage(catalog: catalog, pauseMusic: () async {}),
        ),
      );
      expect(find.text('音乐 MV'), findsOneWidget);
      await tester.enterText(find.byType(TextField), '夜晚');
      await tester.tap(find.byTooltip('搜索 MV'));
      await tester.pumpAndSettle();
      expect(accounts.siteToken, isEmpty);
      expect(calls, 1);
      expect(find.text('MV 夜晚'), findsOneWidget);
      expect(find.text('Neko'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
}
