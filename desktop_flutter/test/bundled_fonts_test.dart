import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:nekoplayer_desktop/ui/bundled_fonts.dart';
import 'package:nekoplayer_desktop/ui/neko_theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'packaged CJK font is registered and matches the pinned asset',
    () async {
      final manifest =
          jsonDecode(await rootBundle.loadString('FontManifest.json'))
              as List<dynamic>;
      final entry = manifest.cast<Map<String, dynamic>>().singleWhere(
        (entry) => entry['family'] == bundledCjkFamily,
      );
      final font =
          (entry['fonts'] as List<dynamic>).single as Map<String, dynamic>;
      final asset = await rootBundle.load(font['asset'] as String);
      expect(
        sha256
            .convert(
              asset.buffer.asUint8List(
                asset.offsetInBytes,
                asset.lengthInBytes,
              ),
            )
            .toString(),
        '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b',
      );
      final loader = FontLoader(bundledCjkFamily)..addFont(Future.value(asset));
      await loader.load();
    },
  );

  test('light and dark text styles use the bundled Chinese fallback', () {
    for (final brightness in Brightness.values) {
      final theme = nekoTheme(brightness: brightness);
      for (final style in [
        theme.textTheme.bodyLarge,
        theme.textTheme.titleLarge,
        theme.textTheme.labelLarge,
        theme.primaryTextTheme.bodyMedium,
      ]) {
        expect(style?.fontFamilyFallback?.first, bundledCjkFamily);
      }
    }
  });

  test('font license is available through the standard license page', () async {
    registerBundledFontLicense();
    final license = await LicenseRegistry.licenses.firstWhere(
      (entry) => entry.packages.contains('Noto Sans CJK'),
    );
    final text = license.paragraphs
        .map((paragraph) => paragraph.text)
        .join('\n');
    expect(text, contains('SIL OPEN FONT LICENSE Version 1.1'));
  });
}
