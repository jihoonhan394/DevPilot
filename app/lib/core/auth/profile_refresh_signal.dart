import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Asks the `GET /me` profile to be read again (docs/02 §2.4).
///
/// Lives in `core` so the API layer can raise it on `USER_NOT_ALLOWED`, `FORBIDDEN` or
/// `ONBOARDING_REQUIRED` without depending on the feature that owns the profile. The value is a
/// counter; only its changes matter.
final class ProfileRefreshSignal extends Notifier<int> {
  @override
  int build() => 0;

  void request() => state++;
}

final profileRefreshSignalProvider = NotifierProvider<ProfileRefreshSignal, int>(
  ProfileRefreshSignal.new,
);
