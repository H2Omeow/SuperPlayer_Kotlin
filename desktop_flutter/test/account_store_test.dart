import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/account_store.dart';
import 'package:nekoplayer_desktop/data/native_music_catalog.dart';
import 'package:nekoplayer_desktop/domain/online_music.dart';

class MemoryCredentials implements CredentialStorage {
  String? value;
  bool fail = false;
  int writes = 0;
  @override
  Future<String?> read() async => value;
  @override
  Future<void> write(String value) async {
    if (fail) throw StateError('keyring locked');
    writes++;
    this.value = value;
  }
}

void main() {
  test('cloud sessions commit both slots in a single durable write', () async {
    final storage = MemoryCredentials();
    final accounts = AccountStore(storage: storage);
    await accounts.initialize();
    await accounts.update(siteToken: 'cloud-session-12345');
    final writes = storage.writes;
    await accounts.importMissingSessions(
      netease: {'MUSIC_U': 'nc'},
      kugou: [
        {'token': 'standard', 'userid': '1'},
        {'token': 'lite', 'userid': '2'},
      ],
      preferredPlatform: 1,
      expectedSiteToken: accounts.siteToken,
    );
    expect(storage.writes, writes + 1);
    expect(accounts.neteaseLoggedIn, isTrue);
    expect(accounts.kugou(0)['token'], 'standard');
    expect(accounts.kugou(1)['token'], 'lite');
    expect(accounts.platform, 1);
    final restored = AccountStore(storage: storage);
    await restored.initialize();
    expect(restored.kugou(1)['token'], 'lite');
  });

  test(
    'cloud credential validation and storage failure leave every account untouched',
    () async {
      final storage = MemoryCredentials();
      final accounts = AccountStore(storage: storage);
      await accounts.initialize();
      await accounts.update(siteToken: 'cloud-session-12345');
      final before = storage.value;
      await expectLater(
        accounts.importMissingSessions(
          netease: {'MUSIC_U': 'nc'},
          kugou: [
            {'token': 'valid'},
            {'token': 'bad value'},
          ],
          preferredPlatform: 1,
          expectedSiteToken: accounts.siteToken,
        ),
        throwsFormatException,
      );
      expect(accounts.neteaseLoggedIn, isFalse);
      expect(accounts.kugou(0)['token'], isNull);
      expect(storage.value, before);
      storage.fail = true;
      await expectLater(
        accounts.importMissingSessions(
          netease: {'MUSIC_U': 'nc'},
          kugou: [
            {'token': 'valid'},
            {'token': 'lite'},
          ],
          preferredPlatform: 1,
          expectedSiteToken: accounts.siteToken,
        ),
        throwsStateError,
      );
      expect(accounts.neteaseLoggedIn, isFalse);
      expect(accounts.kugou(0)['token'], isNull);
      expect(accounts.kugou(1)['token'], isNull);
      expect(accounts.platform, 0);
      expect(storage.value, before);
    },
  );

  test(
    'queued session guards reject stale imports and site verification',
    () async {
      final accounts = AccountStore();
      await accounts.initialize();
      const previous = 'old-site-session-123';
      const next = 'new-site-session-123';
      await accounts.update(siteToken: previous);
      final switching = accounts.update(siteToken: next);
      final importing = accounts.importMissingSessions(
        netease: {'MUSIC_U': 'old-remote'},
        kugou: [{}, {}],
        preferredPlatform: 1,
        expectedSiteToken: previous,
      );
      await expectLater(importing, throwsFormatException);
      await switching;
      expect(accounts.neteaseLoggedIn, isFalse);
      await expectLater(
        accounts.update(siteToken: previous, expectedSiteToken: previous),
        throwsFormatException,
      );
      expect(accounts.siteToken, next);
    },
  );

  test(
    'platform credentials and devices are isolated and restore independently',
    () async {
      final storage = MemoryCredentials();
      final accounts = AccountStore(storage: storage);
      await accounts.initialize();
      await accounts.update(netease: {'MUSIC_U': 'nc-secret'});
      await accounts.update(
        kugou: {'token': 'standard', 'userid': '1'},
        platform: 0,
      );
      await accounts.update(
        kugou: {'token': 'lite', 'userid': '2'},
        platform: 1,
      );
      final restored = AccountStore(storage: storage);
      await restored.initialize();
      expect(restored.neteaseLoggedIn, isTrue);
      expect(restored.kugou(0)['token'], 'standard');
      expect(restored.kugou(1)['token'], 'lite');
      expect(
        restored.kugou(0)['KUGOU_API_MID'],
        isNot(restored.kugou(1)['KUGOU_API_MID']),
      );
      expect(restored.kugou(0)['KUGOU_API_MID'], matches(r'^\d+$'));
      expect(restored.platform, 1);
      await restored.update(
        kugou: {'dfid': 'device-standard'},
        platform: 0,
        selectPlatform: false,
      );
      expect(restored.platform, 1);
    },
  );

  test(
    'failed writes never advertise successful login and next write can recover',
    () async {
      final storage = MemoryCredentials()..fail = true;
      final accounts = AccountStore(storage: storage);
      await accounts.initialize();
      await expectLater(
        accounts.update(netease: {'MUSIC_U': 'secret'}),
        throwsStateError,
      );
      expect(accounts.neteaseLoggedIn, isFalse);
      storage.fail = false;
      await accounts.update(netease: {'MUSIC_U': 'secret'});
      expect(accounts.neteaseLoggedIn, isTrue);
    },
  );

  test('corrupt credential data is protected from overwrites', () async {
    final storage = MemoryCredentials()..value = 'broken';
    final accounts = AccountStore(storage: storage);
    await accounts.initialize();
    expect(accounts.readable, isFalse);
    await expectLater(
      accounts.update(netease: {'MUSIC_U': 'secret'}),
      throwsStateError,
    );
    expect(storage.value, 'broken');
    expect(storage.writes, 0);
  });

  test(
    'cookie parser rejects header injection and preserves equals in values',
    () {
      expect(AccountStore.parseCookies('Cookie: MUSIC_U=abc==; __csrf=123'), {
        'MUSIC_U': 'abc==',
        '__csrf': '123',
      });
      for (final value in [
        'MUSIC_U=abc\r\nOther: secret',
        'bad name=abc',
        'bad',
        'MUSIC_U=abc\u0000',
        'MUSIC_U=first; MUSIC_U=second',
        'MUSIC_U=null',
        'token=undefined',
      ]) {
        expect(() => AccountStore.parseCookies(value), throwsFormatException);
      }
    },
  );

  test('queued updates preserve concurrent changes', () async {
    final storage = MemoryCredentials();
    final accounts = AccountStore(storage: storage);
    await accounts.initialize();
    await Future.wait([
      accounts.update(netease: {'MUSIC_U': 'nc'}),
      accounts.update(kugou: {'token': 'kg', 'userid': '123'}),
    ]);
    final body = jsonDecode(storage.value!) as Map<String, dynamic>;
    expect((body['netease'] as Map)['MUSIC_U'], 'nc');
    expect(((body['kugou'] as List).first as Map)['token'], 'kg');
  });

  test('Netease QR only commits validated authorized responses', () async {
    var code = 801;
    var valid = false;
    final accounts = AccountStore();
    final catalog = NativeMusicCatalog(
      accounts: accounts,
      transport: (request) async {
        expect(request.uri.host, 'interface.music.163.com');
        if (request.uri.path.endsWith('unikey')) {
          return {'code': 200, 'unikey': 'qr-key'};
        }
        expect(request.captureCookies, isTrue);
        return {
          'code': code,
          '_cookies': {if (valid) 'MUSIC_U': 'secret'},
        };
      },
    );
    final qr = await catalog.createLoginQr(MusicProvider.netease);
    expect(qr.uri.queryParameters['codekey'], 'qr-key');
    expect(await catalog.checkLoginQr(qr), LoginProgress.waiting);
    expect(accounts.neteaseLoggedIn, isFalse);
    code = 803;
    await expectLater(
      catalog.checkLoginQr(qr),
      throwsA(isA<MusicSearchException>()),
    );
    valid = true;
    await catalog.checkLoginQr(qr, accept: () => false);
    expect(accounts.neteaseLoggedIn, isFalse);
    expect(await catalog.checkLoginQr(qr), LoginProgress.authorized);
    expect(accounts.neteaseLoggedIn, isTrue);
    code = 800;
    expect(await catalog.checkLoginQr(qr), LoginProgress.expired);
  });

  test(
    'Kugou QR uses the selected app and cannot commit after switching',
    () async {
      final accounts = AccountStore();
      await accounts.initialize();
      await accounts.update(
        kugou: {'dfid': 'registered', 'vip_token': 'previous-account-vip'},
        platform: 1,
      );
      var status = 1;
      var valid = false;
      final catalog = NativeMusicCatalog(
        accounts: accounts,
        transport: (request) async {
          expect(request.uri.host, 'login-user.kugou.com');
          if (request.uri.path.endsWith('/qrcode')) {
            expect(
              request.uri.queryParameters['qrcode_txt'],
              contains('appid=3116'),
            );
            return {
              'status': 1,
              'data': {'qrcode': 'kg-key'},
            };
          }
          expect(request.uri.queryParameters['appid'], '3116');
          return {
            'status': 1,
            'data': {
              'status': status,
              if (valid) ...{'token': 'secret', 'userid': 123},
            },
          };
        },
      );
      final qr = await catalog.createLoginQr(MusicProvider.kugou);
      expect(qr.platform, 1);
      expect(await catalog.checkLoginQr(qr), LoginProgress.waiting);
      status = 4;
      await expectLater(
        catalog.checkLoginQr(qr),
        throwsA(isA<MusicSearchException>()),
      );
      valid = true;
      await catalog.checkLoginQr(qr, accept: () => false);
      expect(accounts.kugouLoggedIn(1), isFalse);
      await catalog.checkLoginQr(qr);
      expect(accounts.kugouLoggedIn(1), isTrue);
      expect(accounts.kugou(1)['vip_token'], isNull);
      expect(accounts.kugou(1)['dfid'], 'registered');
      expect(accounts.kugouLoggedIn(0), isFalse);
      await accounts.update(platform: 0);
      await expectLater(
        catalog.checkLoginQr(qr),
        throwsA(isA<MusicSearchException>()),
      );
    },
  );
}
