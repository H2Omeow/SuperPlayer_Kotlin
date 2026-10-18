import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:path_provider/path_provider.dart';
import 'dart:io';
import 'data/audio_cache.dart';

import 'data/library_store.dart';
import 'data/native_music_catalog.dart';
import 'data/account_store.dart';
import 'download_controller.dart';
import 'playback/effects_playback_engine.dart';
import 'player_controller.dart';
import 'ui/neko_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    MediaKit.ensureInitialized();
    final accounts = AccountStore(storage: SecureCredentialStorage());
    await accounts.initialize();
    AudioCache? cache;
    try {
      final directory = await getApplicationCacheDirectory();
      cache = AudioCache(
        directory: Directory('${directory.path}/audio-v1'),
        store: PreferencesCacheStore(SharedPreferencesAsync()),
      );
      await cache.initialize();
    } catch (_) {}
    final onlineSource = NativeMusicCatalog(accounts: accounts, cache: cache);
    final downloads = DownloadController(
      source: onlineSource,
      store: PreferencesDownloadStore(SharedPreferencesAsync()),
    );
    await downloads.initialize();
    final engine = EffectsPlaybackEngine(onlineSource: onlineSource);
    await engine.initializeEffects();
    final controller = PlayerController(
      engine: engine,
      onlineSource: onlineSource,
      downloads: downloads,
      store: PreferencesLibraryStore(SharedPreferencesAsync()),
    );
    await controller.initialize();
    runApp(NekoApp(controller: controller));
  } catch (_) {
    runApp(
      const MaterialApp(
        home: Scaffold(
          body: Center(
            child: Padding(
              padding: EdgeInsets.all(32),
              child: SelectableText(
                '播放内核初始化失败。\nLinux 请安装与你的系统架构匹配的 libmpv 和 GTK 3；Windows 请检查安装包中的播放依赖。',
                textAlign: TextAlign.center,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
