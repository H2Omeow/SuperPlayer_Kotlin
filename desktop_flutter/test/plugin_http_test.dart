import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/audio_downloader.dart';
import 'package:nekoplayer_desktop/data/plugins/plugin_http.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

void main() {
  test(
    'host requests and downloads strip credentials on cross-origin redirects',
    () async {
      final first = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final second = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      final directory = await Directory.systemTemp.createTemp(
        'neko-plugin-headers-',
      );
      addTearDown(() async {
        await first.close(force: true);
        await second.close(force: true);
        await directory.delete(recursive: true);
      });
      var initial = 0;
      var redirected = 0;
      first.listen((request) async {
        initial++;
        expect(request.headers.value('authorization'), 'Bearer fixture');
        expect(request.headers.value('cookie'), 'plugin=session');
        await request.response.redirect(
          Uri.parse('http://127.0.0.1:${second.port}/audio'),
        );
      });
      second.listen((request) async {
        redirected++;
        expect(request.headers.value('authorization'), isNull);
        expect(request.headers.value('cookie'), isNull);
        expect(request.headers.value('x-plugin'), 'retained');
        request.response.headers.contentType = ContentType('audio', 'mpeg');
        request.response.write('audio');
        await request.response.close();
      });
      final uri = Uri.parse('http://127.0.0.1:${first.port}/audio');
      const headers = {
        'Authorization': 'Bearer fixture',
        'Cookie': 'plugin=session',
        'X-Plugin': 'retained',
      };
      final response = await sendPluginHttp({
        'url': '$uri',
        'headers': headers,
      });
      expect(utf8.decode(base64Decode(response['body'] as String)), 'audio');
      final destination = File('${directory.path}/audio.mp3');
      await AudioDownloader().download(
        audio: ResolvedAudio(uri, '128k', headers: headers),
        partial: File('${directory.path}/audio.part'),
        destination: destination,
        cancellation: DownloadCancellation(),
        progress: (_, _) {},
      );
      expect(await destination.readAsString(), 'audio');
      expect(initial, 2);
      expect(redirected, 2);
    },
  );
}
