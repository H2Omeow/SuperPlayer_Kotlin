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
  @override
  bool get playing => delegate.playing;
  @override
  Duration get position => delegate.position;
  @override
  int get index => delegate.index;
  @override
  String? get currentPath => delegate.currentPath;
  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) =>
      delegate.open(tracks, index, play: play);
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
  setUp(() {
    normal = FakeEngine();
    professional = TestProfessional();
    engine = EffectsPlaybackEngine(normal: normal, professional: professional);
  });
  tearDown(() => engine.dispose());

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
