import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:media_kit/media_kit.dart' hide Track;

import '../domain/library.dart';
import '../domain/online_music.dart';
import 'resolving_queue.dart';

enum RepeatMode { sequential, loop, single, shuffle }

abstract class PlaybackEngine extends ChangeNotifier {
  bool get playing;
  bool get buffering;
  Duration get position;
  Duration get duration;
  int get index;
  String? get currentPath;
  String? get error;

  Future<void> open(List<Track> tracks, int index, {bool play = true});
  Future<void> replaceQueue(List<Track> tracks, int index) async {
    throw UnsupportedError('Queue editing is unavailable');
  }

  Future<void> clearQueue() async {
    throw UnsupportedError('Queue clearing is unavailable');
  }

  Future<void> toggle();
  Future<void> pause();
  Future<void> next();
  Future<void> previous();
  Future<void> seek(Duration position);
  Future<void> setVolume(double value);
  Future<void> setRate(double value);
  Future<void> setRepeatMode(RepeatMode mode);
  Future<void> setPreferredQuality(String quality) async {}
}

class MediaKitEngine extends PlaybackEngine {
  MediaKitEngine({OnlineAudioSource? onlineSource}) {
    _queue = ResolvingQueue(
      source: onlineSource,
      load: (uri) async {
        if (_disposed) return;
        await _player.open(Media(uri.toString()), play: false);
      },
      loadAudio: (audio) async {
        if (_disposed) return;
        await _player.open(
          Media(audio.uri.toString(), httpHeaders: audio.headers),
          play: false,
        );
      },
    );
    _subscriptions.addAll([
      _player.stream.playing.listen((value) {
        _playing = value;
        notifyListeners();
      }),
      _player.stream.buffering.listen((value) {
        _buffering = value;
        notifyListeners();
      }),
      _player.stream.position.listen((value) {
        _position = value;
        notifyListeners();
      }),
      _player.stream.duration.listen((value) {
        _duration = value;
        notifyListeners();
      }),
      _player.stream.completed.listen((completed) {
        if (completed) {
          unawaited(_serialize(() => _advance(1, automatic: true)));
        }
      }),
      _player.stream.error.listen((value) {
        _error = '音频无法播放，请确认文件可访问且格式受支持。';
        notifyListeners();
      }),
    ]);
  }

  final Player _player = Player(
    configuration: const PlayerConfiguration(title: 'NekoPlayer'),
  );
  final List<StreamSubscription<dynamic>> _subscriptions = [];
  late final ResolvingQueue _queue;
  Future<void> _commands = Future.value();
  bool _disposed = false;

  Future<void> _serialize(Future<void> Function() action) {
    final command = _commands.then((_) async {
      if (!_disposed) await action();
    });
    _commands = command.catchError((Object failure) {
      if (_disposed) return;
      _error = failure is MusicSearchException
          ? failure.message
          : '音频无法播放，请检查网络、文件与播放设备。';
      notifyListeners();
    });
    return command;
  }

  Future<void> _activate({bool play = true}) async {
    if (_disposed) return;
    _index = _queue.index;
    _currentPath = _queue.current?.path;
    _position = Duration.zero;
    _duration = Duration.zero;
    _error = null;
    notifyListeners();
    if (play) await _player.play();
  }

  Future<void> _advance(int direction, {bool automatic = false}) async {
    if (await _queue.advance(direction, automatic: automatic)) {
      await _activate();
    }
  }

  bool _playing = false;
  bool _buffering = false;
  Duration _position = Duration.zero;
  Duration _duration = Duration.zero;
  int _index = 0;
  String? _currentPath;
  String? _error;

  @override
  bool get playing => _playing;
  @override
  bool get buffering => _buffering;
  @override
  Duration get position => _position;
  @override
  Duration get duration => _duration;
  @override
  int get index => _index;
  @override
  String? get currentPath => _currentPath;
  @override
  String? get error => _error;

  @override
  Future<void> open(List<Track> tracks, int index, {bool play = true}) =>
      _serialize(() async {
        if (index < 0 || index >= tracks.length) return;
        await _queue.open(tracks, index);
        await _activate(play: play);
      });

  @override
  Future<void> replaceQueue(List<Track> tracks, int index) =>
      _serialize(() async {
        _queue.replace(tracks, index);
        _index = index;
        notifyListeners();
      });

  @override
  Future<void> clearQueue() => _serialize(() async {
    await _player.stop();
    _queue.clear();
    _index = 0;
    _currentPath = null;
    _position = Duration.zero;
    _duration = Duration.zero;
    _playing = false;
    notifyListeners();
  });

  @override
  Future<void> toggle() => _player.playOrPause();
  @override
  Future<void> pause() => _player.pause();
  @override
  Future<void> next() => _serialize(() => _advance(1));
  @override
  Future<void> previous() => _serialize(() => _advance(-1));
  @override
  Future<void> seek(Duration position) => _player.seek(position);
  @override
  Future<void> setVolume(double value) => _player.setVolume(value);
  @override
  Future<void> setRate(double value) => _player.setRate(value);
  @override
  Future<void> setRepeatMode(RepeatMode mode) async {
    _queue.repeat = mode;
  }

  @override
  Future<void> setPreferredQuality(String quality) async {
    _queue.preferredQuality = quality;
  }

  @override
  void dispose() {
    _disposed = true;
    for (final subscription in _subscriptions) {
      unawaited(subscription.cancel());
    }
    unawaited(_player.dispose());
    super.dispose();
  }
}
