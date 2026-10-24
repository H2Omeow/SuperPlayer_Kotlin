import 'dart:async';
import 'dart:convert';
import 'dart:ffi';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/audio_effects.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';
import 'package:nekoplayer_desktop/playback/dsp_playback_engine.dart';
import 'package:nekoplayer_desktop/playback/pcm_sink.dart';

class LoopbackAudioSource implements OnlineAudioSource {
  LoopbackAudioSource(this.uri);
  final Uri uri;
  @override
  Future<ResolvedAudio> resolve(
    OnlineSong song, {
    String preferred = 'exhigh',
  }) async => ResolvedAudio(uri, 'standard');
  @override
  Future<String> lyrics(OnlineSong song) async => '';
}

Future<File> toneFile(Directory directory) async {
  final bytes = ByteData(44 + 48000 * 4);
  void text(int offset, String value) {
    for (var index = 0; index < value.length; index++) {
      bytes.setUint8(offset + index, value.codeUnitAt(index));
    }
  }

  text(0, 'RIFF');
  bytes.setUint32(4, bytes.lengthInBytes - 8, Endian.little);
  text(8, 'WAVEfmt ');
  bytes.setUint32(16, 16, Endian.little);
  bytes.setUint16(20, 1, Endian.little);
  bytes.setUint16(22, 2, Endian.little);
  bytes.setUint32(24, 48000, Endian.little);
  bytes.setUint32(28, 192000, Endian.little);
  bytes.setUint16(32, 4, Endian.little);
  bytes.setUint16(34, 16, Endian.little);
  text(36, 'data');
  bytes.setUint32(40, 48000 * 4, Endian.little);
  return File(
    '${directory.path}/tone.wav',
  ).writeAsBytes(bytes.buffer.asUint8List());
}

void serveTone(
  HttpServer server,
  List<int> bytes, {
  void Function()? onRequest,
}) {
  server.listen((request) async {
    onRequest?.call();
    try {
      request.response.headers.contentType = ContentType('audio', 'wav');
      request.response.headers.set(HttpHeaders.acceptRangesHeader, 'bytes');
      final range = request.headers.value(HttpHeaders.rangeHeader);
      final match = range == null
          ? null
          : RegExp(r'^bytes=(\d+)-(\d*)$').firstMatch(range);
      final start = match == null ? 0 : int.parse(match[1]!);
      final end = match == null || match[2]!.isEmpty
          ? bytes.length - 1
          : int.parse(match[2]!).clamp(0, bytes.length - 1);
      if (start > end) {
        request.response.statusCode = HttpStatus.requestedRangeNotSatisfiable;
      } else {
        if (match != null) {
          request.response.statusCode = HttpStatus.partialContent;
          request.response.headers.set(
            HttpHeaders.contentRangeHeader,
            'bytes $start-$end/${bytes.length}',
          );
        }
        request.response.contentLength = end - start + 1;
        request.response.add(bytes.sublist(start, end + 1));
      }
      await request.response.close();
    } on HttpException {
      return;
    } on SocketException {
      return;
    }
  }, onError: (Object _) {});
}

