import 'dart:async';

import 'package:flutter/material.dart';

import '../data/catalog_browse_repository.dart';
import '../domain/online_music.dart';
import '../player_controller.dart';
import 'account_music_page.dart';

class CatalogBrowsePage extends StatefulWidget {
  const CatalogBrowsePage({
    super.key,
    required this.repository,
    required this.player,
    required this.onComments,
    this.onDownload,
  });
  final CatalogBrowseRepository repository;
  final PlayerController player;
  final void Function(OnlineSong) onComments;
  final void Function(OnlineSong)? onDownload;
  @override
  State<CatalogBrowsePage> createState() => _CatalogBrowsePageState();
}

class _CatalogBrowsePageState extends State<CatalogBrowsePage> {
  final input = TextEditingController();
  CatalogKind kind = CatalogKind.artist;
  List<CatalogItem> results = [];
  List<String> suggestions = [];
  String keyword = '';
  String? error;
  bool busy = false;
  bool recommended = false;
  bool more = false;
  int page = 0;
  int generation = 0;
  int suggestionGeneration = 0;
  Timer? debounce;

  String label(CatalogKind value) => switch (value) {
    CatalogKind.artist => '歌手',
    CatalogKind.album => '专辑',
    CatalogKind.playlist => '歌单',
  };

  void suggest(String value) {
    debounce?.cancel();
    final request = ++suggestionGeneration;
    if (value.trim().isEmpty) {
      setState(() => suggestions = []);
      return;
    }
    debounce = Timer(const Duration(milliseconds: 400), () async {
      try {
        final words = await widget.repository.suggestions(value);
        if (mounted && request == suggestionGeneration) {
          setState(() => suggestions = words);
        }
      } catch (_) {
        if (mounted && request == suggestionGeneration) {
          setState(() => suggestions = []);
        }
      }
    });
  }

  Future<void> search({bool reset = true, bool recommendations = false}) async {
    if (busy && !reset) return;
    final nextKeyword = reset ? input.text.trim() : keyword;
    if (!recommendations && nextKeyword.isEmpty) return;
    final request = ++generation;
    ++suggestionGeneration;
    debounce?.cancel();
    final nextPage = reset ? 1 : page + 1;
    setState(() {
      busy = true;
      error = null;
      suggestions = [];
      recommended = recommendations;
      if (reset) {
        results = [];
        page = 0;
        more = false;
        keyword = nextKeyword;
      }
    });
    try {
      final response = recommendations
          ? await widget.repository.recommendedPlaylists()
          : await widget.repository.search(kind, nextKeyword, nextPage);
      if (!mounted || request != generation) return;
      final byId = {
        for (final item in results) '${item.kind}:${item.id}': item,
      };
      for (final item in response.items) {
        byId['${item.kind}:${item.id}'] = item;
      }
      setState(() {
        results = byId.values.toList();
        page = nextPage;
        more = response.hasMore;
      });
    } catch (failure) {
      if (mounted && request == generation) {
        setState(
          () => error = failure is MusicSearchException
              ? failure.message
              : '分类内容读取失败，请重试。',
        );
      }
    } finally {
      if (mounted && request == generation) setState(() => busy = false);
    }
  }

  @override
  void dispose() {
    generation++;
    suggestionGeneration++;
    debounce?.cancel();
    input.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('歌手、专辑与推荐歌单')),
    body: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1000),
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('分类内容使用网易云原生目录；歌曲播放仍按平台权限解析。'),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final value in CatalogKind.values)
                        ChoiceChip(
                          label: Text(label(value)),
                          selected: kind == value && !recommended,
                          onSelected: (_) {
                            generation++;
                            suggestionGeneration++;
                            debounce?.cancel();
                            setState(() {
                              kind = value;
                              recommended = false;
                              busy = false;
                              results = [];
                              suggestions = [];
                              page = 0;
                              more = false;
                              error = null;
                            });
                            if (input.text.trim().isNotEmpty) search();
                          },
                        ),
                      ActionChip(
                        label: const Text('推荐歌单'),
                        onPressed: () => search(recommendations: true),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: input,
                    maxLength: 200,
                    onChanged: suggest,
                    onSubmitted: (_) => search(),
                    decoration: InputDecoration(
                      labelText: '搜索${label(kind)}',
                      counterText: '',
                      suffixIcon: IconButton(
                        tooltip: '搜索',
                        onPressed: () => search(),
                        icon: const Icon(Icons.search),
                      ),
                    ),
                  ),
                  if (suggestions.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final word in suggestions)
                            ActionChip(
                              label: Text(word),
                              onPressed: () {
                                input.text = word;
                                search();
                              },
                            ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
            if (busy) const LinearProgressIndicator(),
            if (error != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Row(
                  children: [
                    Expanded(child: Text(error!)),
                    TextButton(
                      onPressed: busy
                          ? null
                          : () => search(
                              reset: page == 0,
                              recommendations: recommended,
                            ),
                      child: const Text('重试'),
                    ),
                  ],
                ),
              ),
            Expanded(
              child: results.isEmpty && !busy && error == null
                  ? const Center(child: Text('输入关键词，或浏览推荐歌单'))
                  : ListView.builder(
                      itemCount: results.length + (more ? 1 : 0),
                      itemBuilder: (context, index) {
                        if (index == results.length) {
                          return Center(
                            child: TextButton(
                              onPressed: busy
                                  ? null
                                  : () => search(reset: false),
                              child: const Text('加载更多'),
                            ),
                          );
                        }
                        final item = results[index];
                        return ListTile(
                          leading: Icon(
                            item.kind == CatalogKind.artist
                                ? Icons.person_outline
                                : item.kind == CatalogKind.album
                                ? Icons.album_outlined
                                : Icons.queue_music,
                          ),
                          title: Text(item.name),
                          subtitle: Text(
                            [
                              item.subtitle,
                              if (item.count > 0) '${item.count} 首',
                            ].where((value) => value.isNotEmpty).join(' · '),
                          ),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => Navigator.of(context).push(
                            MaterialPageRoute<void>(
                              builder: (_) => AccountMusicPage(
                                repository: widget.repository.music,
                                player: widget.player,
                                onComments: widget.onComments,
                                onDownload: widget.onDownload,
                                title: item.name,
                                collectionId:
                                    'browse-${item.kind.name}-${item.id}',
                                songLoader: (page) =>
                                    widget.repository.songs(item, page),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    ),
  );
}
