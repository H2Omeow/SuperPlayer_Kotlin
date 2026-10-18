import 'dart:async';
import 'dart:ffi';
import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:ffi/ffi.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/audio_effects.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/playback/dsp_playback_engine.dart';
import 'package:nekoplayer_desktop/playback/pcm_sink.dart';

void main() {
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

      test(
        'FFmpeg playback seeks, changes tempo and completes without a real output device',
        () async {
          final directory = await Directory.systemTemp.createTemp(
            'neko-pcm-test-',
          );
          final file = File('${directory.path}/tone.wav');
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
          await file.writeAsBytes(bytes.buffer.asUint8List());
          final sinks = <PcmSink>[];
          final engine = DspPlaybackEngine(
            sinkFactory: () {
              final next = PcmSink(library: library, silent: true);
              sinks.add(next);
              return next;
            },
          );
          final output = calloc<Int16>(960);
          final ticker = Timer.periodic(const Duration(milliseconds: 10), (_) {
            for (final current in sinks) {
              if (current.bufferedSamples > 0) {
                render(current.handle, output, 480);
              }
            }
          });
          try {
            await engine.open([
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
            await directory.delete(recursive: true);
          }
        },
      );
    },
  );
}
