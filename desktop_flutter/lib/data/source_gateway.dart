import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';

import '../domain/online_music.dart';
import 'account_store.dart';
import 'catalog_transport.dart';

class _SourceSession {
  const _SourceSession(this.token, this.id, this.key, this.expires);
  final String token;
  final String id;
  final List<int> key;
  final int expires;
}

class SourceGateway {
  SourceGateway({
    required this.accounts,
    CatalogTransport? transport,
    int Function()? clock,
    String Function()? nonce,
  }) : _transport = transport ?? sendCatalogRequest,
       _clock = clock ?? (() => DateTime.now().millisecondsSinceEpoch),
       _nonce = nonce ?? (() => AccountStore.randomHex(16));

  final AccountStore accounts;
  final CatalogTransport _transport;
  final int Function() _clock;
  final String Function() _nonce;
  _SourceSession? _session;
  Future<_SourceSession>? _pending;
  String? _pendingToken;

  Future<_SourceSession> _credentials(String token) async {
    final cached = _session;
    if (cached != null &&
        cached.token == token &&
        cached.expires > _clock() + 60000) {
      return cached;
    }
    if (_pending != null && _pendingToken == token) return _pending!;
    _pendingToken = token;
    final pending = _createSession(token);
    _pending = pending;
    try {
      return await pending;
    } finally {
      if (_pending == pending) {
        _pending = null;
        _pendingToken = null;
      }
    }
  }

  Future<_SourceSession> _createSession(String token) async {
    final body = await _transport(
      CatalogRequest(
        Uri.https('nekoh2o.top', '/api/music-source/session'),
        headers: {'Authorization': 'Bearer $token'},
        body: '',
      ),
    );
    final hex = body['key']?.toString() ?? '';
    final id = body['keyId']?.toString() ?? '';
    final expires = int.tryParse(body['expires']?.toString() ?? '') ?? 0;
    if (!RegExp(r'^[a-fA-F0-9]{64,256}$').hasMatch(hex) ||
        hex.length.isOdd ||
        id.isEmpty ||
        id.length > 256 ||
        id.contains(RegExp(r'[^\x21-\x7e]')) ||
        expires <= _clock()) {
      throw const MusicSearchException('音源服务会话无效，请重新登录本站后重试。');
    }
    if (accounts.siteToken != token) {
      throw const MusicSearchException('本站账号已切换，请重试。');
    }
    final session = _SourceSession(token, id, [
      for (var index = 0; index < hex.length; index += 2)
        int.parse(hex.substring(index, index + 2), radix: 16),
    ], expires);
    _session = session;
    return session;
  }

  Future<Map<String, dynamic>> request(
    String endpoint,
    Map<String, String> query, {
    bool media = false,
  }) async {
    final token = accounts.siteToken;
    if (token.isEmpty) throw const MusicSearchException('请先登录本站账号后使用惜缘惜梦音源。');
    AccountStore.validateSiteToken(token);
    const endpoints = {
      'usage',
      'music/search',
      'music/url',
      'music/lyric',
      'music/mv/search',
      'music/mv/url',
    };
    if ((!media && !endpoints.contains(endpoint)) ||
        (media && !{'mv/search', 'mv/url'}.contains(endpoint))) {
      throw const MusicSearchException('音源请求地址无效。');
    }
    try {
      final session = await _credentials(token);
      if (accounts.siteToken != token) {
        throw const MusicSearchException('本站账号已切换，请重试。');
      }
      final uri = Uri.https(
        'nekoh2o.top',
        '/api/${media ? 'media' : 'music-source'}/$endpoint',
        query.isEmpty ? null : query,
      );
      final timestamp = '${_clock()}';
      final nonce = _nonce();
      final path = uri.toString().substring('https://nekoh2o.top'.length);
      final canonical = [
        'GET',
        path,
        timestamp,
        nonce,
        sha256.convert(const <int>[]).toString(),
      ].join('\n');
      return await _transport(
        CatalogRequest(
          uri,
          headers: {
            'Authorization': 'Bearer $token',
            'X-Source-Key': session.id,
            'X-Source-Time': timestamp,
            'X-Source-Nonce': nonce,
            'X-Source-Signature': Hmac(
              sha256,
              session.key,
            ).convert(utf8.encode(canonical)).toString(),
          },
        ),
      );
    } on MusicHttpException catch (failure) {
      if (failure.status == 401) _session = null;
      rethrow;
    } on HandshakeException {
      throw const MusicSearchException('音源服务 TLS 证书验证失败，请联系站点维护者；不会忽略安全校验。');
    } on IOException {
      throw const MusicSearchException('无法连接音源服务，请检查网络。');
    } on TimeoutException {
      throw const MusicSearchException('音源服务请求超时，请稍后重试。');
    } on FormatException {
      throw const MusicSearchException('音源服务响应格式异常。');
    }
  }
}
