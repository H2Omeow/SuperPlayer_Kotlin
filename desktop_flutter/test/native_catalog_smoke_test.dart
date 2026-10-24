import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/domain/library.dart';

void main() {
  for (final provider in const [MusicProvider.netease, MusicProvider.kugou]) {
    test(
      '${provider.name} public timed lyrics without resolving audio',
      () async {
        final catalog = NativeMusicCatalog();
        final result = await catalog.search(
          provider,
          Platform.environment['NATIVE_AUDIO_QUERY'] ?? '夜曲',
          1,
        );
        expect(result.songs, isNotEmpty);
        final lines = parseLrc(await catalog.lyrics(result.songs.first));
        expect(lines, isNotEmpty);
        expect(lines.every((line) => line.text.isNotEmpty), isTrue);
      },
      skip: Platform.environment['NATIVE_LYRICS_SMOKE'] != '1',
    );
    test('${provider.name} public catalog connectivity', () async {
      final result = await NativeMusicCatalog().search(provider, '夜曲', 1);
      expect(result.songs, isNotEmpty);
      expect(result.songs.every((song) => song.provider == provider), isTrue);
    }, skip: Platform.environment['NATIVE_API_SMOKE'] != '1');
  }
  for (final provider in const [MusicProvider.netease, MusicProvider.kugou]) {
    test(
      '${provider.name} lyrics and full-stream resolver connectivity',
      () async {
        final catalog = NativeMusicCatalog();
        final result = await catalog.search(
          provider,
          Platform.environment['NATIVE_AUDIO_QUERY'] ?? '义勇军进行曲',
          1,
        );
        expect(result.songs, isNotEmpty);
        final song = result.songs.first;
        final lyrics = await catalog.lyrics(song);
        expect(lyrics, isNotEmpty);
        final audio = await catalog.resolve(song, preferred: 'standard');
        expect(audio.uri.hasAuthority, isTrue);
        expect(['https', 'http'], contains(audio.uri.scheme));
      },
      skip: Platform.environment['NATIVE_AUDIO_SMOKE'] != '1',
    );
  }
  for (final provider in const [MusicProvider.netease, MusicProvider.kugou]) {
    test(
      '${provider.name} public QR creation without authorizing login',
      () async {
        final qr = await NativeMusicCatalog().createLoginQr(provider);
        expect(qr.key, isNotEmpty);
        expect(qr.uri.scheme, 'https');
      },
      skip: Platform.environment['NATIVE_QR_SMOKE'] != '1',
    );
  }
}
