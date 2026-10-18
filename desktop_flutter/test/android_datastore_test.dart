import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/data/android_datastore.dart';

List<int> stringField(int tag, String value) {
  final bytes = utf8.encode(value);
  return [tag, bytes.length, ...bytes];
}

List<int> preferenceEntry(String key, List<int> value) {
  final entry = [...stringField(10, key), 18, value.length, ...value];
  return [10, entry.length, ...entry];
}

void main() {
  test('reads AndroidX string preferences, ignoring other value types', () {
    final bytes = Uint8List.fromList([
      ...preferenceEntry('nc_cookie', stringField(42, 'MUSIC_U=old')),
      ...preferenceEntry('kg_platform', stringField(42, '1')),
      ...preferenceEntry('boolean', [8, 1]),
      ...preferenceEntry('float', [21, 0, 0, 128, 63]),
      ...preferenceEntry('integer', [24, 42]),
      ...preferenceEntry('long', [32, 42]),
      ...preferenceEntry('set', [50, 3, 10, 1, 97]),
      ...preferenceEntry('double', [57, 0, 0, 0, 0, 0, 0, 240, 63]),
      ...preferenceEntry('bytes', [66, 1, 255]),
      16,
      1,
    ]);
    expect(AndroidDataStore.stringPreferences(bytes), {
      'nc_cookie': 'MUSIC_U=old',
      'kg_platform': '1',
    });
  });

  test('validates UTF-8 and rejects duplicate keys and oneof values', () {
    final entry = preferenceEntry('name', stringField(42, 'value'));
    for (final bytes in [
      [...entry, ...entry],
      preferenceEntry('name', [8, 1, ...stringField(42, 'value')]),
      preferenceEntry('name', [42, 1, 255]),
      [10, 2, 10, 0],
      [10, 0],
    ]) {
      expect(
        () => AndroidDataStore.stringPreferences(Uint8List.fromList(bytes)),
        throwsFormatException,
      );
    }
  });

  test('rejects truncated fields, invalid tags and oversized varints', () {
    for (final bytes in [
      [10, 100, 1],
      [0],
      [10, 128],
      [9, 0],
      [13, 0],
      [11],
      List<int>.filled(11, 128),
      [10, ...List<int>.filled(9, 255), 1],
    ]) {
      expect(
        () => AndroidDataStore.stringPreferences(Uint8List.fromList(bytes)),
        throwsFormatException,
      );
    }
  });
}
