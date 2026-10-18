import 'dart:convert';
import 'dart:math';

import 'package:encrypt/encrypt.dart';
import 'package:pointycastle/asymmetric/api.dart' show RSAPublicKey;

class NativeCrypto {
  static const neteaseRsa =
      'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDgtQn2JZ34ZC28NWYpAUd98iZ37BUrX/aKzmFbt7clFSs6sXqHauqKWqdtLkF2KexO40H1YTX8z2lSgBBOAxLsvaklV8k4cBFK9snQXE9/DDaFt6Rr7iVZMldczhC0JNgTz+SHXT6CBHuX3e9SdB1Ua44oncaTWz7OBGLbCiK45wIDAQAB';
  static const kugouRsa =
      'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB';
  static const kugouLiteRsa =
      'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB';

  static String randomText(int length) {
    const characters = 'abcdefghijklmnopqrstuvwxyz0123456789';
    final random = Random.secure();
    return List.generate(
      length,
      (_) => characters[random.nextInt(characters.length)],
    ).join();
  }

  static Encrypter _aes(String key) =>
      Encrypter(AES(Key.fromUtf8(key), mode: AESMode.cbc));
  static Encrypted encrypt(String text, String key, String iv) =>
      _aes(key).encrypt(text, iv: IV.fromUtf8(iv));
  static String decrypt(List<int> bytes, String key, String iv) => _aes(
    key,
  ).decrypt(Encrypted.fromBase64(base64Encode(bytes)), iv: IV.fromUtf8(iv));
  static RSAPublicKey _key(String encoded) =>
      RSAKeyParser().parse(
            '-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----',
          )
          as RSAPublicKey;

  static String rsa(
    String text,
    String encoded, {
    bool pkcs = false,
    bool padEnd = false,
  }) {
    final key = _key(encoded);
    final bytes = utf8.encode(text);
    if (pkcs) return Encrypter(RSA(publicKey: key)).encryptBytes(bytes).base16;
    final size = (key.modulus!.bitLength + 7) ~/ 8;
    if (bytes.length > size) throw const FormatException('RSA input too large');
    final padded = [
      ...bytes,
      if (padEnd) ...List.filled(size - bytes.length, 0),
    ];
    final value = BigInt.parse(
      padded.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join(),
      radix: 16,
    );
    return value
        .modPow(key.publicExponent!, key.modulus!)
        .toRadixString(16)
        .padLeft(size * 2, '0');
  }

  static String weapi(Map<String, dynamic> data, {String? secret}) {
    secret ??= randomText(16);
    final first = encrypt(
      jsonEncode(data),
      '0CoJUm6Qyw8W8jud',
      '0102030405060708',
    ).base64;
    return Uri(
      queryParameters: {
        'params': encrypt(first, secret, '0102030405060708').base64,
        'encSecKey': rsa(secret.split('').reversed.join(), neteaseRsa),
      },
    ).query;
  }
}
