import 'package:flutter/material.dart';

import '../domain/library.dart';
import '../player_controller.dart';

Future<void> showPlaylistPicker(
  BuildContext context,
  PlayerController player,
  Track track,
) => showDialog<void>(
  context: context,
  builder: (_) => _PlaylistPicker(player: player, track: track),
);

class _PlaylistPicker extends StatefulWidget {
  const _PlaylistPicker({required this.player, required this.track});
  final PlayerController player;
  final Track track;

  @override
  State<_PlaylistPicker> createState() => _PlaylistPickerState();
}

class _PlaylistPickerState extends State<_PlaylistPicker> {
  final name = TextEditingController();

  void add(String id) {
    widget.player.addToPlaylist(id, widget.track);
    Navigator.pop(context);
  }

  void create() {
    final id = widget.player.createPlaylist(name.text);
    if (id != null) add(id);
  }

  @override
  void dispose() {
    name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.player,
    builder: (context, _) => AlertDialog(
      title: const Text('加入本机歌单'),
      scrollable: true,
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              widget.track.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 12),
            if (widget.player.playlists.isEmpty)
              const Text('尚无本机歌单，可以在下方新建。')
            else
              SizedBox(
                height: 200,
                child: ListView.builder(
                  itemCount: widget.player.playlists.length,
                  itemBuilder: (context, index) {
                    final playlist = widget.player.playlists[index];
                    final contains = playlist.paths.contains(widget.track.path);
                    return ListTile(
                      title: Text(playlist.name),
                      subtitle: Text(
                        contains ? '已在歌单中' : '${playlist.paths.length} 首',
                      ),
                      leading: Icon(
                        contains ? Icons.check : Icons.playlist_add,
                      ),
                      onTap: () => add(playlist.id),
                    );
                  },
                ),
              ),
            const SizedBox(height: 12),
            TextField(
              controller: name,
              maxLength: 100,
              decoration: const InputDecoration(labelText: '新歌单名称'),
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => create(),
            ),
            const SizedBox(height: 8),
            const Text('仅修改本机歌单，不修改平台账号，也不会开始播放。'),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('取消'),
        ),
        ListenableBuilder(
          listenable: name,
          builder: (context, _) => FilledButton(
            onPressed: name.text.trim().isEmpty ? null : create,
            child: const Text('新建并加入'),
          ),
        ),
      ],
    ),
  );
}
