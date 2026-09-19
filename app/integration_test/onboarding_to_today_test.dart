import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import '../test/support/onboarding_to_today_flow.dart';

/// BL-CLI-19 (docs/09 §12): onboarding → Today → start, against in-memory fake repositories.
///
/// Run in Chrome (chromedriver on port 4444):
/// `fvm flutter drive --driver=test_driver/integration_test.dart
///  --target=integration_test/onboarding_to_today_test.dart -d web-server --browser-name=chrome`
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('shouldGoFromOnboardingToAStartedTodayTask', runOnboardingToTodayFlow);
}
