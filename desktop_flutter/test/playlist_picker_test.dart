import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/player_controller.dart';
import 'package:nekoplayer_desktop/ui/playlist_picker.dart';

import 'fakes.dart';

void main() {
  final track = Track.fromOnline(
    const OnlineSong(
      provider: MusicProvider.netease,
      id: '42',
      title: 'Online song',
      artist: 'Neko',
    ),
  );

  for (final existing in [false, true]) {
    testWidgets(
      'playlist picker ${existing ? 'selects existing' : 'creates new'} without playback at large text scale',
      (tester) async {
        await tester.binding.setSurfaceSize(const Size(360, 480));
        addTearDown(() => tester.binding.setSurfaceSize(null));
        final store = MemoryStore();
        final engine = FakeEngine();
        final player = PlayerController(engine: engine, store: store);
        await player.initialize();
        if (existing) player.createPlaylist('My playlist');
        try {
          await tester.pumpWidget(
            MaterialApp(
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: const TextScaler.linear(2)),
                child: child!,
              ),
              home: Scaffold(
                body: Builder(
                  builder: (context) => TextButton(
                    onPressed: () => showPlaylistPicker(context, player, track),
                    child: const Text('Open picker'),
                  ),
                ),
              ),
            ),
          );
          await tester.tap(find.text('Open picker'));
          await tester.pumpAndSettle();
          if (existing) {
            await tester.ensureVisible(find.text('My playlist'));
            await tester.pumpAndSettle();
            await tester.tap(find.text('My playlist'));
          } else {
            expect(
              tester
                  .widget<FilledButton>(
                    find.widgetWithText(FilledButton, '新建并加入'),
                  )
                  .onPressed,
              isNull,
            );
            await tester.ensureVisible(find.byType(TextField));
            await tester.pumpAndSettle();
            await tester.enterText(find.byType(TextField), 'New playlist');
            await tester.testTextInput.receiveAction(TextInputAction.done);
          }
          await tester.pumpAndSettle();
          await player.flushed;
          expect(find.text('加入本机歌单'), findsNothing);
          expect(player.playlists.single.paths, [track.path]);
          expect(
            LibraryData.decode(store.value!).tracks.single.online?.id,
            '42',
          );
          expect(engine.playing, isFalse);
          expect(player.queue, isEmpty);
          expect(tester.takeException(), isNull);
        } finally {
          await tester.pumpWidget(const SizedBox());
          await player.flushed;
          player.dispose();
          await tester.pump();
        }
      },
    );
  }

  testWidgets('closing the picker does not create a library entry', (
    tester,
  ) async {
    final player = PlayerController(engine: FakeEngine(), store: MemoryStore());
    await player.initialize();
    try {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => TextButton(
                onPressed: () => showPlaylistPicker(context, player, track),
                child: const Text('Open picker'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open picker'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();
      expect(player.tracks, isEmpty);
      expect(player.playlists, isEmpty);
    } finally {
      await tester.pumpWidget(const SizedBox());
      player.dispose();
      await tester.pump();
    }
  });
}
