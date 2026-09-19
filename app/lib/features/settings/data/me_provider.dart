import 'dart:async';

import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/core/auth/profile_refresh_signal.dart';
import 'package:devpilot_app/core/time/local_date.dart';
import 'package:devpilot_app/core/time/time_zone_support.dart';
import 'package:devpilot_app/features/settings/data/me_repository.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// `GET /me` of the signed-in user: the input of the router redirect rules (docs/02 §2.4).
///
/// Read again when the access token changes (sign-in), when the API layer raises
/// [profileRefreshSignalProvider], or through [refresh] after a write that changes the profile.
final class MeController extends AsyncNotifier<MeResponse> {
  @override
  Future<MeResponse> build() async {
    final accessToken = ref.watch(authStateProvider.select((authState) => authState.accessToken));
    ref.listen(profileRefreshSignalProvider, (_, _) => unawaited(refresh()));
    if (accessToken == null) {
      throw const ApiException(code: ApiErrorCode.authenticationRequired);
    }
    return ref.read(meRepositoryProvider).fetchMe();
  }

  /// Re-reads the profile without a loading state, so the current screen stays.
  ///
  /// `USER_NOT_ALLOWED` replaces the profile (the router then shows SCR-NOT-ALLOWED). Other
  /// failures keep the last profile: a transient error must not lock the whole app.
  Future<void> refresh() async {
    if (ref.read(authStateProvider) is! SignedIn) {
      return;
    }
    try {
      final me = await ref.read(meRepositoryProvider).fetchMe();
      if (ref.mounted) {
        state = AsyncData(me);
      }
    } on ApiException catch (error, stackTrace) {
      if (ref.mounted && (error.code == ApiErrorCode.userNotAllowed || !state.hasValue)) {
        state = AsyncError(error, stackTrace);
      }
    }
  }

  /// Stores a profile returned by another call (`PATCH /me`, `POST /onboarding`).
  void replace(MeResponse me) => state = AsyncData(me);
}

final meProvider = AsyncNotifierProvider<MeController, MeResponse>(MeController.new);

/// The user's IANA zone for showing instants (docs/02 §6.6).
final userTimeZoneProvider = Provider<String>(
  (ref) => ref.watch(meProvider.select((me) => me.value?.timezone)) ?? defaultTimeZone,
);

/// The server plan-day of the user (`GET /me.today`). Screens behind the session gate always
/// have it.
final userTodayProvider = Provider<LocalDate>(
  (ref) => LocalDate.parse(ref.watch(meProvider.select((me) => me.requireValue.today))),
);
