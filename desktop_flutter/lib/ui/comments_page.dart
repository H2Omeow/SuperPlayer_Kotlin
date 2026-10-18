import 'package:flutter/material.dart';

import '../data/comments_repository.dart';
import '../domain/comments.dart';
import '../domain/online_music.dart';

class CommentsPage extends StatefulWidget {
  const CommentsPage({
    super.key,
    required this.repository,
    required this.song,
    this.source = CommentSource.site,
    this.parent,
    this.parentPage = const CommentPage(),
  });
  final CommentsRepository repository;
  final OnlineSong song;
  final CommentSource source;
  final SongComment? parent;
  final CommentPage parentPage;
  @override
  State<CommentsPage> createState() => _CommentsPageState();
}

class _CommentsPageState extends State<CommentsPage> {
  late CommentSource source = widget.source;
  OnlineSong? selected;
  List<OnlineSong> matches = [];
  List<SongComment> comments = [];
  CommentPage page = const CommentPage();
  bool loading = false;
  bool posting = false;
  String account = '';
  String? error;
  String? message;
  int generation = 0;
  int offset = 0;

  @override
  void initState() {
    super.initState();
    prepare();
  }

  Future<void> prepare() async {
    final request = ++generation;
    setState(() {
      loading = true;
      comments = [];
      matches = [];
      selected = null;
      error = null;
      message = null;
      offset = 0;
      page = widget.parentPage;
    });
    try {
      final candidates = await widget.repository.matches(source, widget.song);
      if (!mounted || request != generation) return;
      if (source == CommentSource.site ||
          source.name == widget.song.provider.name) {
        selected = widget.song;
        await fetch(reset: true);
      } else {
        setState(() {
          matches = candidates;
          if (candidates.isEmpty) error = '没有找到对应平台歌曲，请切换评论来源。';
        });
      }
    } catch (failure) {
      if (mounted && request == generation) {
        setState(
          () => error = failure is MusicSearchException
              ? failure.message
              : '评论服务暂时不可用，请重试。',
        );
      }
    } finally {
      if (mounted && request == generation) setState(() => loading = false);
    }
  }

  Future<void> fetch({bool reset = false}) async {
    final song = selected;
    if (song == null) return;
    final request = generation;
    setState(() {
      loading = true;
      error = null;
    });
    try {
      if (reset) account = await widget.repository.accountId(source);
      final result = await widget.repository.list(
        source,
        song,
        offset: reset ? 0 : offset,
        account: account,
        parent: widget.parent,
        previous: reset ? widget.parentPage : page,
      );
      if (!mounted || request != generation) return;
      setState(() {
        comments = {
          if (!reset) ...{for (final comment in comments) comment.id: comment},
          for (final comment in result.comments) comment.id: comment,
        }.values.toList();
        offset = (reset ? 0 : offset) + result.comments.length;
        page = result;
      });
    } catch (failure) {
      if (mounted && request == generation) {
        setState(
          () => error = failure is MusicSearchException
              ? failure.message
              : '评论加载失败，请重试。',
        );
      }
    } finally {
      if (mounted && request == generation) setState(() => loading = false);
    }
  }

  Future<void> mutate(Future<void> Function() action, String success) async {
    if (posting || selected == null) return;
    final request = generation;
    setState(() {
      posting = true;
      message = null;
      error = null;
    });
    try {
      await action();
      if (!mounted || request != generation) return;
      setState(() => message = success);
      await fetch(reset: true);
    } catch (failure) {
      if (mounted && request == generation) {
        setState(
          () => error = failure is MusicSearchException
              ? failure.message
              : '操作未获确认，请检查平台状态后再试，避免重复发送。',
        );
      }
    } finally {
      if (mounted && request == generation) setState(() => posting = false);
    }
  }

