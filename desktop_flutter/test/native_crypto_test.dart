import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/native_crypto.dart';

void main() {
  test('weapi double AES CBC round-trip uses the native request format', () {
    const secret = 'abcdefghijklmnop';
    final form = Uri.splitQueryString(
      NativeCrypto.weapi({'songId': '123', 'csrf_token': ''}, secret: secret),
    );
    final first = NativeCrypto.decrypt(
      base64Decode(form['params']!),
      secret,
      '0102030405060708',
    );
    final plain = NativeCrypto.decrypt(
      base64Decode(first),
      '0CoJUm6Qyw8W8jud',
      '0102030405060708',
    );
    expect(jsonDecode(plain), {'songId': '123', 'csrf_token': ''});
    expect(form['encSecKey'], hasLength(256));
    expect(
      form['encSecKey'],
      NativeCrypto.rsa(
        secret.split('').reversed.join(),
        NativeCrypto.neteaseRsa,
      ),
    );
  });

  test('device RSA ciphertext and AES payload use native encodings', () {
    for (final key in [NativeCrypto.kugouRsa, NativeCrypto.kugouLiteRsa]) {
      expect(
        NativeCrypto.rsa('{"aes":"secret"}', key, pkcs: true),
        hasLength(256),
      );
      expect(
        NativeCrypto.rsa('{"key":"secret"}', key, padEnd: true),
        hasLength(256),
      );
    }
    const key = '0123456789abcdef';
    final encrypted = NativeCrypto.encrypt('测试设备', key, key);
    expect(NativeCrypto.decrypt(encrypted.bytes, key, key), '测试设备');
    expect(encrypted.base16, matches(r'^[a-f0-9]+$'));
  });
}
