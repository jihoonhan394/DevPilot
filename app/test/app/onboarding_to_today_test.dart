import 'package:flutter_test/flutter_test.dart';

import '../support/onboarding_to_today_flow.dart';

/// The integration flow of BL-CLI-19 in the headless widget runner, so every `flutter test` run
/// covers it; `integration_test/onboarding_to_today_test.dart` runs the same flow in Chrome.
void main() {
  testWidgets('shouldGoFromOnboardingToAStartedTodayTask', runOnboardingToTodayFlow);
}
