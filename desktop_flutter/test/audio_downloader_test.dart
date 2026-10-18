import 'dart:async';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/audio_downloader.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

void main() {
  late Directory directory;
  late HttpServer server;
  late Uri uri;
  late File partial;
  late File destination;
  late Future<void> Function(HttpRequest request) handler;
  final bytes = List<int>.generate(65536, (index) => index % 251);

  setUp(() async {
    directory = await Directory.systemTemp.createTemp('neko-download-');
    partial = File('${directory.path}/task.part');
    destination = File('${directory.path}/song.mp3');
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    uri = Uri.parse('http://127.0.0.1:${server.port}/song.mp3');
    handler = (request) async {
      request.response.headers.contentType = ContentType('audio', 'mpeg');
      request.response.contentLength = bytes.length;
      request.response.add(bytes);
      await request.response.close();
    };
    server.listen((request) {
      request.response.headers.contentType = ContentType('audio', 'mpeg');
      unawaited(handler(request));
    });
  });

  test(
    'fully downloaded partial file recovers final rename without a range request',
    () async {
      var requested = false;
      handler = (_) async {
        requested = true;
      };
      await partial.writeAsBytes(bytes);
      await AudioDownloader().download(
        audio: ResolvedAudio(
          uri,
          'exhigh',
          size: bytes.length,
          checksum: md5.convert(bytes).toString(),
        ),
        partial: partial,
        destination: destination,
        cancellation: DownloadCancellation(),
        progress: (_, _) {},
      );
      expect(requested, isFalse);
      expect(await destination.readAsBytes(), bytes);
      expect(await partial.exists(), isFalse);
    },
  );

  tearDown(() async {
    await server.close(force: true);
    await directory.delete(recursive: true);
  });

  Future<void> download({
    String? checksum,
    int? size,
    DownloadCancellation? cancellation,
  }) => AudioDownloader().download(
    audio: ResolvedAudio(
      uri,
      'standard',
      size: size ?? bytes.length,
      format: 'mp3',
      checksum: checksum ?? md5.convert(bytes).toString(),
    ),
    partial: partial,
    destination: destination,
    cancellation: cancellation ?? DownloadCancellation(),
    progress: (_, _) {},
  );

  test(
    'streams and verifies complete audio before committing target',
    () async {
      await download();
      expect(await destination.readAsBytes(), bytes);
      expect(await partial.exists(), isFalse);
    },
  );

  test('resumes only when content-range matches the partial file', () async {
    await partial.writeAsBytes(bytes.take(1000).toList());
    handler = (request) async {
      expect(request.headers.value(HttpHeaders.rangeHeader), 'bytes=1000-');
      request.response.statusCode = 206;
      request.response.headers.set(
        HttpHeaders.contentRangeHeader,
        'bytes 1000-${bytes.length - 1}/${bytes.length}',
      );
      request.response.contentLength = bytes.length - 1000;
      request.response.add(bytes.skip(1000).toList());
      await request.response.close();
    };
    await download();
    expect(await destination.readAsBytes(), bytes);
  });

  test('server ignoring range restarts rather than appending', () async {
    await partial.writeAsBytes([1, 2, 3]);
    await download();
    expect(await destination.readAsBytes(), bytes);
  });

  test('existing target is never overwritten', () async {
    await destination.writeAsString('keep');
    await expectLater(download(), throwsA(isA<MusicSearchException>()));
    expect(await destination.readAsString(), 'keep');
  });

  test(
    'checksum failure removes corrupted partial and leaves no target',
    () async {
      await expectLater(
        download(checksum: '00000000000000000000000000000000'),
        throwsA(isA<MusicSearchException>()),
      );
      expect(await destination.exists(), isFalse);
      expect(await partial.exists(), isFalse);
    },
  );

  test(
    'rejects mismatching content-range without changing the partial',
    () async {
      await partial.writeAsBytes([1, 2, 3]);
      handler = (request) async {
        request.response.statusCode = 206;
        request.response.headers.set(
          HttpHeaders.contentRangeHeader,
          'bytes 0-${bytes.length - 1}/${bytes.length}',
        );
        request.response.add(bytes);
        await request.response.close();
      };
      await expectLater(download(), throwsA(isA<MusicSearchException>()));
      expect(await partial.readAsBytes(), [1, 2, 3]);
      expect(await destination.exists(), isFalse);
    },
  );

  test('rejects error documents and mismatched sizes', () async {
    handler = (request) async {
      request.response.headers.contentType = ContentType.html;
      request.response.write('<html>not audio</html>');
      await request.response.close();
    };
    await expectLater(download(), throwsA(isA<MusicSearchException>()));
    handler = (request) async {
      request.response.contentLength = 2;
      request.response.add([1, 2]);
      await request.response.close();
    };
    await expectLater(download(), throwsA(isA<MusicSearchException>()));
    expect(await destination.exists(), isFalse);
  });

  test('follows bounded redirects without leaking credentials', () async {
    handler = (request) async {
      expect(request.headers.value(HttpHeaders.authorizationHeader), isNull);
      expect(request.headers.value(HttpHeaders.cookieHeader), isNull);
      if (request.uri.path == '/song.mp3') {
        request.response.statusCode = 302;
        request.response.headers.set(HttpHeaders.locationHeader, '/actual.mp3');
      } else {
        request.response.contentLength = bytes.length;
        request.response.add(bytes);
      }
      await request.response.close();
    };
    await download();
    expect(await destination.length(), bytes.length);
  });

  test('cancellation preserves partial and does not create target', () async {
    final cancellation = DownloadCancellation();
    final started = Completer<void>();
    handler = (request) async {
      try {
        request.response.contentLength = bytes.length * 2;
        request.response.add(bytes);
        await request.response.flush();
        started.complete();
      } catch (_) {}
    };
    final future = download(
      size: bytes.length * 2,
      checksum: '',
      cancellation: cancellation,
    );
    final assertion = expectLater(future, throwsA(isA<DownloadCancelled>()));
    await started.future;
    await Future<void>.delayed(const Duration(milliseconds: 30));
    cancellation.cancel();
    await assertion;
    expect(await destination.exists(), isFalse);
    expect(await partial.exists(), isTrue);
  });
}
