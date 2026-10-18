import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as paths;

import 'data/library_store.dart';
import 'domain/library.dart';
import 'domain/online_music.dart';
import 'playback/playback_engine.dart';
import 'download_controller.dart';
import 'data/cloud_library.dart';

class PlayerController extends ChangeNotifier {
  PlayerController({
    required this.engine,
    required this.store,
    this.onlineSource,
    this.downloads,
  }) {
    engine.addListener(_playbackChanged);
  }

  final PlaybackEngine engine;
  final LibraryStore store;
  final OnlineAudioSource? onlineSource;
  final DownloadController? downloads;
  List<Track> tracks = [];
  Set<String> favorites = {};
  List<String> history = [];
  List<LocalPlaylist> playlists = [];
  List<Track> queue = [];
  List<LyricLine> lyrics = [];
  RepeatMode repeat = RepeatMode.sequential;
  double volume = 75;
  double rate = 1;
  String preferredQuality = 'exhigh';
  bool showTranslation = true;
  bool doubleLyrics = true;
  String? error;
  String? _lyricPath;
  String? _historyPath;
  bool _disposed = false;
  bool _storageReadable = true;
  bool busy = false;
  Future<void> _writes = Future.value();
  Future<void> _commands = Future.value();
  Timer? _sleepTimer;
  DateTime? sleepDeadline;
  PlaybackSession? _pendingSession;
  Timer? _sessionTimer;
  int _savedPosition = -1;

  PlaybackSession? get _session {
    if (_pendingSession != null) return _pendingSession;
    if (queue.isEmpty ||
        engine.currentPath == null ||
        engine.index < 0 ||
        engine.index >= queue.length) {
      return null;
    }
    return PlaybackSession(
      paths: queue.map((track) => track.path).toList(),
      index: engine.index,
      position: engine.position,
    );
  }

  static const extensions = [
    'mp3',
    'flac',
    'wav',
    'm4a',
    'aac',
    'ogg',
    'opus',
    'ape',
    'wma',
    'alac',
  ];

  Track? get current => engine.currentPath == null
      ? null
      : queue.where((track) => track.path == engine.currentPath).firstOrNull;
  List<Track> get recent => history
      .map((path) => tracks.where((track) => track.path == path).firstOrNull)
      .nonNulls
      .toList();
  int get lyricIndex =>
      lyrics.lastIndexWhere((line) => line.time <= engine.position);
  Future<void> get flushed => _writes;

  LibraryData get snapshot => LibraryData(
    tracks: List.of(tracks),
    favorites: Set.of(favorites),
    history: List.of(history),
    playlists: List.of(playlists),
    volume: volume,
    rate: rate,
    preferredQuality: preferredQuality,
    showTranslation: showTranslation,
    doubleLyrics: doubleLyrics,
    repeatMode: repeat.name,
    session: _session,
  );

  Future<void> mergeCloud(Map<String, dynamic> remote) async {
    if (!_storageReadable) {
      throw const MusicSearchException('本地音乐库读取失败，未合并或覆盖原数据。');
    }
    final merged = CloudLibrary.merge(snapshot, CloudLibrary.decode(remote));
    tracks = merged.tracks;
    favorites = merged.favorites;
    history = merged.history;
    playlists = merged.playlists;
    final write = _persist();
    _notify();
    await write;
  }

