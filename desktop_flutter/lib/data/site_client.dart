import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../domain/online_music.dart';
import 'account_store.dart';

class SiteRequest {
  const SiteRequest(this.uri, this.method, this.token, {this.body});
  final Uri uri;
  final String method;
  final String token;
  final Map<String, dynamic>? body;
}

typedef SiteTransport =
    Future<Map<String, dynamic>> Function(SiteRequest request);

class SiteClient {
  SiteClient({SiteTransport? transport}) : _transport = transport ?? _send;
  final SiteTransport _transport;

  Future<Map<String, dynamic>> _request(
    String path,
    String token, {
    String method = 'GET',
    Map<String, dynamic>? body,
    Map<String, String>? query,
    int expectedCode = 0,
    bool allowAnonymous = false,
  }) async {
    if (!allowAnonymous || token.isNotEmpty) {
      AccountStore.validateSiteToken(token);
    }
    try {
      final response = await _transport(
        SiteRequest(
          Uri.https('player.nekoh2o.top', '/$path', query),
          method,
          token,
          body: body,
        ),
      );
      if (response['code'] != expectedCode) {
        throw const MusicSearchException('本站未确认操作成功，请检查登录状态后重试。');
      }
      return response;
    } on HandshakeException {
      throw const MusicSearchException(
        '本站 TLS 证书验证失败。请联系站点维护者检查证书；客户端不会忽略安全校验。',
      );
    } on IOException {
      throw const MusicSearchException('无法连接本站，请检查网络。');
    } on TimeoutException {
      throw const MusicSearchException('本站请求超时，请稍后重试。');
    } on FormatException {
      throw const MusicSearchException('本站响应格式异常，未修改本地数据。');
    }
  }

  Future<Map<String, dynamic>> me(String token) async {
    final body = await _request('auth/sso/me', token);
    if (body['user'] is! Map<String, dynamic>) {
      throw const MusicSearchException('登录令牌无效或已过期。');
    }
    return body['user'] as Map<String, dynamic>;
  }

  Future<Map<String, dynamic>> pull(String token) async {
    final body = await _request('user/data', token);
    if (body['data'] == null) return {};
    if (body['data'] is! Map<String, dynamic>) {
      throw const MusicSearchException('云端数据格式异常。');
    }
    return body['data'] as Map<String, dynamic>;
  }

  Future<void> push(String token, Map<String, dynamic> data) async {
    await _request('user/data', token, method: 'PUT', body: data);
  }

  Future<void> logout(String token) async {
    await _request('auth/sso/logout', token, method: 'POST');
  }

  Future<List<Uri>> wallpapers() async {
    final body = await _request(
      'local/wallpaper/list',
      '',
      query: {'orientation': 'horizontal'},
      expectedCode: 200,
      allowAnonymous: true,
    );
    final rows = body['data'];
    if (rows is! List) throw const MusicSearchException('壁纸列表格式异常。');
    return [
          for (final row in rows.whereType<Map<String, dynamic>>())
            Uri.https(
              'player.nekoh2o.top',
              '/',
            ).resolve(row['url']?.toString() ?? ''),
        ]
        .where(
          (uri) =>
              uri.scheme == 'https' &&
              uri.host == 'player.nekoh2o.top' &&
              uri.port == 443 &&
              uri.userInfo.isEmpty &&
              uri.path.startsWith('/local/wallpaper/') &&
              !uri.path.contains('..'),
        )
        .toList();
  }

  Future<Map<String, dynamic>> commentRequest(
    String path,
    String token, {
    String method = 'GET',
    Map<String, String>? query,
    Map<String, dynamic>? body,
  }) {
    if (!RegExp(r'^comments(?:/[A-Za-z0-9_-]+(?:/like)?)?$').hasMatch(path)) {
      throw const MusicSearchException('评论地址无效。');
    }
    return _request(
      path,
      token,
      method: method,
      query: query,
      body: body,
      expectedCode: 200,
      allowAnonymous: method == 'GET',
    );
  }

  static Future<Map<String, dynamic>> _send(SiteRequest request) async {
    if (request.uri.scheme != 'https' ||
        request.uri.host != 'player.nekoh2o.top' ||
        request.uri.port != 443 ||
        request.uri.userInfo.isNotEmpty) {
      throw const MusicSearchException('拒绝向非本站地址发送登录凭据。');
    }
    final client = HttpClient()
      ..connectionTimeout = const Duration(seconds: 15);
    try {
      return await (() async {
        final outgoing = await client.openUrl(request.method, request.uri);
        outgoing.followRedirects = false;
        if (request.token.isNotEmpty) {
          outgoing.headers.set(
            HttpHeaders.authorizationHeader,
            'Bearer ${request.token}',
          );
        }
        if (request.body != null) {
          outgoing.headers.contentType = ContentType.json;
          outgoing.write(jsonEncode(request.body));
        }
        final response = await outgoing.close();
        if (response.statusCode == 204 &&
            request.uri.path == '/auth/sso/logout') {
          return {'code': 0};
        }
        if (response.statusCode != 200) {
          throw MusicSearchException(
            '本站请求失败（HTTP ${response.statusCode}），未发送至重定向地址。',
          );
        }
        final bytes = <int>[];
        await for (final chunk in response) {
          if (bytes.length + chunk.length > 8 * 1024 * 1024) {
            throw const MusicSearchException('云端数据超过 8MB，已停止读取。');
          }
          bytes.addAll(chunk);
        }
        final result = jsonDecode(utf8.decode(bytes));
        if (result is! Map<String, dynamic>) {
          throw const FormatException('Expected an object');
        }
        return result;
      })().timeout(const Duration(seconds: 30));
    } finally {
      client.close(force: true);
    }
  }
}
