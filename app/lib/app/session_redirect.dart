import 'package:devpilot_app/app/routes.dart';
import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/api/api_exception.dart';
import 'package:devpilot_app/core/auth/auth_controller.dart';
import 'package:devpilot_app/features/settings/data/me_response.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// What the router needs to know about `GET /me` (docs/02 §2.4 rules 3~7).
sealed class ProfileStatus {
  const ProfileStatus();

  /// Derives the status from the profile provider value.
  factory ProfileStatus.of(AsyncValue<MeResponse> me) {
    final error = me.error;
    if (error is ApiException && error.code == ApiErrorCode.userNotAllowed) {
      return const ProfileNotAllowed();
    }
    // AsyncLoading also covers a sign-in with another account: the old profile must not decide.
    if (me is AsyncLoading) {
      return const ProfileLoading();
    }
    final profile = me.value;
    if (profile != null) {
      return ProfileReady(
        onboardingCompleted: profile.onboardingCompleted,
        deletionRequested: profile.status == UserStatus.deletionRequested,
      );
    }
    return ProfileFailed(error ?? const ApiException(code: ApiErrorCode.internalError));
  }
}

final class ProfileLoading extends ProfileStatus {
  const ProfileLoading();
}

final class ProfileNotAllowed extends ProfileStatus {
  const ProfileNotAllowed();
}

final class ProfileFailed extends ProfileStatus {
  const ProfileFailed(this.error);

  final Object error;
}

final class ProfileReady extends ProfileStatus {
  const ProfileReady({required this.onboardingCompleted, required this.deletionRequested});

  final bool onboardingCompleted;
  final bool deletionRequested;
}

/// Redirect rules 1~7 of docs/02 §2.4, evaluated in order. Returns null to stay on [location].
///
/// Rule 3 (profile loading) and a failed profile keep the location; the session gate shows the
/// full-screen loading or error view meanwhile. Rule 5 (deletion requested) is a sign-out done by
/// the router's profile listener, after which rule 1 applies. Rule 8 (feature flags) is not used:
/// only the routes of shipped stages are registered.
String? redirectFor({
  required AuthState auth,
  required ProfileStatus profile,
  required Uri location,
}) {
  final path = location.path;
  final access = RouteAccess.of(path);
  switch (auth) {
    case SignedOut(:final reason):
      if (!access.requiresSession) {
        return null;
      }
      final queryParameters = {
        if (path != AppRoutes.root) 'from': location.toString(),
        'reason': ?reason?.name,
      };
      return Uri(
        path: AppRoutes.login,
        queryParameters: queryParameters.isEmpty ? null : queryParameters,
      ).toString();
    case SignedIn():
      if (path == AppRoutes.login) {
        return _returnPath(location.queryParameters['from']);
      }
  }
  switch (profile) {
    case ProfileLoading() || ProfileFailed():
      return null;
    case ProfileNotAllowed():
      return path == AppRoutes.notAllowed ? null : AppRoutes.notAllowed;
    case ProfileReady(deletionRequested: true):
      return null;
    case ProfileReady(:final onboardingCompleted):
      if (path == AppRoutes.notAllowed) {
        return AppRoutes.start;
      }
      if (!onboardingCompleted && (access == RouteAccess.onboarded)) {
        return AppRoutes.onboardingGoal;
      }
      if (onboardingCompleted && access == RouteAccess.onboardingInput) {
        return AppRoutes.start;
      }
      if (path == AppRoutes.root) {
        return AppRoutes.start;
      }
      return null;
  }
}

/// Accepts only in-app paths so `from` cannot point to another origin.
String _returnPath(String? from) {
  if (from == null || !from.startsWith('/') || from.startsWith('//')) {
    return AppRoutes.start;
  }
  final path = Uri.parse(from).path;
  return path == AppRoutes.login || path == AppRoutes.root ? AppRoutes.start : from;
}
