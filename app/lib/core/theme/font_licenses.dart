import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Registers the SIL OFL 1.1 texts of the bundled fonts so they appear on the license page.
void registerFontLicenses() {
  LicenseRegistry.addLicense(() async* {
    yield LicenseEntryWithLineBreaks(const [
      'Pretendard',
    ], await rootBundle.loadString('assets/fonts/Pretendard-OFL.txt'));
    yield LicenseEntryWithLineBreaks(const [
      'JetBrains Mono',
    ], await rootBundle.loadString('assets/fonts/JetBrainsMono-OFL.txt'));
  });
}
