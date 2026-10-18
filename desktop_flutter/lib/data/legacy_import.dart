import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:xml/xml.dart';

import '../domain/library.dart';
import 'account_store.dart';
import 'android_datastore.dart';
import 'cloud_library.dart';

class LegacyImport {
  LegacyImport({
    required this.library,
    this.netease,
    this.kugou = const {},
    this.siteToken,
    this.platform,
    this.skippedSongs = 0,
  });
  final Map<String, dynamic> library;
  final Map<String, String>? netease;
  final Map<int, Map<String, String>> kugou;
  final String? siteToken;
  final int? platform;
  final int skippedSongs;
  LibraryData get decoded => CloudLibrary.decode(library);
  bool get hasCredentials =>
      netease != null || kugou.isNotEmpty || siteToken != null;
  static const maxFileBytes = 8 * 1024 * 1024;

  static Future<LegacyImport> readFiles(
    List<String> paths, {
    bool includeCredentials = false,
  }) async {
    if (paths.isEmpty || paths.length > 8) {
      throw const FormatException('请选择 1–8 个旧版备份文件。');
    }
    final values = <String, String>{};
    Map<String, dynamic>? library;
    var total = 0;
    for (final path in paths) {
      final bytes = BytesBuilder(copy: false);
      final file = File(path);
      if (await file.length() > maxFileBytes) {
        throw const FormatException('单个备份不能超过 8MB。');
      }
      await for (final chunk in file.openRead()) {
        total += chunk.length;
        if (total > 32 * 1024 * 1024 ||
            bytes.length + chunk.length > maxFileBytes) {
          throw const FormatException('备份文件过大。');
        }
        bytes.add(chunk);
      }
      final content = bytes.takeBytes();
      final extension = path.toLowerCase().split('.').last;
      if (extension == 'json') {
        if (library != null) throw const FormatException('一次只能导入一份音乐库备份。');
        final decoded = jsonDecode(utf8.decode(content));
        if (decoded is! Map<String, dynamic>) {
          throw const FormatException('备份 JSON 结构无效。');
        }
        final body = decoded['data'] is Map<String, dynamic>
            ? decoded['data'] as Map<String, dynamic>
            : decoded;
        library = {
          for (final key in ['favorites', 'history', 'playlists'])
            if (body.containsKey(key)) key: body[key],
        };
        if (library.isEmpty) throw const FormatException('JSON 中未找到旧版音乐库。');
        if (includeCredentials) {
          for (final key in ['ncCookie', 'kgToken', 'kgPlatform']) {
            if (body[key] != null) values[key] = body[key].toString();
          }
          if (body['kgCookies'] is List) {
            final cookies = body['kgCookies'] as List;
            for (var index = 0; index < cookies.length && index < 2; index++) {
              if (cookies[index] is! String) {
                throw const FormatException('酷狗会话格式异常。');
              }
              values['cookies_$index'] = cookies[index] as String;
            }
          }
        }
      } else if (extension == 'properties' ||
          extension == 'xml' ||
          extension == 'pb' ||
          extension == 'preferences_pb') {
        final parsed = extension == 'properties'
            ? properties(content)
            : extension == 'pb' || extension == 'preferences_pb'
            ? AndroidDataStore.stringPreferences(content)
            : androidPreferences(utf8.decode(content));
        for (final entry in parsed.entries) {
          if (values.containsKey(entry.key)) {
            throw const FormatException('多个备份包含重复配置，请分开导入。');
          }
          values[entry.key] = entry.value;
        }
      } else {
        throw const FormatException(
          '仅支持旧版 .properties、Android .xml / DataStore .pb 或音乐库 .json。',
        );
      }
    }
    final rawLibrary = values['library'];
    if (rawLibrary != null) {
      if (library != null) throw const FormatException('一次只能导入一份音乐库备份。');
      final decoded = jsonDecode(rawLibrary);
      if (decoded is! Map<String, dynamic>) {
        throw const FormatException('音乐库结构无效。');
      }
      library = decoded;
    }
    for (final entry in {
      'my_favorites': 'favorites',
      'my_history': 'history',
      'my_playlists': 'playlists',
    }.entries) {
      if (values.containsKey(entry.key)) {
        library ??= {};
        if (library.containsKey(entry.value)) {
          throw const FormatException('发现重复音乐库，请分开导入。');
        }
        library[entry.value] = jsonDecode(values[entry.key]!);
      }
    }
    final credentials = <int, Map<String, String>>{};
    Map<String, String>? netease;
    String? token;
    int? platform;
    if (includeCredentials) {
      final cookie =
          values['nc_user'] ?? values['ncCookie'] ?? values['nc_cookie'];
      if (cookie != null && cookie.isNotEmpty) {
        netease = AccountStore.parseCookies(cookie);
      }
      for (var index = 0; index < 2; index++) {
        final raw = values['cookies_$index'];
        if (raw != null && raw.isNotEmpty) {
          credentials[index] = AccountStore.parseCookies(raw);
        }
      }
      final selection = values['kg_platform'] ?? values['kgPlatform'];
      if (selection != null) {
        platform = int.tryParse(selection);
        if (platform != 0 && platform != 1) {
          throw const FormatException('酷狗账号槽位无效。');
        }
      }
      final legacyToken = values['kgToken'] ?? values['kg_token'];
      if (legacyToken != null &&
          legacyToken.isNotEmpty &&
          credentials[platform ?? 0] == null) {
        credentials[platform ?? 0] = AccountStore.parseCookies(
          [
            'token=$legacyToken',
            if (values['kg_userid']?.isNotEmpty ?? false)
              'userid=${values['kg_userid']}',
            if (values['kg_dfid']?.isNotEmpty ?? false)
              'dfid=${values['kg_dfid']}',
          ].join('; '),
        );
      }
      token = values['site_token'] ?? values['app_token'];
      if (token != null && token.isNotEmpty) {
        AccountStore.validateSiteToken(token);
      }
    }
    if (library == null &&
        netease == null &&
        credentials.isEmpty &&
        (token == null || token.isEmpty)) {
      throw const FormatException('未找到可导入内容；账号文件需要先勾选导入凭据。');
    }
    final safeLibrary = {
      for (final key in ['favorites', 'history', 'playlists'])
        if (library?.containsKey(key) ?? false) key: library![key],
    };
    final decoded = CloudLibrary.decode(safeLibrary);
    var skipped = 0;
    for (final list in [
      safeLibrary['favorites'],
      safeLibrary['history'],
      ...CloudLibrary.items(
        safeLibrary['playlists'],
      ).map((playlist) => playlist['songs']),
    ]) {
      for (final song in CloudLibrary.items(list)) {
        if (CloudLibrary.decodeSong(song) == null) skipped++;
      }
    }
    if (decoded.tracks.isEmpty && skipped > 0) {
      throw const FormatException('所有歌曲都缺少可识别的音源 ID，未导入。');
    }
    return LegacyImport(
      library: safeLibrary,
      netease: netease,
      kugou: credentials,
      siteToken: token?.isEmpty == true ? null : token,
      platform: platform,
      skippedSongs: skipped,
    );
  }

