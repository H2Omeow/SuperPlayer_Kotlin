import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/player_controller.dart';

import 'fakes.dart';

class FakeOnlineSource implements OnlineAudioSource {
  @override
  Future<String> lyrics(OnlineSong song) async => '[00:01.00]在线歌词';
  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) async =>
      ResolvedAudio(Uri.https('audio.example', '/expiring-token'), preferred);
}

void main() {
  test(
    'online identity and metadata round-trip without storing media URLs',
    () {
      final track = Track.fromOnline(
        const OnlineSong(
          provider: MusicProvider.kugou,
          id: '123',
          title: '夜晚',
          artist: 'Neko',
          albumId: '45',
          albumAudioId: '67',
          hash: '0123456789abcdef0123456789abcdef',
        ),
      );
      final encoded = LibraryData(
        tracks: [track],
        favorites: {track.path},
        history: [track.path],
      ).encode();
      final restored = LibraryData.decode(encoded);
      expect(restored.tracks.single.online!.albumAudioId, '67');
      expect(restored.favorites, {track.path});
      expect(restored.history, [track.path]);
      expect(encoded, isNot(contains('https://')));
    },
  );

  test(
    'online songs bypass file checks and participate in library and playlists',
    () async {
      final store = MemoryStore();
      final engine = FakeEngine();
      final controller = PlayerController(
        engine: engine,
        store: store,
        onlineSource: FakeOnlineSource(),
      );
      await controller.initialize();
      final track = Track.fromOnline(
        const OnlineSong(
          provider: MusicProvider.netease,
          id: '123',
          title: '夜晚',
          artist: 'Neko',
        ),
      );
      await controller.play([track], 0);
      await Future<void>.delayed(Duration.zero);
      expect(controller.current?.path, 'netease:123');
      expect(controller.error, isNull);
      expect(controller.lyrics.single.text, '在线歌词');
      controller.toggleFavorite(track);
      controller.createPlaylist('线上歌单');
      controller.addToPlaylist(controller.playlists.single.id, track);
      expect(
        controller.playlistTracks(controller.playlists.single).single.path,
        track.path,
      );
      await controller.flushed;
      final restored = LibraryData.decode(store.value!);
      expect(restored.history, [track.path]);
      expect(restored.favorites, {track.path});
      controller.dispose();
    },
  );
}
