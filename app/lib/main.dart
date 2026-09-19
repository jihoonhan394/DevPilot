import 'package:devpilot_app/app/app.dart';
import 'package:devpilot_app/app/config_error_app.dart';
import 'package:devpilot_app/core/config/app_config.dart';
import 'package:devpilot_app/core/theme/font_licenses.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  // docs/02 A-6: build the semantics tree from the start so screen readers work without the
  // "enable accessibility" placeholder button. The handle lives as long as the app.
  if (kIsWeb) {
    SemanticsBinding.instance.ensureSemantics();
  }
  registerFontLicenses();
  switch (AppConfig.validate()) {
    case AppConfigValid(:final config):
      runApp(
        ProviderScope(
          overrides: [appConfigProvider.overrideWithValue(config)],
          // Retrying is the API layer's decision (docs/02 §6.6), not Riverpod's automatic retry.
          retry: (retryCount, error) => null,
          child: const DevPilotApp(),
        ),
      );
    case AppConfigInvalid(:final invalidKey):
      runApp(ConfigErrorApp(invalidKey: invalidKey));
  }
}
