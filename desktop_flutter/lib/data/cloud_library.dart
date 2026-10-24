import '../domain/library.dart';
import '../domain/online_music.dart';
import '../domain/music_plugin.dart';

class CloudLibrary {
  static List<Map<String, dynamic>> items(Object? value) =>
      value is List ? value.whereType<Map<String, dynamic>>().toList() : [];

  static String songKey(Map<String, dynamic> song) {
    final identity =
        [song['providerMediaId'], song['hash'], song['id']]
            .map((value) => value?.toString() ?? '')
            .where((value) => value.isNotEmpty)
            .firstOrNull ??
        '';
    final source = song['source'] ?? 'netease';
    return '$source:${source == 'kugou' ? identity.toLowerCase() : identity}';
  }

  static Track? decodeSong(Map<String, dynamic> song) {
    final source = song['source'] ?? 'netease';
    MusicPluginSource? plugin;
    if (song['pluginSource'] is Map<String, dynamic>) {
      try {
        plugin = MusicPluginSource.fromJson(
          song['pluginSource'] as Map<String, dynamic>,
          reference: true,
        );
      } catch (_) {
        return null;
      }
    }
    final provider =
        MusicProvider.values
            .where((provider) => provider.sourceId == source)
            .firstOrNull ??
        (plugin != null ? MusicProvider.gatewayKugou : null);
    if (provider == null) return null;
    final id = song['id']?.toString() ?? '';
    final rawHash = song['hash']?.toString() ?? '';
    final hash = source == 'kugou' ? rawHash.toLowerCase() : rawHash;
    if (source == 'netease' && !RegExp(r'^\d+$').hasMatch(id)) return null;
    if (source == 'kugou' && !RegExp(r'^[a-f0-9]{32}$').hasMatch(hash)) {
      return null;
    }
    return Track.fromOnline(
      OnlineSong(
        provider: provider,
        id: provider.gateway == null
            ? id
            : (song['providerMediaId']?.toString().isNotEmpty ?? false)
            ? song['providerMediaId'].toString()
            : hash.isNotEmpty
            ? hash
            : id,
        hash: hash,
        title: song['nm']?.toString() ?? '',
        artist: song['ar']?.toString() ?? '',
        albumId: song['albumId']?.toString() ?? '',
        albumAudioId: song['albumAudioId']?.toString() ?? '0',
        artwork: song['pc']?.toString() ?? '',
        pluginSource: plugin,
        pluginData: Map<String, dynamic>.from(song['pluginData'] as Map? ?? {}),
      ),
    );
  }

  static Map<String, dynamic> encodeSong(Track track) {
    final song = track.online!;
    return {
      'id':
          int.tryParse(song.id) ??
          (song.hash.isEmpty
              ? 0
              : int.tryParse(
                      song.hash.substring(0, song.hash.length.clamp(0, 15)),
                      radix: 16,
                    ) ??
                    0),
      'nm': song.title,
      'ar': song.artist,
      'source': song.pluginSource?.id ?? song.provider.sourceId,
      'hash': song.hash,
      'albumId': song.albumId,
      'albumAudioId': int.tryParse(song.albumAudioId) ?? 0,
      if (song.artwork.isNotEmpty) 'pc': song.artwork,
      if (song.provider.gateway != null) ...{
        'providerSource': song.provider.gateway,
        'providerMediaId': song.id,
      },
      if (song.pluginSource != null)
        'pluginSource': song.pluginSource!.toJson(includeScript: false),
      if (song.pluginData.isNotEmpty) 'pluginData': song.pluginData,
    };
  }