  Future<int> fillMissingAccounts(AccountStore accounts) async {
    var imported = 0;
    final hasLocalKugou = [
      0,
      1,
    ].any((slot) => (accounts.kugou(slot)['token'] ?? '').isNotEmpty);
    if (!accounts.neteaseLoggedIn && (netease?['MUSIC_U'] ?? '').isNotEmpty) {
      await accounts.update(netease: netease, replace: true);
      imported++;
    }
    for (final entry in kugou.entries) {
      if ((accounts.kugou(entry.key)['token'] ?? '').isEmpty &&
          (entry.value['token'] ?? '').isNotEmpty) {
        await accounts.update(
          kugou: entry.value,
          platform: entry.key,
          replace: true,
          selectPlatform: false,
        );
        imported++;
      }
    }
    if (!hasLocalKugou && kugou.isNotEmpty && platform != null) {
      await accounts.update(platform: platform);
    }
    if (accounts.siteToken.isEmpty && siteToken != null) {
      await accounts.update(siteToken: siteToken);
      imported++;
    }
    return imported;
  }

  static Map<String, String> androidPreferences(String source) {
    if (RegExp(
      r'<!\s*(DOCTYPE|ENTITY)',
      caseSensitive: false,
    ).hasMatch(source)) {
      throw const FormatException('不接受带外部实体的 XML。');
    }
    final root = XmlDocument.parse(source).rootElement;
    if (root.name.qualified != 'map') {
      throw const FormatException('不是 Android SharedPreferences 备份。');
    }
    final values = <String, String>{};
    for (final element in root.childElements) {
      if (element.name.qualified != 'string') continue;
      final key = element.getAttribute('name');
      if (key == null ||
          values.containsKey(key) ||
          element.childElements.isNotEmpty) {
        throw const FormatException('SharedPreferences 字段无效或重复。');
      }
      values[key] = element.innerText;
    }
    return values;
  }