  Future<void> initialize() async {
    try {
      final encoded = await store.read();
      final library = encoded == null
          ? const LibraryData()
          : LibraryData.decode(encoded);
      tracks = library.tracks;
      favorites = library.favorites.toSet();
      history = library.history.toList();
      playlists = library.playlists.toList();
      volume = library.volume;
      rate = library.rate;
      preferredQuality = library.preferredQuality;
      showTranslation = library.showTranslation;
      doubleLyrics = library.doubleLyrics;
      repeat =
          RepeatMode.values
              .where((mode) => mode.name == library.repeatMode)
              .firstOrNull ??
          RepeatMode.sequential;
      _pendingSession = library.session;
      if (_pendingSession != null) {
        final byPath = {for (final track in tracks) track.path: track};
        queue = _pendingSession!.paths.map((path) => byPath[path]!).toList();
      }
    } catch (_) {
      _storageReadable = false;
      error = '音乐库读取失败，已保留原数据；本次会话的修改不会覆盖它。';
    }
    await perform(() async {
      await engine.setVolume(volume);
      await engine.setRate(rate);
      await engine.setPreferredQuality(preferredQuality);
      await engine.setRepeatMode(repeat);
      if (_pendingSession != null) await _restoreSession(play: false);
    });
    _sessionTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      if (engine.currentPath == null || _pendingSession != null) return;
      final position = engine.position.inSeconds;
      if (position != _savedPosition) {
        _savedPosition = position;
        unawaited(_persist().catchError((Object _) {}));
      }
    });
    _notify();
  }

  Future<void> _restoreSession({required bool play}) async {
    final session = _pendingSession;
    if (session == null || queue.isEmpty) return;
    await engine.open(queue, session.index, play: false);
    final offset =
        engine.duration > Duration.zero && session.position > engine.duration
        ? engine.duration
        : session.position;
    if (offset > Duration.zero) await engine.seek(offset);
    if (play) await engine.toggle();
    _pendingSession = null;
    _notify();
  }

  Future<void> perform(Future<void> Function() action) {
    _commands = _commands.then((_) async {
      if (_disposed) return;
      try {
        await action();
      } catch (failure) {
        error = failure is MusicSearchException
            ? failure.message
            : engine.error ?? '操作未完成，请检查音频文件与播放设备。';
        _notify();
      }
    });
    return _commands;
  }

  Future<void> importFiles(List<String> files) async {
    if (busy || _disposed) return;
    busy = true;
    _notify();
    try {
      final imported = <String, Track>{
        for (final track in tracks) track.path: track,
      };
      for (final file in files) {
        final path = paths.normalize(paths.absolute(file));
        if (!extensions.contains(
          paths.extension(path).replaceFirst('.', '').toLowerCase(),
        )) {
          continue;
        }
        if (!await File(path).exists()) continue;
        final name = paths.basenameWithoutExtension(path);
        final separator = name.indexOf(' - ');
        imported[path] = Track(
          path: path,
          title: separator < 0 ? name : name.substring(separator + 3),
          artist: separator < 0 ? '本地音乐' : name.substring(0, separator),
        );
      }
      tracks = imported.values.toList();
      _persist();
    } catch (_) {
      error = '部分文件无法读取，请检查访问权限。';
    } finally {
      busy = false;
      _notify();
    }
  }

  Future<void> play(List<Track> list, int index) =>
      perform(() => _play(list, index));

  Future<void> _play(List<Track> list, int index) async {
    if (list.isEmpty || index < 0 || index >= list.length) return;
    if (list[index].online == null && !await File(list[index].path).exists()) {
      error = '文件已移动或删除，请重新导入。';
      _notify();
      return;
    }
    final previousQueue = queue;
    queue = List.of(list);
    final imported = {for (final track in tracks) track.path: track};
    for (final track in list.where((track) => track.online != null)) {
      imported[track.path] = track;
    }
    tracks = imported.values.toList();
    error = null;
    _historyPath = null;
    await engine.setRepeatMode(repeat);
    try {
      await engine.open(queue, index);
    } catch (_) {
      queue = previousQueue;
      rethrow;
    }
    _pendingSession = null;
    _persist();
    _notify();
  }

  Future<void> toggle() => _pendingSession != null
      ? perform(() => _restoreSession(play: true))
      : current == null
      ? play(tracks, 0)
      : perform(engine.toggle);

  Future<void> enqueue(Track track) => perform(() async {
    if (queue.isEmpty || current == null) {
      await _play([track], 0);
      return;
    }
    final selected = engine.index;
    final pending = [...queue, track];
    await engine.replaceQueue(pending, selected);
    queue = pending;
    if (track.online != null &&
        !tracks.any((item) => item.path == track.path)) {
      tracks = [...tracks, track];
    }
    _persist();
    _notify();
  });

  Future<void> moveInQueue(int from, int to) => perform(() async {
    if (from < 0 ||
        from >= queue.length ||
        to < 0 ||
        to >= queue.length ||
        from == to) {
      return;
    }
    var selected = engine.index;
    final pending = List<Track>.of(queue);
    pending.insert(to, pending.removeAt(from));
    if (selected == from) {
      selected = to;
    } else if (from < selected && to >= selected) {
      selected--;
    } else if (from > selected && to <= selected) {
      selected++;
    }
    await engine.replaceQueue(pending, selected);
    queue = pending;
    _persist();
    _notify();
  });

  Future<void> removeFromQueue(int index) => perform(() async {
    if (index < 0 || index >= queue.length) return;
    if (queue.length == 1) {
      await engine.clearQueue();
      queue = [];
      _pendingSession = null;
      _persist();
      _notify();
      return;
    }
    final selected = engine.index;
    final pending = List<Track>.of(queue)..removeAt(index);
    if (index == selected) {
      await _play(pending, index.clamp(0, pending.length - 1));
      return;
    }
    await engine.replaceQueue(
      pending,
      index < selected ? selected - 1 : selected,
    );
    queue = pending;
    _persist();
    _notify();
  });

  Future<void> clearQueue() => perform(() async {
    await engine.clearQueue();
    queue = [];
    _pendingSession = null;
    lyrics = [];
    _lyricPath = null;
    _persist();
    _notify();
  });

  void toggleFavorite(Track track) {
    if (!favorites.remove(track.path)) favorites.add(track.path);
    _persist();
    _notify();
  }

  void createPlaylist(String name) {
    if (name.trim().isEmpty) return;
    playlists = [
      ...playlists,
      LocalPlaylist(
        id: DateTime.now().microsecondsSinceEpoch.toString(),
        name: name.trim(),
      ),
    ];
    _persist();
    _notify();
  }

  void deletePlaylist(String id) {
    playlists = playlists.where((playlist) => playlist.id != id).toList();
    _persist();
    _notify();
  }

  void addToPlaylist(String id, Track track) {
    playlists = playlists
        .map(
          (playlist) => playlist.id == id
              ? playlist.withPaths({...playlist.paths, track.path}.toList())
              : playlist,
        )
        .toList();
    _persist();
    _notify();
  }

  void removeFromPlaylist(String id, Track track) {
    playlists = playlists
        .map(
          (playlist) => playlist.id == id
              ? playlist.withPaths(
                  playlist.paths.where((path) => path != track.path).toList(),
                )
              : playlist,
        )
        .toList();
    _persist();
    _notify();
  }

  List<Track> playlistTracks(LocalPlaylist playlist) => playlist.paths
      .map((path) => tracks.where((track) => track.path == path).firstOrNull)
      .nonNulls
      .toList();

  void setVolume(double value) {
    volume = value.clamp(0, 100);
    unawaited(perform(() => engine.setVolume(volume)));
    _persist();
    _notify();
  }

  void setRate(double value) {
    rate = value.clamp(0.5, 2);
    unawaited(perform(() => engine.setRate(rate)));
    _persist();
    _notify();
  }

  void setPreferredQuality(String quality) {
    preferredQuality = quality;
    unawaited(perform(() => engine.setPreferredQuality(quality)));
    _persist();
    _notify();
  }

  void setLyricPreferences({bool? translation, bool? twoLines}) {
    showTranslation = translation ?? showTranslation;
    doubleLyrics = twoLines ?? doubleLyrics;
    _persist();
    _notify();
  }

  void cycleRepeat() {
    setRepeatMode(
      RepeatMode.values[(repeat.index + 1) % RepeatMode.values.length],
    );
  }

  void setRepeatMode(RepeatMode mode) {
    repeat = mode;
    unawaited(perform(() => engine.setRepeatMode(repeat)));
    _persist();
    _notify();
  }

  void setSleepTimer(Duration? duration) {
    _sleepTimer?.cancel();
    sleepDeadline = duration == null ? null : DateTime.now().add(duration);
    if (duration != null) {
      _sleepTimer = Timer(duration, () {
        sleepDeadline = null;
        unawaited(perform(engine.pause));
        _notify();
      });
    }
    _notify();
  }

  void dismissError() {
    error = null;
    _notify();
  }

  void _playbackChanged() {
    final track = current;
    var changed = false;
    if (track?.path != _lyricPath) {
      changed = true;
      _lyricPath = track?.path;
      lyrics = [];
      if (track != null) unawaited(_readLyrics(track));
    }
    if (track != null && engine.playing && _historyPath != track.path) {
      changed = true;
      _historyPath = track.path;
      history = [
        track.path,
        ...history.where((path) => path != track.path),
      ].take(200).toList();
      _persist();
    }
    if (engine.error != null && engine.error != error) {
      error = engine.error;
      changed = true;
    }
    if (changed) _notify();
  }

  Future<void> _readLyrics(Track track) async {
    final path = track.path;
    try {
      String content;
      if (track.online != null) {
        content = await onlineSource?.lyrics(track.online!) ?? '';
      } else {
        final file = File(paths.setExtension(path, '.lrc'));
        content = await file.exists() && await file.length() <= 2 * 1024 * 1024
            ? await file.readAsString()
            : '';
      }
      if (_lyricPath == path && !_disposed) {
        lyrics = parseLrc(content);
        _notify();
      }
    } catch (_) {
      if (_lyricPath == path && !_disposed) {
        lyrics = [];
        _notify();
      }
    }
  }

  Future<void> _persist() {
    if (!_storageReadable) return Future.value();
    final encoded = snapshot.encode();
    final write = _writes.then((_) => store.write(encoded));
    _writes = write.catchError((Object failure) {
      error = '保存音乐库失败，请检查磁盘空间与访问权限。';
      _notify();
    });
    return write;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    unawaited(_persist().catchError((Object _) {}));
    _disposed = true;
    _sessionTimer?.cancel();
    _sleepTimer?.cancel();
    engine.removeListener(_playbackChanged);
    engine.dispose();
    downloads?.dispose();
    super.dispose();
  }
}
