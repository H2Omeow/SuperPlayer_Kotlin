import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

const bundledCjkFamily = 'Neko CJK';

void registerBundledFontLicense() {
  LicenseRegistry.addLicense(() async* {
    final license = await rootBundle.loadString('assets/fonts/OFL.txt');
    yield LicenseEntryWithLineBreaks(['Noto Sans CJK'], license);
  });
}