  static Map<String, String> properties(List<int> bytes) {
    final values = <String, String>{};
    var logical = '';
    var continued = false;
    void parse(String line) {
      final trimmed = line.replaceFirst(RegExp(r'^[ \t\f]+'), '');
      if (trimmed.isEmpty ||
          trimmed.startsWith('#') ||
          trimmed.startsWith('!')) {
        return;
      }
      var escaped = false;
      var end = trimmed.length;
      for (var index = 0; index < trimmed.length; index++) {
        final character = trimmed[index];
        if (!escaped &&
            (character == '=' ||
                character == ':' ||
                ' \t\f'.contains(character))) {
          end = index;
          break;
        }
        escaped = !escaped && character == r'\';
      }
      var start = end;
      while (start < trimmed.length && ' \t\f'.contains(trimmed[start])) {
        start++;
      }
      if (start < trimmed.length &&
          (trimmed[start] == '=' || trimmed[start] == ':')) {
        start++;
      }
      while (start < trimmed.length && ' \t\f'.contains(trimmed[start])) {
        start++;
      }
      values[_unescape(trimmed.substring(0, end))] = _unescape(
        trimmed.substring(start),
      );
    }

    for (final physical in latin1.decode(bytes).split(RegExp(r'\r\n|\n|\r'))) {
      if (!continued && RegExp(r'^[ \t\f]*[#!]').hasMatch(physical)) continue;
      final line = continued
          ? physical.replaceFirst(RegExp(r'^[ \t\f]+'), '')
          : physical;
      logical += line;
      var slashes = 0;
      for (
        var index = logical.length - 1;
        index >= 0 && logical[index] == r'\';
        index--
      ) {
        slashes++;
      }
      continued = slashes.isOdd;
      if (continued) {
        logical = logical.substring(0, logical.length - 1);
      } else {
        parse(logical);
        logical = '';
      }
    }
    if (logical.isNotEmpty) parse(logical);
    return values;
  }

  static String _unescape(String source) {
    final result = StringBuffer();
    for (var index = 0; index < source.length; index++) {
      if (source[index] != r'\') {
        result.write(source[index]);
        continue;
      }
      index++;
      if (index >= source.length) break;
      final character = source[index];
      if (character == 'u') {
        if (index + 4 >= source.length) {
          throw const FormatException('Properties Unicode 转义不完整。');
        }
        final code = int.tryParse(
          source.substring(index + 1, index + 5),
          radix: 16,
        );
        if (code == null) {
          throw const FormatException('Properties Unicode 转义无效。');
        }
        result.writeCharCode(code);
        index += 4;
      } else {
        result.write(switch (character) {
          't' => '\t',
          'r' => '\r',
          'n' => '\n',
          'f' => '\f',
          _ => character,
        });
      }
    }
    return result.toString();
  }
}
