import 'dart:convert';
import 'online_music.dart';

class Track {
  const Track({
    required this.path,
    required this.title,
    required this.artist,
    this.online,
  });

  factory Track.fromOnline(OnlineSong song) => Track(
    path: song.key,
    title: song.title,
    artist: song.artist,
    online: song,
  );

  final String path;
  final String title;
  final String artist;
  final OnlineSong? online;

  factory Track.fromJson(Map<String, dynamic> json) => Track(
    path: json['path'] as String,
    title: json['title'] as String,
    artist: json['artist'] as String,
    online: json['online'] == null
        ? null
        : OnlineSong.fromJson(json['online'] as Map<String, dynamic>),
  );

  Map<String, dynamic> toJson() => {
    'path': path,
    'title': title,
    'artist': artist,
    if (online != null) 'online': online!.toJson(),
  };
}

class LocalPlaylist {
  const LocalPlaylist({
    required this.id,
    required this.name,
    this.paths = const [],
  });

  final String id;
  final String name;
  final List<String> paths;

  LocalPlaylist withPaths(List<String> value) =>
      LocalPlaylist(id: id, name: name, paths: value);

  factory LocalPlaylist.fromJson(Map<String, dynamic> json) => LocalPlaylist(
    id: json['id'] as String,
    name: json['name'] as String,
    paths: (json['paths'] as List).cast<String>().toSet().toList(),
  );

  Map<String, dynamic> toJson() => {'id': id, 'name': name, 'paths': paths};
}

class PlaybackSession {
  const PlaybackSession({
    required this.paths,
    required this.index,
    required this.position,
  });
  final List<String> paths;
  final int index;
  final Duration position;

  factory PlaybackSession.fromJson(Map<String, dynamic> json) {
    final paths = (json['paths'] as List).cast<String>();
    final index = json['index'] as int;
    final milliseconds = json['position'] as int;
    if (paths.isEmpty ||
        paths.length > 100000 ||
        index < 0 ||
        index >= paths.length ||
        milliseconds < 0 ||
        milliseconds > 604800000) {
      throw const FormatException('播放恢复记录无效。');
    }
    return PlaybackSession(
      paths: paths,
      index: index,
      position: Duration(milliseconds: milliseconds),
    );
  }

  Map<String, dynamic> toJson() => {
    'paths': paths,
    'index': index,
    'position': position.inMilliseconds,
  };
}

class LibraryData {
  const LibraryData({
    this.tracks = const [],
    this.favorites = const {},
    this.history = const [],
    this.playlists = const [],
    this.volume = 75,
    this.rate = 1,
    this.preferredQuality = 'exhigh',
    this.showTranslation = true,
    this.doubleLyrics = true,
    this.repeatMode = 'sequential',
    this.session,
  });

  final List<Track> tracks;
  final Set<String> favorites;
  final List<String> history;
  final List<LocalPlaylist> playlists;
  final double volume;
  final double rate;
  final String preferredQuality;
  final bool showTranslation;
  final bool doubleLyrics;
  final String repeatMode;
  final PlaybackSession? session;

  factory LibraryData.decode(String value) {
    final json = jsonDecode(value) as Map<String, dynamic>;
    if (json['version'] != 1) throw const FormatException('未知音乐库格式');
    final unique = <String, Track>{};
    for (final item in json['tracks'] as List) {
      final track = Track.fromJson(item as Map<String, dynamic>);
      unique[track.path] = track;
    }
    final tracks = unique.values.toList();
    final paths = unique.keys.toSet();
    final session = json['session'] == null
        ? null
        : PlaybackSession.fromJson(json['session'] as Map<String, dynamic>);
    return LibraryData(
      tracks: tracks,
      favorites: (json['favorites'] as List)
          .cast<String>()
          .toSet()
          .intersection(paths),
      history: (json['history'] as List)
          .cast<String>()
          .where(paths.contains)
          .toSet()
          .take(200)
          .toList(),
      playlists: (json['playlists'] as List)
          .map((value) => LocalPlaylist.fromJson(value as Map<String, dynamic>))
          .map(
            (playlist) => playlist.withPaths(
              playlist.paths.where(paths.contains).toList(),
            ),
          )
          .toList(),
      volume: (json['volume'] as num).toDouble().clamp(0, 100),
      rate: (json['rate'] as num).toDouble().clamp(0.5, 2),
      preferredQuality: json['preferredQuality'] as String? ?? 'exhigh',
      showTranslation: json['showTranslation'] as bool? ?? true,
      doubleLyrics: json['doubleLyrics'] as bool? ?? true,
      repeatMode: json['repeatMode'] as String? ?? 'sequential',
      session: session != null && session.paths.every(paths.contains)
          ? session
          : null,
    );
  }

  String encode() => jsonEncode({
    'version': 1,
    'tracks': tracks.map((track) => track.toJson()).toList(),
    'favorites': favorites.toList(),
    'history': history,
    'playlists': playlists.map((playlist) => playlist.toJson()).toList(),
    'volume': volume,
    'rate': rate,
    'preferredQuality': preferredQuality,
    'showTranslation': showTranslation,
    'doubleLyrics': doubleLyrics,
    'repeatMode': repeatMode,
    if (session != null) 'session': session!.toJson(),
  });
}

class LyricLine {
  const LyricLine(this.time, this.text, {this.translation = ''});

  final Duration time;
  final String text;
  final String translation;
}

List<LyricLine> parseLrc(String source) {
  final offset =
      int.tryParse(
        RegExp(
              r'\[offset:([+-]?\d+)\]',
              caseSensitive: false,
            ).firstMatch(source)?.group(1) ??
            '',
      ) ??
      0;
  final timestamp = RegExp(r'\[(\d+):(\d{2})(?:[.:](\d{1,3}))?\]');
  final result = <LyricLine>[];
  for (final raw in const LineSplitter().convert(source)) {
    final matches = timestamp.allMatches(raw).toList();
    if (matches.isEmpty) continue;
    final text = raw.substring(matches.last.end).trim();
    if (text.isEmpty) continue;
    for (final match in matches) {
      final seconds = int.parse(match.group(2)!);
      if (seconds >= 60) continue;
      final fraction = (match.group(3) ?? '').padRight(3, '0');
      final milliseconds =
          int.parse(match.group(1)!) * 60000 +
          seconds * 1000 +
          int.parse(fraction) +
          offset;
      result.add(
        LyricLine(Duration(milliseconds: milliseconds.clamp(0, 1 << 53)), text),
      );
    }
  }
  final grouped = <Duration, List<String>>{};
  for (final line in result) {
    grouped.putIfAbsent(line.time, () => []).add(line.text);
  }
  final merged = [
    for (final entry in grouped.entries)
      LyricLine(
        entry.key,
        entry.value.first,
        translation: entry.value
            .skip(1)
            .where((text) => text != entry.value.first)
            .toSet()
            .join('\n'),
      ),
  ];
  merged.sort((left, right) => left.time.compareTo(right.time));
  return merged;
}
