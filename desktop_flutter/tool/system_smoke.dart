import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

const channel = MethodChannel('top.nekoh2o/desktop');
const trackId = '/top/nekoh2o/track/system_smoke';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    const MaterialApp(
      home: Scaffold(body: Center(child: Text('NekoPlayer 系统集成验证'))),
    ),
  );
  if (!Platform.isLinux) {
    stderr.writeln('This smoke test requires a Linux desktop session.');
    exit(2);
  }
  try {
    await Future<void>.delayed(const Duration(milliseconds: 800));
    final capabilities = await channel.invokeMapMethod<String, dynamic>(
      'initialize',
    );
    if (capabilities?['media'] != true || capabilities?['lyrics'] != true) {
      throw StateError('Native integrations unavailable');
    }
    final received = <String, Completer<Map>>{
      'loop': Completer<Map>(),
      'seek': Completer<Map>(),
    };
    channel.setMethodCallHandler((call) async {
      if (call.method != 'action' || call.arguments is! Map) return;
      final arguments = call.arguments as Map;
      final completer = received[arguments['action']];
      if (completer != null && !completer.isCompleted) {
        completer.complete(arguments);
      }
    });
    final state = <String, dynamic>{
      'trackId': trackId,
      'title': '系统测试曲',
      'artist': 'Neko',
      'album': 'Smoke',
      'playing': true,
      'hasTrack': true,
      'position': 1000000,
      'duration': 30000000,
      'volume': .5,
      'rate': 1.0,
      'loop': 'None',
      'shuffle': false,
      'lyricsVisible': true,
      'lyric': '桌面歌词 < & 安全转义',
      'nextLyric': '下一句歌词',
      'lyricsLocked': false,
      'lyricsSize': 30.0,
      'lyricsOpacity': .9,
    };
    await channel.invokeMethod<void>('state', state);
    await Future<void>.delayed(const Duration(milliseconds: 400));
    final trayEnabled = await channel.invokeMethod<bool>('configureTray', {
      'closeToTray': true,
    });
    final trayDisabled = await channel.invokeMethod<bool>('configureTray', {
      'closeToTray': false,
    });
    if (trayDisabled != false) throw StateError('Tray opt-out failed');
    stdout.writeln(
      'Native tray available: ${trayEnabled == true}; close-to-tray reset to off.',
    );
    final destination = 'org.mpris.MediaPlayer2.NekoPlayer.instance$pid';
    Future<String> dbus(String method, List<String> arguments) async {
      final result = await Process.run('gdbus', [
        'call',
        '--session',
        '--dest',
        destination,
        '--object-path',
        '/org/mpris/MediaPlayer2',
        '--method',
        method,
        ...arguments,
      ]);
      if (result.exitCode != 0) {
        throw StateError('D-Bus call failed: ${result.stderr}');
      }
      return '${result.stdout}';
    }

    final metadata = await dbus('org.freedesktop.DBus.Properties.Get', [
      'org.mpris.MediaPlayer2.Player',
      'Metadata',
    ]);
    if (!metadata.contains('系统测试曲') || !metadata.contains(trackId)) {
      throw StateError('Metadata mismatch');
    }
    await dbus('org.freedesktop.DBus.Properties.Set', [
      'org.mpris.MediaPlayer2.Player',
      'LoopStatus',
      "<'Track'>",
    ]);
    final loop = await received['loop']!.future.timeout(
      const Duration(seconds: 3),
    );
    if (loop['value'] != 'Track') throw StateError('Loop callback mismatch');
    await dbus('org.mpris.MediaPlayer2.Player.SetPosition', [
      trackId,
      '2500000',
    ]);
    final seek = await received['seek']!.future.timeout(
      const Duration(seconds: 3),
    );
    if (seek['value'] != 2500000) throw StateError('Seek callback mismatch');
    await channel.invokeMethod<void>('state', {
      ...state,
      'lyricsLocked': true,
      'lyricsSize': 42.0,
    });
    await Future<void>.delayed(const Duration(milliseconds: 400));
    await channel.invokeMethod<void>('state', {
      ...state,
      'lyricsVisible': false,
    });
    await channel.invokeMethod<void>('dispose');
    stdout.writeln(
      'PASS: native MPRIS metadata, loop/seek callbacks, lyrics update/lock/hide.',
    );
    exit(0);
  } catch (failure) {
    stderr.writeln('FAIL: $failure');
    exit(1);
  }
}
