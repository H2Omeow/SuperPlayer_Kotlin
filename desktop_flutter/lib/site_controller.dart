import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import 'data/account_store.dart';
import 'data/cloud_library.dart';
import 'data/site_client.dart';
import 'domain/online_music.dart';
import 'player_controller.dart';

class SiteController extends ChangeNotifier {
  SiteController({
    required this.accounts,
    required this.player,
    SiteClient? client,
  }) : client = client ?? SiteClient();
  final AccountStore accounts;
  final PlayerController player;
  final SiteClient client;
  Map<String, dynamic>? user;
  bool busy = false;
  String? message;
  bool _disposed = false;
  HttpServer? _server;
  Completer<String>? _callback;

  Future<void> _action(Future<void> Function() work) async {
    if (busy || _disposed) return;
    busy = true;
    message = null;
    _notify();
    try {
      await work();
    } catch (failure) {
      message = failure is MusicSearchException
          ? failure.message
          : failure is FormatException
          ? failure.message
          : failure is TimeoutException
          ? '等待登录超时，请重试。'
          : '操作未完成，请检查系统密钥环与网络后重试。';
    } finally {
      busy = false;
      _notify();
    }
  }

  Future<void> importToken(String value) => _action(() async {
    value = value.trim();
    if (value.startsWith('nekoplayer://auth?')) {
      value = Uri.parse(value).queryParameters['token'] ?? '';
    }
    await _accept(value);
  });

  Future<void> _accept(String token) async {
    final previous = accounts.siteToken;
    AccountStore.validateSiteToken(token);
    final verified = await client.me(token);
    if (_disposed) return;
    await accounts.update(siteToken: token, expectedSiteToken: previous);
    user = verified;
    message = '本站登录成功；云同步需手动触发。';
  }

  Future<void> login(
    Future<bool> Function(Uri uri) launch,
  ) => _action(() async {
    final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    _server = server;
    final path = '/auth/${AccountStore.randomHex(24)}';
    final callback = Completer<String>();
    _callback = callback;
    server.listen((request) async {
      final token = request.uri.queryParameters['token'] ?? '';
      var valid =
          request.method == 'GET' &&
          request.uri.path == path &&
          request.headers.host == '127.0.0.1' &&
          request.headers.port == server.port &&
          !callback.isCompleted;
      try {
        AccountStore.validateSiteToken(token);
      } catch (_) {
        valid = false;
      }
      request.response.statusCode = valid ? 200 : 400;
      request.response.headers.contentType = ContentType.text;
      request.response.headers.set(HttpHeaders.cacheControlHeader, 'no-store');
      request.response.headers.set('Referrer-Policy', 'no-referrer');
      request.response.write(
        valid
            ? 'You may return to NekoPlayer. Your session is being verified.'
            : 'Invalid login callback.',
      );
      await request.response.close();
      if (valid && !callback.isCompleted) callback.complete(token);
    });
    try {
      final redirect = Uri(
        scheme: 'http',
        host: '127.0.0.1',
        port: server.port,
        path: path,
      );
      if (!await launch(
        Uri.https('account.nekoh2o.top', '/login', {
          'redirect': redirect.toString(),
        }),
      )) {
        throw const MusicSearchException('无法打开浏览器，请使用令牌导入。');
      }
      message = '浏览器已打开，等待本站登录；可取消。';
      _notify();
      final token = await callback.future.timeout(const Duration(minutes: 3));
      if (!_disposed && token.isNotEmpty) {
        await _accept(token);
      } else if (!_disposed) {
        message = '已取消登录。';
      }
    } finally {
      await server.close(force: true);
      if (_server == server) {
        _server = null;
        _callback = null;
      }
    }
  });

  void cancelLogin() {
    if (_callback != null && !_callback!.isCompleted) _callback!.complete('');
  }

  Future<void> refresh() => _action(() async {
    user = await client.me(accounts.siteToken);
    message = '本站账号已验证。';
  });

  Future<void> logout() => _action(() async {
    final token = accounts.siteToken;
    await accounts.update(siteToken: '');
    user = null;
    message = '已清除本机本站登录凭据。';
    if (token.isNotEmpty) {
      try {
        await client.logout(token);
      } catch (_) {
        message = '本机已退出；服务端会话注销失败，请在账号中心检查。';
      }
    }
  });

  Future<void> sync({bool includeCredentials = false}) => _action(() async {
    final token = accounts.siteToken;
    final remote = await client.pull(token);
    if (_disposed || token != accounts.siteToken) return;
    CloudLibrary.decode(remote);
    final remoteCookies = remote['kgCookies'] is List
        ? remote['kgCookies'] as List
        : const [];
    final remotePlatform = remote['kgPlatform'] == 1 ? 1 : 0;
    if (includeCredentials) {
      Map<String, String> parse(Object? value) {
        if (value == null || value == '') return {};
        if (value is! String) throw const FormatException('云端登录凭据格式异常。');
        return AccountStore.parseCookies(value);
      }

      final nc = parse(remote['ncCookie']);
      if (remote['kgCookies'] != null &&
          (remote['kgCookies'] is! List || remoteCookies.length > 2)) {
        throw const FormatException('云端酷狗账号槽位无效。');
      }
      final sessions = [
        for (var platform = 0; platform < 2; platform++)
          parse(
            platform < remoteCookies.length ? remoteCookies[platform] : null,
          ),
      ];
      final legacyToken = remote['kgToken'];
      if (legacyToken != null && legacyToken is! String) {
        throw const FormatException('云端酷狗凭据格式异常。');
      }
      if ((sessions[remotePlatform]['token'] ?? '').isEmpty &&
          legacyToken is String &&
          legacyToken.isNotEmpty) {
        sessions[remotePlatform].addAll(parse('token=$legacyToken'));
      }
      await accounts.importMissingSessions(
        netease: nc,
        kugou: sessions,
        preferredPlatform: remotePlatform,
        expectedSiteToken: token,
      );
    }
    if (_disposed || token != accounts.siteToken) return;
    await player.mergeCloud(remote);
    if (_disposed || token != accounts.siteToken) return;
    final merged = CloudLibrary.encode(player.snapshot, remote);
    if (includeCredentials) {
      String cookie(Map<String, String> values) => values.entries
          .map((entry) => '${entry.key}=${entry.value}')
          .join('; ');
      merged['ncCookie'] = accounts.neteaseLoggedIn
          ? cookie(accounts.netease)
          : remote['ncCookie'] ?? '';
      merged['kgCookies'] = [
        for (var platform = 0; platform < 2; platform++)
          (accounts.kugou(platform)['token'] ?? '').isNotEmpty
              ? cookie(accounts.kugou(platform))
              : platform < remoteCookies.length
              ? remoteCookies[platform]
              : '',
      ];
      merged['kgPlatform'] = accounts.platform;
      merged['kgToken'] = accounts.kugou()['token'] ?? remote['kgToken'] ?? '';
    }
    await client.push(token, merged);
    if (_disposed || token != accounts.siteToken) return;
    message =
        '云端数据合并成功；本地文件路径不会上传。${includeCredentials ? '已按你的选择同步平台凭据。' : '未上传本机平台凭据。'}';
  });

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    cancelLogin();
    unawaited(_server?.close(force: true));
    super.dispose();
  }
}