  static LibraryData decode(Map<String, dynamic> body) {
    for (final key in ['favorites', 'history', 'playlists']) {
      final value = body[key];
      if (value != null &&
          (value is! List ||
              value.any((item) => item is! Map<String, dynamic>))) {
        throw const FormatException('云端音乐库格式异常。');
      }
    }
    for (final playlist in items(body['playlists'])) {
      final songs = playlist['songs'];
      if (songs != null &&
          (songs is! List ||
              songs.any((song) => song is! Map<String, dynamic>))) {
        throw const FormatException('云端歌单格式异常。');
      }
    }
    final tracks = <String, Track>{};
    List<String> collect(Object? value) {
      final selected = <String>[];
      for (final song in items(value)) {
        final track = decodeSong(song);
        if (track == null) continue;
        tracks[track.path] = track;
        selected.add(track.path);
      }
      return selected.toSet().toList();
    }

    final favorites = collect(body['favorites']).toSet();
    final history = collect(body['history']);
    final playlists = [
      for (final playlist in items(body['playlists']))
        if ((playlist['id']?.toString() ?? '').isNotEmpty)
          LocalPlaylist(
            id: playlist['id'].toString(),
            name: playlist['name']?.toString() ?? '云端歌单',
            paths: collect(playlist['songs']),
          ),
    ];
    return LibraryData(
      tracks: tracks.values.toList(),
      favorites: favorites,
      history: history,
      playlists: playlists,
    );
  }

  static LibraryData merge(LibraryData local, LibraryData remote) {
    final tracks = {
      for (final track in remote.tracks) track.path: track,
      for (final track in local.tracks) track.path: track,
    };
    final playlists = {
      for (final playlist in local.playlists) playlist.id: playlist,
    };
    for (final remotePlaylist in remote.playlists) {
      final current = playlists[remotePlaylist.id];
      playlists[remotePlaylist.id] = current == null
          ? remotePlaylist
          : current.withPaths(
              {...current.paths, ...remotePlaylist.paths}.toList(),
            );
    }
    return LibraryData(
      tracks: tracks.values.toList(),
      favorites: {...local.favorites, ...remote.favorites},
      history: {...local.history, ...remote.history}.take(200).toList(),
      playlists: playlists.values.toList(),
      volume: local.volume,
      rate: local.rate,
      preferredQuality: local.preferredQuality,
      showTranslation: local.showTranslation,
      doubleLyrics: local.doubleLyrics,
      repeatMode: local.repeatMode,
      session: local.session,
    );
  }

  static Map<String, dynamic> encode(
    LibraryData local,
    Map<String, dynamic> remote,
  ) {
    final tracks = {for (final track in local.tracks) track.path: track};
    List<Map<String, dynamic>> songs(Iterable<String> keys) => keys
        .map((key) => tracks[key])
        .whereType<Track>()
        .where((track) => track.online != null)
        .map(encodeSong)
        .toList();
    List<Map<String, dynamic>> union(
      List<Map<String, dynamic>> preferred,
      List<Map<String, dynamic>> previous,
    ) {
      final byKey = {for (final song in previous) songKey(song): song};
      final preferredByKey = {
        for (final song in preferred) songKey(song): song,
      };
      return [
        for (final key in {...preferredByKey.keys, ...byKey.keys})
          {...?byKey[key], ...?preferredByKey[key]},
      ];
    }

    final remotePlaylists = {
      for (final playlist in items(remote['playlists']))
        playlist['id']?.toString() ?? '': playlist,
    };
    final localPlaylists = {
      for (final playlist in local.playlists) playlist.id: playlist,
    };
    return {
      ...remote,
      'favorites': union(songs(local.favorites), items(remote['favorites'])),
      'history': union(
        songs(local.history),
        items(remote['history']),
      ).take(200).toList(),
      'playlists': [
        for (final id in {...localPlaylists.keys, ...remotePlaylists.keys})
          if (id.isNotEmpty)
            {
              ...?remotePlaylists[id],
              'id': id,
              'name':
                  localPlaylists[id]?.name ??
                  remotePlaylists[id]?['name'] ??
                  '云端歌单',
              'songs': union(
                songs(localPlaylists[id]?.paths ?? []),
                items(remotePlaylists[id]?['songs']),
              ),
            },
      ],
    };
  }
}
