import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/search_controller.dart';

class PendingCatalog implements MusicCatalog {
  final requests = <({MusicProvider provider, String keyword, int page})>[];
  final responses = <Completer<SearchPage>>[];

  @override
  Future<SearchPage> search(MusicProvider provider, String keyword, int page) {
    requests.add((provider: provider, keyword: keyword, page: page));
    final response = Completer<SearchPage>();
    responses.add(response);
    return response.future;
  }
}

OnlineSong song(String id) => OnlineSong(
  provider: MusicProvider.netease,
  id: id,
  title: id,
  artist: '歌手',
);

void main() {
  late PendingCatalog catalog;
  late SearchController controller;
  setUp(() {
    catalog = PendingCatalog();
    controller = SearchController(catalog);
  });
  tearDown(() => controller.dispose());

  test('empty search does not issue a request', () async {
    await controller.search('  ');
    expect(catalog.requests, isEmpty);
  });

  test('new search supersedes an older response', () async {
    final old = controller.search('old');
    final latest = controller.search(' latest ');
    catalog.responses[1].complete(
      SearchPage(songs: [song('new')], hasMore: false),
    );
    await latest;
    catalog.responses[0].complete(
      SearchPage(songs: [song('old')], hasMore: true),
    );
    await old;
    expect(controller.keyword, 'latest');
    expect(controller.songs.single.id, 'new');
    expect(controller.hasMore, isFalse);
  });

  test('switching source discards pending result and resets state', () async {
    final pending = controller.search('music');
    controller.selectProvider(MusicProvider.kugou);
    catalog.responses.single.complete(
      SearchPage(songs: [song('old')], hasMore: true),
    );
    await pending;
    expect(controller.songs, isEmpty);
    expect(controller.loading, isFalse);
    expect(controller.keyword, isEmpty);
    final search = controller.search('new');
    expect(catalog.requests.last.provider, MusicProvider.kugou);
    expect(catalog.requests.last.page, 1);
    catalog.responses.last.complete(
      const SearchPage(songs: [], hasMore: false),
    );
    await search;
  });

  test(
    'pagination deduplicates and retries the same page after failure',
    () async {
      final initial = controller.search('music');
      catalog.responses.last.complete(
        SearchPage(songs: [song('1')], hasMore: true),
      );
      await initial;
      final failed = controller.loadMore();
      await controller.loadMore();
      expect(catalog.requests, hasLength(2));
      catalog.responses.last.completeError(const MusicSearchException('网络错误'));
      await failed;
      expect(controller.error, '网络错误');
      expect(controller.songs, hasLength(1));
      final retry = controller.loadMore();
      expect(catalog.requests.last.page, 2);
      catalog.responses.last.complete(
        SearchPage(songs: [song('1'), song('2')], hasMore: false),
      );
      await retry;
      expect(controller.songs.map((song) => song.id), ['1', '2']);
      expect(controller.error, isNull);
      await controller.loadMore();
      expect(catalog.requests, hasLength(3));
    },
  );

  test('disposed controller ignores a pending completion', () async {
    final temporary = SearchController(catalog);
    final pending = temporary.search('music');
    temporary.dispose();
    catalog.responses.last.complete(
      SearchPage(songs: [song('1')], hasMore: true),
    );
    await pending;
    expect(temporary.songs, isEmpty);
  });
}
