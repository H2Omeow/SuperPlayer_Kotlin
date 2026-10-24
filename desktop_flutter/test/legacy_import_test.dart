import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/legacy_import.dart';

List<int> _varint(int value) {
  final result = <int>[];
  while (value >= 128) {
    result.add((value & 127) | 128);
    value >>= 7;
  }
  return [...result, value];
}

List<int> _message(int tag, List<int> bytes) => [
  tag,
  ..._varint(bytes.length),
  ...bytes,
];

List<int> _preferences(Map<String, String> values) => [
  for (final entry in values.entries)
    ..._message(10, [
      ..._message(10, utf8.encode(entry.key)),
      ..._message(18, _message(42, utf8.encode(entry.value))),
    ]),
];

void main() {
  test(
    'Java properties honors escapes, Unicode, continuation and comment lines',
    () {
      final result = LegacyImport.properties(
        latin1.encode(
          '# comment ending in slash\\\n'
          'library = {"name":"\\u97f3\\u4e50"}\n'
          'escaped\\ key\\:=hello\\=world\n'
          'continued: one\\\r\n  two\r\n'
          'even=ends\\\\\n'
          'tab=hello\\tworld\n'
          'duplicate=old\nduplicate=new\n',
        ),
      );
      expect(result['library'], '{"name":"音乐"}');
      expect(result['escaped key:'], 'hello=world');
      expect(result['continued'], 'onetwo');
      expect(result['even'], 'ends\\');
      expect(result['tab'], 'hello\tworld');
      expect(result['duplicate'], 'new');
      expect(
        () => LegacyImport.properties(latin1.encode(r'name=\uXYZ0')),
        throwsFormatException,
      );
    },
  );

  test('Android XML rejects entities, duplicate strings and nested markup', () {
    expect(
      LegacyImport.androidPreferences(
        '<map><string name="my_history">[{&quot;id&quot;:1}]</string></map>',
      )['my_history'],
      '[{"id":1}]',
    );
    expect(
      () => LegacyImport.androidPreferences(
        '<!DOCTYPE map [<!ENTITY secret SYSTEM "file:///etc/passwd">]><map/>',
      ),
      throwsFormatException,
    );
    expect(
      () => LegacyImport.androidPreferences(
        '<map><string name="x">1</string><string name="x">2</string></map>',
      ),
      throwsFormatException,
    );
    expect(
      () => LegacyImport.androidPreferences(
        '<map><string name="x"><b>wrong</b></string></map>',
      ),
      throwsFormatException,
    );
  });

  test('Android XML preserves scalar types and rejects malformed values', () {
    expect(
      LegacyImport.androidPreferences(
        '<map><boolean name="floating_double_row" value="false"/>'
        '<int name="play_index" value="2"/>'
        '<long name="play_position" value="4294967296"/>'
        '<float name="playback_speed" value="1.5"/></map>',
      ),
      {
        'floating_double_row': 'false',
        'play_index': '2',
        'play_position': '4294967296',
        'playback_speed': '1.5',
      },
    );
    for (final content in [
      '<boolean name="x" value="yes"/>',
      '<int name="x" value="1.5"/>',
      '<int name="x" value="2147483648"/>',
      '<int name="x" value="-2147483649"/>',
      '<long name="x" value="missing"/>',
      '<long name="x" value="9223372036854775808"/>',
      '<float name="x" value="NaN"/>',
      '<float name="x"/>',
      '<boolean name="x" value="true">false</boolean>',
      '<string name="x">first</string><int name="x" value="2"/>',
    ]) {
      expect(
        () => LegacyImport.androidPreferences('<map>$content</map>'),
        throwsFormatException,
      );
    }
  });

  test('playback import rejects malformed speed, quality and lyric flags', () {
    for (final values in [
      {'playback_speed': 'NaN'},
      {'playback_speed': '2.5'},
      {'playback_speed': 'invalid'},
      {'audio_quality': '../path'},
      {'floating_translation': 'yes'},
      {'floating_double_row': '1'},
    ]) {
      expect(
        () => LegacyPlaybackPreferences.parse(values),
        throwsFormatException,
      );
    }
  });

  group('explicit file import', () {
    late Directory directory;
    setUp(
      () async => directory = await Directory.systemTemp.createTemp(
        'neko-legacy-test-',
      ),
    );
    tearDown(() async => directory.delete(recursive: true));
    Future<String> write(String name, String content) async {
      final file = File('${directory.path}/$name');
      await file.writeAsString(content);
      return file.path;
    }

    test(
      'settings import is explicit and does not require credentials',
      () async {
        final path = await write(
          'settings.xml',
          '<map><float name="playback_speed" value="1.5"/>'
              '<string name="audio_quality">lossless</string>'
              '<boolean name="floating_double_row" value="false"/>'
              '<boolean name="floating_translation" value="true"/>'
              '<string name="download_dir_uri">content://private/downloads</string>'
              '<string name="nc_cookie">MUSIC_U=must-not-import</string></map>',
        );
        await expectLater(
          LegacyImport.readFiles([path]),
          throwsFormatException,
        );
        final imported = await LegacyImport.readFiles([
          path,
        ], includeSettings: true);
        expect(imported.playback.count, 4);
        expect(imported.playback.rate, 1.5);
        expect(imported.playback.quality, 'lossless');
        expect(imported.playback.twoLines, isFalse);
        expect(imported.playback.translation, isTrue);
        expect(imported.hasCredentials, isFalse);
        expect(imported.library, isEmpty);
      },
    );

    test('typed DataStore settings use the same import path', () async {
      final file = File('${directory.path}/settings.preferences_pb');
      await file.writeAsBytes([
        for (final entry in {
          'playback_speed': [21, 0, 0, 192, 63],
          'floating_double_row': [8, 0],
          'quality_level': _message(42, utf8.encode('hires')),
        }.entries)
          ..._message(10, [
            ..._message(10, utf8.encode(entry.key)),
            ..._message(18, entry.value),
          ]),
      ]);
      final imported = await LegacyImport.readFiles([
        file.path,
      ], includeSettings: true);
      expect(imported.playback.count, 3);
      expect(imported.playback.rate, 1.5);
      expect(imported.playback.twoLines, isFalse);
      expect(imported.playback.quality, 'hires');
    });

    test(
      'desktop library import never copies credentials unless opted in',
      () async {
        final backup = jsonEncode({
          'favorites': [
            {'id': 1, 'nm': 'Song', 'ar': 'Neko'},
          ],
          'history': [],
          'playlists': [],
          'ncCookie': 'MUSIC_U=not-copied',
        });
        final path = await write(
          'preferences.properties',
          'library=$backup\nnc_user=MUSIC_U=secret\n',
        );
        final imported = await LegacyImport.readFiles([path]);
        expect(imported.decoded.favorites, {'netease:1'});
        expect(imported.hasCredentials, isFalse);
        expect(imported.library, isNot(contains('ncCookie')));
        expect(
          await File(path).readAsString(),
          contains('nc_user=MUSIC_U=secret'),
        );
      },
    );

    test(
      'Android backup decodes entity-escaped JSON and reports skipped sources',
      () async {
        final path = await write(
          'user_data.xml',
          '<map><string name="my_favorites">[{&quot;id&quot;:2,&quot;nm&quot;:&quot;Music&quot;},{&quot;id&quot;:9,&quot;source&quot;:&quot;unknown&quot;}]</string><string name="my_playlists">[]</string></map>',
        );
        final imported = await LegacyImport.readFiles([path]);
        expect(imported.decoded.tracks.single.path, 'netease:2');
        expect(imported.skippedSongs, 1);
      },
    );

    test(
      'Android DataStore credentials require opt-in and retain scoped userid',
      () async {
        final file = File('${directory.path}/player_prefs.preferences_pb');
        await file.writeAsBytes(
          _preferences({
            'nc_cookie': 'MUSIC_U=android-session',
            'kg_token': 'android-kugou',
            'kg_userid': '42',
            'kg_dfid': 'android-device',
            'kg_platform': '1',
            'app_token': 'site-token-valid-123',
            'unknown': '猫' * 100,
          }),
        );
        await expectLater(
          LegacyImport.readFiles([file.path]),
          throwsFormatException,
        );
        final imported = await LegacyImport.readFiles([
          file.path,
        ], includeCredentials: true);
        expect(imported.netease!['MUSIC_U'], 'android-session');
        expect(imported.kugou[1], {
          'token': 'android-kugou',
          'userid': '42',
          'dfid': 'android-device',
        });
        expect(imported.siteToken, 'site-token-valid-123');
        expect(imported.platform, 1);
        expect(imported.library, isEmpty);
        final accounts = AccountStore();
        await accounts.initialize();
        expect(await imported.fillMissingAccounts(accounts), 3);
        expect(accounts.kugou(1)['userid'], '42');
        expect(accounts.platform, 1);
        expect(
          await file.readAsBytes(),
          _preferences({
            'nc_cookie': 'MUSIC_U=android-session',
            'kg_token': 'android-kugou',
            'kg_userid': '42',
            'kg_dfid': 'android-device',
            'kg_platform': '1',
            'app_token': 'site-token-valid-123',
            'unknown': '猫' * 100,
          }),
        );
      },
    );

    test(
      'opt-in fills only empty account slots and preserves local selection',
      () async {
        final accountPath = await write(
          'accounts.properties',
          'nc_user=MUSIC_U=old-nc\nsite_token=site-token-valid-123\nkg_platform=1\n',
        );
        final kugouPath = await write(
          'kugou.properties',
          'cookies_0=token=old-kg; userid=1\ncookies_1=token=old-lite; userid=2\n',
        );
        final imported = await LegacyImport.readFiles([
          accountPath,
          kugouPath,
        ], includeCredentials: true);
        final accounts = AccountStore();
        await accounts.initialize();
        await accounts.update(
          netease: {'MUSIC_U': 'local-nc'},
          kugou: {'token': 'partial-local'},
        );
        expect(await imported.fillMissingAccounts(accounts), 2);
        expect(accounts.netease['MUSIC_U'], 'local-nc');
        expect(accounts.kugou(0)['token'], 'partial-local');
        expect(accounts.kugou(1)['token'], 'old-lite');
        expect(accounts.siteToken, 'site-token-valid-123');
        expect(accounts.platform, 0);
        expect(await imported.fillMissingAccounts(accounts), 0);
      },
    );

    test(
      'credential files are rejected by default and invalid tokens fail before import',
      () async {
        final path = await write(
          'accounts.properties',
          'nc_user=MUSIC_U=secret\nsite_token=bad\n',
        );
        await expectLater(
          LegacyImport.readFiles([path]),
          throwsFormatException,
        );
        await expectLater(
          LegacyImport.readFiles([path], includeCredentials: true),
          throwsFormatException,
        );
      },
    );

    test(
      'ambiguous libraries, broken JSON and files above limit are rejected',
      () async {
        final first = await write('first.json', '{"favorites":[]}');
        final second = await write('second.json', '{"history":[]}');
        await expectLater(
          LegacyImport.readFiles([first, second]),
          throwsFormatException,
        );
        await expectLater(
          LegacyImport.readFiles([
            await write('bad.json', '{"history":"bad"}'),
          ]),
          throwsFormatException,
        );
        final large = File('${directory.path}/large.json');
        final output = await large.open(mode: FileMode.write);
        await output.truncate(LegacyImport.maxFileBytes + 1);
        await output.close();
        await expectLater(
          LegacyImport.readFiles([large.path]),
          throwsFormatException,
        );
      },
    );
  });
}
