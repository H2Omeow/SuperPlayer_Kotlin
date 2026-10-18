enum MusicProvider {
  netease('网易云'),
  kugou('酷狗'),
  gatewayKugou('惜缘 · 酷狗', gateway: 'kg'),
  gatewayKuwo('惜缘 · 酷我', gateway: 'kw'),
  gatewayNetease('惜缘 · 网易', gateway: 'wy'),
  gatewayTencent('惜缘 · QQ', gateway: 'tx'),
  gatewayMigu('惜缘 · 咪咕', gateway: 'mg'),
  gatewayBilibili('惜缘 · B站', gateway: 'bilibili');

  const MusicProvider(this.label, {this.gateway});
  final String label;
  final String? gateway;
  String get sourceId => gateway == null ? name : 'animemusic-$gateway';
}

class KugouLoginChoice {
  const KugouLoginChoice(this.id, this.name);
  final String id;
  final String name;
}

class KugouAccountSelection implements Exception {
  const KugouAccountSelection(this.accounts);
  final List<KugouLoginChoice> accounts;
}

class OnlineSong {
  const OnlineSong({
    required this.provider,
    required this.id,
    required this.title,
    required this.artist,
    this.album = '',
    this.hash = '',
    this.albumId = '',
    this.albumAudioId = '',
    this.artwork = '',
  });

  final MusicProvider provider;
  final String id;
  final String title;
  final String artist;
  final String album;
  final String hash;
  final String albumId;
  final String albumAudioId;
  final String artwork;

  factory OnlineSong.fromJson(Map<String, dynamic> json) => OnlineSong(
    provider: MusicProvider.values.byName(json['provider'] as String),
    id: json['id'] as String,
    title: json['title'] as String,
    artist: json['artist'] as String,
    album: json['album'] as String? ?? '',
    hash: json['hash'] as String? ?? '',
    albumId: json['albumId'] as String? ?? '',
    albumAudioId: json['albumAudioId'] as String? ?? '',
    artwork: json['artwork'] as String? ?? '',
  );

  Map<String, dynamic> toJson() => {
    'provider': provider.name,
    'id': id,
    'title': title,
    'artist': artist,
    'album': album,
    'hash': hash,
    'albumId': albumId,
    'albumAudioId': albumAudioId,
    'artwork': artwork,
  };

  String get key =>
      '${provider.sourceId}:${provider.gateway != null || hash.isEmpty ? id : hash}';
}

class SearchPage {
  const SearchPage({required this.songs, required this.hasMore});

  final List<OnlineSong> songs;
  final bool hasMore;
}

class MusicSearchException implements Exception {
  const MusicSearchException(this.message);
  final String message;

  @override
  String toString() => message;
}

class MusicHttpException extends MusicSearchException {
  MusicHttpException(this.status) : super('音乐平台请求失败（HTTP $status），请稍后重试。');
  final int status;
}

abstract interface class MusicCatalog {
  Future<SearchPage> search(MusicProvider provider, String keyword, int page);
}

class ResolvedAudio {
  const ResolvedAudio(
    this.uri,
    this.quality, {
    this.size = 0,
    this.format = '',
    this.checksum = '',
  });
  final Uri uri;
  final String quality;
  final int size;
  final String format;
  final String checksum;
}

abstract interface class OnlineAudioSource {
  Future<ResolvedAudio> resolve(OnlineSong song, {String preferred = 'exhigh'});
  Future<String> lyrics(OnlineSong song);
}

abstract interface class OnlineDownloadSource {
  Future<ResolvedAudio> resolveDownload(OnlineSong song, String quality);
}

class LoginQr {
  const LoginQr(this.provider, this.key, this.uri, this.platform);
  final MusicProvider provider;
  final String key;
  final Uri uri;
  final int platform;
}

enum LoginProgress { waiting, scanned, expired, authorized }

class MusicVideo {
  const MusicVideo({
    required this.provider,
    required this.id,
    required this.title,
    required this.artist,
    this.artwork = '',
  });
  final MusicProvider provider;
  final String id;
  final String title;
  final String artist;
  final String artwork;
  String get key => '${provider.sourceId}:$id';
}

class SearchVideoPage {
  const SearchVideoPage(this.videos, this.hasMore);
  final List<MusicVideo> videos;
  final bool hasMore;
}