  Future<void> compose([SongComment? reply]) async {
    if (!widget.repository.loggedIn(source)) {
      setState(() => error = '请先在设置中登录${source.label}账号。');
      return;
    }
    final input = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(reply == null ? '发布到${source.label}' : '回复 ${reply.user}'),
        content: SizedBox(
          width: 440,
          child: TextField(
            controller: input,
            maxLength: 2000,
            minLines: 3,
            maxLines: 6,
            decoration: const InputDecoration(hintText: '写下你想说的话…'),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, input.text),
            child: const Text('确认发布'),
          ),
        ],
      ),
    );
    input.dispose();
    if (!mounted || value == null || selected == null) return;
    await mutate(
      () => widget.repository.send(
        source,
        selected!,
        value,
        parent: reply ?? widget.parent,
        page: widget.parent == null ? page : widget.parentPage,
      ),
      '评论已发布。',
    );
  }

  Future<void> delete(SongComment comment) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('删除这条评论？'),
        content: const Text('此操作不能撤销。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('删除'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted && selected != null) {
      await mutate(
        () => widget.repository.delete(source, selected!, comment),
        '评论已删除。',
      );
    }
  }

  @override
  void dispose() {
    generation++;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: Text(widget.parent == null ? '${widget.song.title} · 评论' : '评论回复'),
    ),
    body: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (widget.parent == null)
            Wrap(
              spacing: 8,
              children: [
                for (final value in CommentSource.values)
                  ChoiceChip(
                    label: Text(value.label),
                    selected: source == value,
                    onSelected: posting
                        ? null
                        : (_) {
                            source = value;
                            prepare();
                          },
                  ),
              ],
            ),
          if (selected != null)
            Text('${source.label} · ${selected!.title} · ${selected!.artist}'),
          if (widget.parent != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                '${widget.parent!.user}：${widget.parent!.content}',
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          if (loading || posting) const LinearProgressIndicator(),
          if (message != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(message!),
            ),
          if (error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          Wrap(
            spacing: 8,
            children: [
              if (selected != null)
                FilledButton.tonal(
                  onPressed: posting || loading ? null : () => compose(),
                  child: const Text('写评论'),
                ),
              OutlinedButton(
                onPressed: posting || loading
                    ? null
                    : () => selected == null ? prepare() : fetch(reset: true),
                child: const Text('刷新'),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Expanded(
            child: selected == null
                ? ListView(
                    children: [
                      if (matches.isNotEmpty)
                        const Text('请选择对应歌曲，避免查看到错误资源的评论。'),
                      for (final song in matches)
                        ListTile(
                          title: Text(song.title),
                          subtitle: Text(song.artist),
                          onTap: () {
                            setState(() {
                              selected = song;
                              matches = [];
                            });
                            fetch(reset: true);
                          },
                        ),
                    ],
                  )
                : comments.isEmpty
                ? const Center(child: Text('暂无评论。'))
                : ListView.builder(
                    itemCount: comments.length + 1,
                    itemBuilder: (context, index) {
                      if (index == comments.length) {
                        return page.hasMore
                            ? Center(
                                child: OutlinedButton(
                                  onPressed: loading ? null : fetch,
                                  child: const Text('加载更多'),
                                ),
                              )
                            : const SizedBox.shrink();
                      }
                      final comment = comments[index];
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                comment.user.isEmpty ? '音乐爱好者' : comment.user,
                                style: Theme.of(context).textTheme.titleSmall,
                              ),
                              Text(
                                comment.time,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                              const SizedBox(height: 8),
                              SelectableText(
                                comment.deleted ? '评论已删除' : comment.content,
                              ),
                              if (comment.quotedContent.isNotEmpty)
                                Padding(
                                  padding: const EdgeInsets.only(top: 8),
                                  child: Text(
                                    '${comment.quotedUser}：${comment.quotedContent}',
                                  ),
                                ),
                              Wrap(
                                spacing: 4,
                                children: [
                                  if (source != CommentSource.kugou)
                                    TextButton.icon(
                                      onPressed:
                                          posting || loading || comment.deleted
                                          ? null
                                          : () => mutate(
                                              () => widget.repository.like(
                                                source,
                                                selected!,
                                                comment,
                                              ),
                                              comment.liked ? '已取消点赞。' : '已点赞。',
                                            ),
                                      icon: Icon(
                                        comment.liked
                                            ? Icons.favorite
                                            : Icons.favorite_border,
                                        size: 16,
                                      ),
                                      label: Text('${comment.likes}'),
                                    ),
                                  TextButton(
                                    onPressed:
                                        posting || loading || comment.deleted
                                        ? null
                                        : () => compose(comment),
                                    child: const Text('回复'),
                                  ),
                                  if (comment.replies > 0)
                                    TextButton(
                                      onPressed: () =>
                                          Navigator.of(context).push(
                                            MaterialPageRoute<void>(
                                              builder: (_) => CommentsPage(
                                                repository: widget.repository,
                                                song: selected!,
                                                source: source,
                                                parent: comment,
                                                parentPage: page,
                                              ),
                                            ),
                                          ),
                                      child: Text('${comment.replies} 条回复'),
                                    ),
                                  if (comment.owned &&
                                      source != CommentSource.kugou &&
                                      !comment.deleted)
                                    TextButton(
                                      onPressed: posting || loading
                                          ? null
                                          : () => delete(comment),
                                      child: const Text('删除'),
                                    ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    ),
  );
}