void main() {
  test('DSP only permits required protocols and explicitly verifies HTTPS', () {
    final engine = DspPlaybackEngine();
    expect(engine.inputOptions(Uri.parse('file:///music/test.wav')), [
      '-protocol_whitelist',
      'file,pipe',
    ]);
    expect(engine.inputOptions(Uri.parse('https://audio.example/music')), [
      '-protocol_whitelist',
      'http,https,tcp,tls',
      '-tls_verify',
      '1',
      '-rw_timeout',
      '15000000',
    ]);
    expect(engine.inputOptions(Uri.parse('http://audio.example/music')), [
      '-protocol_whitelist',
      'http,https,tcp,tls',
      '-rw_timeout',
      '15000000',
    ]);
    engine.dispose();
  });

  test(
    'effects preserve 21 original presets and reject unimplemented mastering IDs',
    () {
      expect(AudioEffects.presets, hasLength(21));
      for (final preset in AudioEffects.presets.values) {
        expect(preset, hasLength(10));
      }
      final values = AudioEffects(
        eq: [double.nan, 99, -99],
        bass: 200,
        width: -1,
        mastering: 14,
      ).values;
      expect(values.take(4), [0, 15, -15, 0]);
      expect(values[10], 100);
      expect(values[11], 0);
      expect(values[16], 0);
      expect(
        AudioEffects.fromJson(
          const AudioEffects(mastering: 34).toJson(),
        ).mastering,
        34,
      );
      expect(
        () => AudioEffects.fromJson({
          'version': 1,
          'values': [1],
        }),
        throwsFormatException,
      );
    },
  );

  final nativePath = Platform.environment['NEKO_DSP_LIBRARY'];
  group(
    'actual C++ PCM sink',
    skip: nativePath == null
        ? 'Set NEKO_DSP_LIBRARY to exercise compiled DSP without an audio device.'
        : false,
    () {
      late DynamicLibrary library;
      late PcmSink sink;
      late int Function(Pointer<Void>, Pointer<Int16>, int) render;
      setUp(() {
        library = DynamicLibrary.open(nativePath!);
        sink = PcmSink(library: library, silent: true);
        render = library
            .lookupFunction<
              Int32 Function(Pointer<Void>, Pointer<Int16>, Int32),
              int Function(Pointer<Void>, Pointer<Int16>, int)
            >('neko_sink_render_test');
      });
      tearDown(() => sink.dispose());

      test(
        'ring buffer is bounded, stereo-aligned, paused and drained correctly',
        () {
          final input = calloc<Int16>(96000);
          final output = calloc<Int16>(2048);
          try {
            for (var index = 0; index < 96000; index++) {
              input[index] = 1000;
            }
            expect(sink.write(input, 96000), 96000);
            expect(sink.write(input, 2), 0);
            expect(sink.write(input, 1), -1);
            expect(render(sink.handle, output, 1024), 0);
            expect(output[0], 0);
            expect(sink.playedFrames, 0);
            sink.pause(false);
            expect(render(sink.handle, output, 1024), 0);
            expect(output[0], closeTo(750, 2));
            expect(sink.playedFrames, 1024);
            expect(sink.bufferedSamples, 96000 - 2048);
            expect(sink.write(input, 2048), 2048);
          } finally {
            calloc.free(input);
            calloc.free(output);
          }
        },
      );

      test(
        'original DSP changes PCM and every implemented mastering chain remains bounded',
        () {
          final input = calloc<Int16>(96000);
          final output = calloc<Int16>(96000);
          try {
            for (var frame = 0; frame < 48000; frame++) {
              final value = (sin(frame * 2 * pi * 80 / 48000) * 8000).round();
              input[frame * 2] = value;
              input[frame * 2 + 1] = value;
            }
            sink.pause(false);
            sink.volume(1);
            sink.configure(const AudioEffects(bass: 80, wet: 30, width: 50));
            expect(sink.write(input, 96000), 96000);
            expect(render(sink.handle, output, 48000), 0);
            expect(
              List.generate(
                96000,
                (index) => output[index],
              ).where((value) => value != 0),
              isNotEmpty,
            );
            expect(
              List.generate(
                2000,
                (index) => output[index] != input[index],
              ).where((changed) => changed).length,
              greaterThan(100),
            );
            for (var preset = 15; preset <= 34; preset++) {
              sink.configure(AudioEffects(mastering: preset));
              sink.write(input, 96000);
              render(sink.handle, output, 48000);
              expect(
                output
                    .asTypedList(96000)
                    .every((sample) => sample >= -32768 && sample <= 32767),
                isTrue,
              );
            }
          } finally {
            calloc.free(input);
            calloc.free(output);
          }
        },
      );

      for (final remote in [false, true]) {
        test(
          'FFmpeg ${remote ? 'HTTP' : 'file'} playback seeks, changes tempo and completes without a real output device',
          () async {
            final directory = await Directory.systemTemp.createTemp(
              'neko-pcm-test-',
            );
            final file = await toneFile(directory);
            HttpServer? server;
            LoopbackAudioSource? source;
            if (remote) {
              server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
              serveTone(server, await file.readAsBytes());
              source = LoopbackAudioSource(
                Uri.parse('http://127.0.0.1:${server.port}/tone.wav'),
              );
            }
            final sinks = <PcmSink>[];
            final engine = DspPlaybackEngine(
              onlineSource: source,
              sinkFactory: () {
                final next = PcmSink(library: library, silent: true);
                sinks.add(next);
                return next;
              },
            );
            final output = calloc<Int16>(960);
            final ticker = Timer.periodic(const Duration(milliseconds: 10), (
              _,
            ) {
              for (final current in sinks) {
                if (current.bufferedSamples > 0) {
                  render(current.handle, output, 480);
                }
              }
            });
            try {
              await engine.open([
                if (remote)
                  Track.fromOnline(
                    const OnlineSong(
                      provider: MusicProvider.netease,
                      id: '1',
                      title: 'Tone',
                      artist: 'Test',
                    ),
                  )
                else
                  Track(path: file.path, title: 'Tone', artist: 'Test'),
              ], 0);
              expect(engine.duration, const Duration(seconds: 1));
              expect(engine.playing, isTrue);
              await Future<void>.delayed(const Duration(milliseconds: 80));
              await engine.pause();
              final paused = engine.position;
              await Future<void>.delayed(const Duration(milliseconds: 50));
              expect(engine.position, paused);
              await engine.seek(const Duration(milliseconds: 200));
              expect(engine.playing, isFalse);
              await engine.setRate(2);
              expect(engine.position.inMilliseconds, closeTo(200, 25));
              await engine.toggle();
              final deadline = DateTime.now().add(const Duration(seconds: 5));
              while (engine.playing && DateTime.now().isBefore(deadline)) {
                await Future<void>.delayed(const Duration(milliseconds: 20));
              }
              expect(engine.playing, isFalse);
              expect(engine.error, isNull);
              expect(engine.position.inMilliseconds, closeTo(1000, 50));
              await engine.stop();
            } finally {
              ticker.cancel();
              engine.dispose();
              await Future<void>.delayed(const Duration(milliseconds: 50));
              calloc.free(output);
              await server?.close(force: true);
              await directory.delete(recursive: true);
            }
          },
        );
      }

      test('FFmpeg HTTPS rejects untrusted and wrong-host certificates', () async {
        final directory = await Directory.systemTemp.createTemp(
          'neko-decoder-tls-',
        );
        HttpServer? server;
        HttpServer? redirectServer;
        final engine = DspPlaybackEngine();
        try {
          final certificate = '${directory.path}/certificate.pem';
          final key = '${directory.path}/key.pem';
          final created = await Process.run('openssl', [
            'req',
            '-x509',
            '-newkey',
            'rsa:2048',
            '-nodes',
            '-keyout',
            key,
            '-out',
            certificate,
            '-days',
            '1',
            '-subj',
            '/CN=localhost',
            '-addext',
            'subjectAltName=DNS:localhost',
          ]);
          expect(created.exitCode, 0, reason: '${created.stderr}');
          final context = SecurityContext()
            ..useCertificateChain(certificate)
            ..usePrivateKey(key);
          server = await HttpServer.bindSecure(
            InternetAddress.loopbackIPv4,
            0,
            context,
          );
          var requests = 0;
          serveTone(
            server,
            await (await toneFile(directory)).readAsBytes(),
            onRequest: () => requests++,
          );
          redirectServer = await HttpServer.bind(
            InternetAddress.loopbackIPv4,
            0,
          );
          redirectServer.listen((request) async {
            request.response.statusCode = HttpStatus.temporaryRedirect;
            request.response.headers.set(
              HttpHeaders.locationHeader,
              'https://${request.uri.queryParameters['host']}:${server!.port}/tone.wav',
            );
            await request.response.close();
          });
          Future<ProcessResult> probe(
            String host, {
            bool trust = false,
            bool redirect = false,
            bool decode = false,
          }) {
            final uri = redirect
                ? Uri.parse(
                    'http://127.0.0.1:${redirectServer!.port}/redirect?host=$host',
                  )
                : Uri.parse('https://$host:${server!.port}/tone.wav');
            return Process.run(
              DspPlaybackEngine.executable(decode ? 'ffmpeg' : 'ffprobe'),
              [
                '-v',
                'error',
                '-threads',
                '1',
                ...engine.inputOptions(uri),
                if (trust) ...['-ca_file', certificate],
                if (decode) ...[
                  '-nostdin',
                  '-i',
                  uri.toString(),
                  '-vn',
                  '-sn',
                  '-dn',
                  '-threads',
                  '1',
                  '-filter_threads',
                  '1',
                  '-f',
                  's16le',
                  '-acodec',
                  'pcm_s16le',
                  '-ar',
                  '48000',
                  '-ac',
                  '2',
                  'pipe:1',
                ] else ...[
                  '-show_entries',
                  'format=duration',
                  '-of',
                  'json',
                  uri.toString(),
                ],
              ],
              stdoutEncoding: decode ? null : utf8,
            );
          }

          for (final decode in [false, true]) {
            for (final redirect in [false, true]) {
              requests = 0;
              final untrusted = await probe(
                'localhost',
                redirect: redirect,
                decode: decode,
              );
              expect(untrusted.exitCode, isNot(0));
              expect(
                '${untrusted.stderr}'.toLowerCase(),
                contains('certificate'),
              );
              expect(requests, 0);
              final wrongHost = await probe(
                '127.0.0.1',
                trust: true,
                redirect: redirect,
                decode: decode,
              );
              expect(wrongHost.exitCode, isNot(0));
              expect('${wrongHost.stderr}'.toLowerCase(), contains('hostname'));
              expect(requests, 0);
              final trusted = await probe(
                'localhost',
                trust: true,
                redirect: redirect,
                decode: decode,
              );
              expect(trusted.exitCode, 0, reason: '${trusted.stderr}');
              if (decode) {
                expect(trusted.stdout as List<int>, hasLength(192000));
              } else {
                expect('${trusted.stdout}', contains('1.000000'));
              }
              expect(requests, greaterThan(0));
            }
          }
        } finally {
          engine.dispose();
          await redirectServer?.close(force: true);
          await server?.close(force: true);
          await directory.delete(recursive: true);
        }
      });
    },
  );
}
