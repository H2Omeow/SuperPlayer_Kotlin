import 'dart:async';
import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'playback/playback_engine.dart';
import 'player_controller.dart';

class DesktopIntegration extends ChangeNotifier {
  DesktopIntegration(this.player, {MethodChannel? channel})
    : channel = channel ?? const MethodChannel('top.nekoh2o/desktop');
  final PlayerController player;
  final MethodChannel channel;
  bool mediaAvailable = false;
  bool lyricsAvailable = false;
  bool positioningAvailable = false;
  bool trayAvailable = false;
  bool closeToTray = false;
  bool overlay = false;
  bool locked = false;
  double fontSize = 30;
  double opacity = .9;
  String? error;
  bool _disposed = false;
  bool _sending = false;
  bool _dirty = false;
  String? _signature;

  Future<void> initialize() async {
    channel.setMethodCallHandler(_handle);
    try {
      final result = await channel.invokeMapMethod<String, dynamic>(
        'initialize',
      );
      if (_disposed) return;
      mediaAvailable = result?['media'] == true;
      lyricsAvailable = result?['lyrics'] == true;
      positioningAvailable = result?['positioning'] == true;
      trayAvailable = result?['tray'] == true;
      player.addListener(_changed);
      _changed();
    } on MissingPluginException {
      error = '当前运行环境没有桌面系统集成。';
    } catch (_) {
      error = '系统集成初始化失败，不影响普通播放。';
    }
    if (!_disposed) notifyListeners();
  }

  Future<void> _handle(MethodCall call) async {
    if (_disposed) return;
    final arguments = call.arguments is Map ? call.arguments as Map : const {};
    final action = arguments['action'];
    if (call.method == 'trayChanged') {
      trayAvailable = arguments['available'] == true;
      if (!trayAvailable) closeToTray = false;
      notifyListeners();
      return;
    }
    if (call.method == 'overlayClosed') {
      overlay = false;
      notifyListeners();
      return;
    }
    if (call.method != 'action') return;
    switch (action) {
      case 'play':
        if (!player.engine.playing) await player.toggle();
      case 'pause':
        await player.perform(player.engine.pause);
      case 'toggle':
        await player.toggle();
      case 'next':
        await player.perform(player.engine.next);
      case 'previous':
        await player.perform(player.engine.previous);
      case 'stop':
        await player.perform(() async {
          await player.engine.pause();
          await player.engine.seek(Duration.zero);
        });
      case 'seek':
      case 'seekRelative':
        final value = arguments['value'];
        if (value is! num || !value.isFinite || player.current == null) return;
        final duration = player.engine.duration.inMicroseconds;
        if (duration <= 0) return;
        final target =
            (value +
                    (action == 'seekRelative'
                        ? player.engine.position.inMicroseconds
                        : 0))
                .round()
                .clamp(0, duration);
        await player.perform(
          () => player.engine.seek(Duration(microseconds: target)),
        );
      case 'volume':
        final value = arguments['value'];
        if (value is num && value.isFinite) {
          player.setVolume(value.toDouble() * 100);
        }
      case 'rate':
        final value = arguments['value'];
        if (value is num && value.isFinite) player.setRate(value.toDouble());
      case 'loop':
        final mode = switch (arguments['value']) {
          'Track' => RepeatMode.single,
          'Playlist' => RepeatMode.loop,
          'None' => RepeatMode.sequential,
          _ => null,
        };
        if (mode != null) player.setRepeatMode(mode);
      case 'shuffle':
        if (arguments['value'] is bool) {
          player.setRepeatMode(
            arguments['value'] == true
                ? RepeatMode.shuffle
                : RepeatMode.sequential,
          );
        }
    }
  }

  void configureOverlay({
    bool? enabled,
    bool? lock,
    double? size,
    double? alpha,
  }) {
    if (!lyricsAvailable || _disposed) return;
    overlay = enabled ?? overlay;
    locked = lock ?? locked;
    if (size != null && size.isFinite) fontSize = size.clamp(18, 64);
    if (alpha != null && alpha.isFinite) opacity = alpha.clamp(.25, 1);
    _signature = null;
    _changed();
    notifyListeners();
  }

  Future<void> configureTray(bool enabled) async {
    if (_disposed || (enabled && !trayAvailable)) return;
    try {
      final result = await channel.invokeMethod<bool>('configureTray', {
        'closeToTray': enabled,
      });
      if (_disposed) return;
      closeToTray = enabled && result == true && trayAvailable;
      if (enabled && !closeToTray) error = '系统托盘当前不可用，关闭窗口仍会正常退出。';
    } catch (_) {
      if (_disposed) return;
      error = '托盘设置失败，未启用关闭到托盘。';
    }
    notifyListeners();
  }

  Map<String, dynamic> state() {
    final track = player.current;
    final lyricIndex = player.lyricIndex;
    return {
      'trackId': track == null
          ? '/org/mpris/MediaPlayer2/TrackList/NoTrack'
          : '/top/nekoh2o/track/t${sha256.convert(utf8.encode(track.path))}',
      'title': track?.title ?? 'NekoPlayer',
      'artist': track?.artist ?? '',
      'album': track?.online?.album ?? '',
      'playing': player.engine.playing,
      'hasTrack': track != null,
      'position': player.engine.position.inMicroseconds,
      'duration': player.engine.duration.inMicroseconds,
      'volume': player.volume / 100,
      'rate': player.rate,
      'loop': player.repeat == RepeatMode.single
          ? 'Track'
          : player.repeat == RepeatMode.loop
          ? 'Playlist'
          : 'None',
      'shuffle': player.repeat == RepeatMode.shuffle,
      'lyricsVisible': overlay,
      'lyric': lyricIndex >= 0
          ? player.lyrics[lyricIndex].text
          : track?.title ?? 'NekoPlayer',
      'nextLyric': !player.doubleLyrics
          ? ''
          : player.showTranslation &&
                lyricIndex >= 0 &&
                player.lyrics[lyricIndex].translation.isNotEmpty
          ? player.lyrics[lyricIndex].translation
          : lyricIndex + 1 < player.lyrics.length
          ? player.lyrics[lyricIndex + 1].text
          : track?.artist ?? '',
      'lyricsLocked': locked,
      'lyricsSize': fontSize,
      'lyricsOpacity': opacity,
    };
  }

  void _changed() {
    if (_disposed) return;
    _dirty = true;
    if (!_sending) unawaited(_send());
  }

  Future<void> _send() async {
    _sending = true;
    try {
      while (_dirty && !_disposed) {
        _dirty = false;
        final current = state();
        final signature = jsonEncode({
          ...current,
          'position': (current['position'] as int) ~/ 500000,
        });
        if (signature == _signature) continue;
        await channel.invokeMethod<void>('state', current);
        _signature = signature;
      }
    } catch (_) {
      if (!_disposed) {
        error = '系统控制或桌面歌词更新失败；普通播放仍可用。';
        notifyListeners();
      }
    } finally {
      _sending = false;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    player.removeListener(_changed);
    channel.setMethodCallHandler(null);
    unawaited(channel.invokeMethod<void>('dispose').catchError((Object _) {}));
    super.dispose();
  }
}
