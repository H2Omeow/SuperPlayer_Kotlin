import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/domain/audio_effects.dart';
import 'package:nekoplayer_desktop/domain/library.dart';
import 'package:nekoplayer_desktop/playback/dsp_playback_engine.dart';
import 'package:nekoplayer_desktop/playback/effects_playback_engine.dart';

import 'fakes.dart';

class TestProfessional extends DspPlaybackEngine {
  final delegate = FakeEngine();
  bool failSeek = false;
  int stops = 0;
  int opens = 0;
  @override
  bool get playing => delegate.playing;
  @override
  Duration get position => delegate.position;
  @override
  int get index => delegate.index;
  @override
  String? get currentPath => delegate.currentPath;
  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) async {
    opens++;
    await delegate.open(tracks, index, play: play);
  }

  @override
  Future<void> pause() => delegate.pause();
  @override
  Future<void> toggle() => delegate.toggle();
  @override
  Future<void> seek(Duration position) async {
    if (failSeek) throw StateError('seek failed');
    await delegate.seek(position);
  }

  @override
  Future<void> stop() async {
    stops++;
    await delegate.pause();
  }

  @override
  void dispose() {
    delegate.dispose();
    super.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const tracks = [
    Track(path: '/music/first.mp3', title: 'First', artist: 'Neko'),
    Track(path: '/music/second.mp3', title: 'Second', artist: 'Neko'),
  ];
  late FakeEngine normal;
  late TestProfessional professional;
  late EffectsPlaybackEngine engine;
  String? saved;
  bool failWrite = false;
  setUp(() {
    normal = FakeEngine();
    professional = TestProfessional();
    saved = null;
    failWrite = false;
    engine = EffectsPlaybackEngine(
      normal: normal,
      professional: professional,
      readSettings: () async => saved,
      writeSettings: (value) async {
        if (failWrite) throw StateError('disk full');
        saved = value;
      },
    );
  });
  tearDown(() => engine.dispose());

  test(
    'parameter import preserves ordinary playback and persists without enabling DSP',
    () async {
      await engine.open(tracks, 1);
      await engine.seek(const Duration(seconds: 42));
      await engine.importParameters(
        const AudioEffects(enabled: true, bass: 23, mastering: 34),
      );
      expect(engine.effects.enabled, isFalse);
      expect(engine.effects.bass, 23);
      expect(engine.active, same(normal));
      expect(engine.playing, isTrue);
      expect(engine.position, const Duration(seconds: 42));
      expect(engine.index, 1);
      expect(professional.opens, 0);
      final restored = AudioEffects.fromJson(
        jsonDecode(saved!) as Map<String, dynamic>,
      );
      expect(restored.enabled, isFalse);
      expect(restored.mastering, 34);
    },
  );

  test(
    'parameter import preserves active DSP playback without reopening its stream',
    () async {
      await engine.open(tracks, 1);
      await engine.configure(const AudioEffects(enabled: true));
      await engine.seek(const Duration(seconds: 22));
      await engine.importParameters(const AudioEffects(bass: 11));
      expect(engine.effects.enabled, isTrue);
      expect(engine.active, same(professional));
      expect(engine.playing, isTrue);
      expect(engine.position, const Duration(seconds: 22));
      expect(engine.index, 1);
      expect(professional.opens, 1);
      expect(professional.effects.bass, 11);
    },
  );

  test(
    'parameter import reports persistence failure and supports explicit retry',
    () async {
      failWrite = true;
      await expectLater(
        engine.importParameters(const AudioEffects(bass: 19)),
        throwsStateError,
      );
      expect(saved, isNull);
      expect(engine.effectsError, contains('保存失败'));
      failWrite = false;
      await engine.importParameters(const AudioEffects(bass: 19));
      expect(engine.effectsError, isNull);
      expect(saved, isNotNull);
    },
  );

  test('unreadable settings are protected against parameter import', () async {
    saved = 'corrupt settings';
    await engine.initializeEffects();
    await expectLater(
      engine.importParameters(const AudioEffects(bass: 19)),
      throwsStateError,
    );
    expect(saved, 'corrupt settings');
    expect(engine.effects.bass, 0);
    expect(professional.opens, 0);
  });

  test(
    'parameter import refuses a concurrent switch or disposed engine',
    () async {
      final pending = Completer<void>();
      final other = EffectsPlaybackEngine(
        normal: FakeEngine(),
        professional: TestProfessional(),
        writeSettings: (_) => pending.future,
      );
      final switching = other.configure(const AudioEffects());
      await expectLater(
        other.importParameters(const AudioEffects()),
        throwsStateError,
      );
      pending.complete();
      await switching;
      other.dispose();
      await expectLater(
        other.importParameters(const AudioEffects()),
        throwsStateError,
      );
    },
  );

  test('switch preserves selected track, position and paused state', () async {
    await engine.open(tracks, 1);
    await engine.seek(const Duration(seconds: 12));
    await engine.pause();
    await engine.configure(const AudioEffects(enabled: true));
    expect(engine.active, same(professional));
    expect(engine.index, 1);
    expect(engine.position, const Duration(seconds: 12));
    expect(engine.playing, isFalse);
    expect(normal.playing, isFalse);
    await engine.configure(const AudioEffects());
    expect(engine.active, same(normal));
    expect(engine.position, const Duration(seconds: 12));
    expect(engine.playing, isFalse);
    expect(professional.stops, 1);
  });

  test(
    'failed DSP initialization resumes original engine without overlap',
    () async {
      await engine.open(tracks, 0);
      professional.delegate.failOpen = true;
      await engine.configure(const AudioEffects(enabled: true));
      expect(engine.active, same(normal));
      expect(normal.playing, isTrue);
      expect(professional.playing, isFalse);
      expect(engine.effects.enabled, isFalse);
      expect(engine.changing, isFalse);
      expect(engine.effectsError, contains('已保留原播放模式'));
    },
  );

  test(
    'failed DSP seek stops new output and preserves original offset',
    () async {
      await engine.open(tracks, 1);
      await engine.seek(const Duration(seconds: 40));
      professional.failSeek = true;
      await engine.configure(const AudioEffects(enabled: true));
      expect(normal.playing, isTrue);
      expect(professional.playing, isFalse);
      expect(engine.index, 1);
      expect(engine.position, const Duration(seconds: 40));
      expect(professional.effects.enabled, isFalse);
    },
  );

  test('failure returning to ordinary output restores DSP playback', () async {
    await engine.open(tracks, 0);
    await engine.configure(const AudioEffects(enabled: true));
    await engine.seek(const Duration(seconds: 20));
    normal.failOpen = true;
    await engine.configure(const AudioEffects());
    expect(engine.active, same(professional));
    expect(engine.playing, isTrue);
    expect(normal.playing, isFalse);
    expect(engine.position, const Duration(seconds: 20));
    expect(engine.effects.enabled, isTrue);
  });
}
